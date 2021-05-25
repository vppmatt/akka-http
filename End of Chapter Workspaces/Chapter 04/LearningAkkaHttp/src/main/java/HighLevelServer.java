import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class HighLevelServer {

    ActorSystem<CustomerBehavior.Command> actorSystem = ActorSystem.create(CustomerBehavior.create(), "simpleServer");

    Function<HttpRequest, CompletionStage<HttpResponse>> updateCustomer = (request) -> {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        Unmarshaller<HttpEntity, Customer> unmarshaller = Jackson.unmarshaller(Customer.class);
        CompletionStage<Customer> customerFuture = unmarshaller.unmarshal(request.entity(), actorSystem);
        //should check customer is not null
        customerFuture.whenComplete( (customer, throwable) -> {
            //check throwable is not null
            actorSystem.tell(new CustomerBehavior.UpdateCustomerCommand(customer));
            request.discardEntityBytes(actorSystem);
            response.complete(HttpResponse.create().withStatus(200));
        } );
        return response;
    };

    Function<HttpRequest, CompletionStage<HttpResponse>> getAllCustomers = (request) -> {
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

    Function<HttpRequest, CompletionStage<HttpResponse>> getSingleCustomer = (request) -> {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        int id = Integer.parseInt(request.getUri().query().get("id").get());
        CompletionStage<Customer> customerFuture = AskPattern.ask(actorSystem,
                me -> new CustomerBehavior.GetCustomerCommand(id, me),
                Duration.ofSeconds(5),
                actorSystem.scheduler());
        customerFuture.whenComplete( (customer, throwable) -> {
           if (throwable == null) {
               try {
                   if(customer.getId() == 0) {
                       response.complete(HttpResponse.create().withStatus(StatusCodes.NO_CONTENT));
                       request.discardEntityBytes(actorSystem);
                   }
                   else {
                       String json = new ObjectMapper().writeValueAsString(customer);
                       response.complete(HttpResponse.create().withStatus(StatusCodes.OK)
                               .withEntity(json));
                       request.discardEntityBytes(actorSystem);
                   }
               } catch (JsonProcessingException e) {
                   //should never happen
                   response.complete(HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR));
                   request.discardEntityBytes(actorSystem);
               }
           } else
           //no matching customer
           {
               response.complete(HttpResponse.create().withStatus(StatusCodes.NO_CONTENT));
               request.discardEntityBytes(actorSystem);
           }
        });
        return response;
    };

    private Route createRoute() {

        return pathPrefix("api", () ->
                concat(
                    path("customer", () ->
                        concat(
                                get(() -> parameter("id", id -> handle(getSingleCustomer)) ),
                                get(() -> handle(getAllCustomers)),
                                post(() -> handle(updateCustomer))
                        )),
                    path("alive", () -> get( () -> complete("ok")  ))
            )
        );

//        Route getAllCustomersRoute = get(() -> handle(getAllCustomers));
//        Route updateCustomerRoute = post(() -> handle(updateCustomer));
//        Route combinedRoute = concat(getAllCustomersRoute, updateCustomerRoute);
//        Route customerRoute = path("customer", () -> combinedRoute);
//        Route allRoutes = pathPrefix("api", () -> customerRoute);
//        return allRoutes;
    }

    public void run() {
        CompletionStage<ServerBinding> server = Http.get(actorSystem)
                .newServerAt("localhost", 8080).bind(createRoute());

        server.whenComplete( (binding, throwable) -> {
            if (throwable != null) {
                System.out.println("Something went wrong " + throwable);
            } else {
                System.out.println("The server is running at " + binding.localAddress());
            }
        });
    }
}
