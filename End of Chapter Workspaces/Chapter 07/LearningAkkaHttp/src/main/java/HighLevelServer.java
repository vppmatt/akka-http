import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.*;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.PathMatcher0;
import akka.http.javadsl.server.PathMatcher1;
import akka.http.javadsl.server.Rejections;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.SecurityDirectives;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

public class HighLevelServer {

    ActorSystem<CustomerBehavior.Command> actorSystem = ActorSystem.create(CustomerBehavior.create(), "simpleServer");

    Function<HttpRequest, CompletionStage<HttpResponse>> updateCustomer = (request) -> {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        Unmarshaller<HttpEntity, Customer> unmarshaller = Jackson.unmarshaller(Customer.class);
        CompletionStage<Customer> customerFuture = unmarshaller.unmarshal(request.entity(), actorSystem);

        customerFuture.whenComplete( (customer, throwable) -> {
            if(throwable != null) {
                response.complete(HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST).withEntity(throwable.toString()));
                request.discardEntityBytes(actorSystem);
            } else {
                actorSystem.tell(new CustomerBehavior.UpdateCustomerCommand(customer));
                request.discardEntityBytes(actorSystem);
                response.complete(HttpResponse.create().withStatus(200));
            }
        } );
        return response;
    };

    Function<HttpRequest, CompletionStage<HttpResponse>> getAllCustomers = (request) -> {
        request.getHeaders().forEach(header -> {
            System.out.println(header.name() + ":" + header.value());
        });

        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        CompletionStage<List<Customer>> customersFuture = AskPattern.ask(actorSystem, me -> new CustomerBehavior.GetCustomersCommand(me),
                Duration.ofSeconds(5), actorSystem.scheduler());
        customersFuture.whenComplete( (customers, throwable) -> {
            //check throwable is not null
            try {
                String json = new ObjectMapper().writeValueAsString(customers);
                response.complete(HttpResponse.create().withStatus(200).withEntity(json));
                request.discardEntityBytes(actorSystem);
            } catch (JsonProcessingException e) {
                response.complete(HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR));
                request.discardEntityBytes(actorSystem);
            }
        });
        return response;
    };

    CompletionStage<HttpResponse> getSingleCustomer(int id, HttpRequest request) {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        CompletionStage<Customer> customerFuture = AskPattern.ask(actorSystem,
                me -> new CustomerBehavior.GetCustomerCommand(id, me),
                Duration.ofSeconds(5),
                actorSystem.scheduler());
        customerFuture.whenComplete( (customer, throwable) -> {
           if (throwable == null) {
               try {
                   if(customer.getId() == 0) {
                       response.complete(HttpResponse.create().withStatus(StatusCodes.NO_CONTENT));
                   }
                   else {
                       String json = new ObjectMapper().writeValueAsString(customer);
                       response.complete(HttpResponse.create().withStatus(StatusCodes.OK)
                               .withEntity(json));
                   }
               } catch (JsonProcessingException e) {
                   //should never happen
                   response.complete(HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR));
               }
           } else
           //no matching customer
           {
               response.complete(HttpResponse.create().withStatus(StatusCodes.NO_CONTENT));
           }
        });
        return response;
    }

    Route authenticatedPost(Supplier<Route> inner) {
        return authenticateBasic("secure", basicAuthenticator, user ->
                authorize(
                        () -> user.isAdmin(), () ->
                                post( () -> inner.get())
                        )
        );
    }

    private Route createRoute() {
        PathMatcher0 entryPoint = segment("api").slash("v3").orElse(separateOnSlashes("api/v2"));
        PathMatcher1<Integer> customerUrlWithId = segment("customer").slash(integerSegment());  // "api/v3/customer/id"

        Route customerRoute = path("customer", () ->
                concat(
                        get(() -> parameter("id", id ->
                                extractRequest( request -> {
                                    try {
                                        return completeWithFuture(getSingleCustomer(Integer.parseInt(id), request));
                                    }
                                    catch (Exception e) {
                                        return reject(Rejections.invalidRequiredValueForQueryParam("id","integer",
                                                "not an integer"));
                                    }
                                } ))),
                        get(() -> authenticateBasicEncrypted( user -> handle(getAllCustomers))),
                        authenticatedPost( () -> handle(updateCustomer))

                ));

        return concat(
                pathPrefix( entryPoint, () ->
                concat(
                        path(customerUrlWithId, id ->
                                extractRequest( request -> completeWithFuture(getSingleCustomer(id, request)))),
                        customerRoute,
                        path("alive", () -> get( () -> complete("ok")  ))
                    )
                ),
                pathEndOrSingleSlash( () -> reject())
        );

//        Route getAllCustomersRoute = get(() -> handle(getAllCustomers));
//        Route updateCustomerRoute = post(() -> handle(updateCustomer));
//        Route combinedRoute = concat(getAllCustomersRoute, updateCustomerRoute);
//        Route customerRoute = path("customer", () -> combinedRoute);
//        Route allRoutes = pathPrefix("api", () -> customerRoute);
//        return allRoutes;
    }

    private String getCertificatePassword() {
        try (
                InputStream inputStream = ClassLoader.getSystemResourceAsStream("security.properties");
        ) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty("certificatepassword");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HttpsConnectionContext createHttpsContext() {
        String password = getCertificatePassword();
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream("identity.p12");
                ){
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(inputStream, password.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ConnectionContext.httpsServer(sslContext);
        } catch (KeyStoreException | IOException | CertificateException |
                NoSuchAlgorithmException | UnrecoverableKeyException | KeyManagementException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private java.util.function.Function<Optional<SecurityDirectives.ProvidedCredentials>,
            Optional<User>> basicAuthenticator = providedCredentials -> {

        List<User> users = List.of(new User("user","pass",false),
                new User("admin","pass",true));
        if (providedCredentials.isPresent()) {
            String username = providedCredentials.get().identifier();
            Optional<User> foundUser = users.stream().filter( user -> user.getName().equals(username)).findFirst();
            if (foundUser.isPresent()) {
                if(providedCredentials.get().verify(foundUser.get().getPassword())) {
                    return foundUser;
                };
            }
        }
        return Optional.empty();
    };

    private Optional<User> authenticateUserEncrypted(String token) {
        List<User> users = List.of(new User("user","pass",false),
                new User("admin","pass",true));
        System.out.println("About to decode " + token);
        String usernameAndPassword = new String(Base64.getDecoder().decode(token));
        System.out.println("Username and password was : " + usernameAndPassword);
        String username = usernameAndPassword.split(":")[0];
        String password = usernameAndPassword.split(":")[1];

        //String encryptedPasword = apply some encryption to the password the user has sent through

        return users.stream().
                filter( user -> user.getName().equals(username) && user.getPassword().equals(password)).findFirst();
    }

    private Route authenticateBasicEncrypted(java.util.function.Function<User,Route> inner) {
        return headerValueByName("Authorization", basicAuthHeader -> {
            System.out.println("Auth header is " + basicAuthHeader);
            if (basicAuthHeader.startsWith("Basic")) {
                String token = basicAuthHeader.substring(6);
                Optional<User> foundUser = authenticateUserEncrypted(token);
                if (foundUser.isPresent()) {
                    return inner.apply(foundUser.get());
                }
                else {
                    return complete(StatusCodes.FORBIDDEN);
                }
            }
            else {
                return complete(StatusCodes.FORBIDDEN);
            }
        });
    }

    public void run() {
        CompletionStage<ServerBinding> server = Http.get(actorSystem)
                .newServerAt("localhost", 443).enableHttps(createHttpsContext()).bind(createRoute());

        server.whenComplete( (binding, throwable) -> {
            if (throwable != null) {
                System.out.println("Something went wrong " + throwable);
            } else {
                System.out.println("The server is running at " + binding.localAddress());
            }
        });
    }
}
