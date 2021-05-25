import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.*;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.BlockChain;
import model.Transaction;
import model.TransactionResponse;
import scala.util.Try;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public class HostLevelApplication {

    public void run() {
        ActorSystem actorSystem = ActorSystem.create(Behaviors.empty(), "actorSystem");

        //HTTPS with a non trusted certificate - ONLY USE FOR LOCALHOST!
        Flow<Pair<HttpRequest,  Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> connectionFlow =
                Http.get(actorSystem).cachedHostConnectionPoolHttps(ConnectHttp
                        .toHostHttps("localhost")
                        .withCustomHttpsContext(HttpsConnectionContext.
                                httpsClient(UntrustedServerCertificateUtility.getSSLContext())),
                        ConnectionPoolSettings.create(actorSystem.classicSystem()),
                        actorSystem.classicSystem().log());

        Map<Integer, Transaction> transactions = Map.of(
                567, new Transaction (0, System.currentTimeMillis(), 123, 79.65),
                568, new Transaction (0, System.currentTimeMillis(), 167, 109.65),
                569, new Transaction (0, System.currentTimeMillis(), 203, 95.55),
                570, new Transaction (0, System.currentTimeMillis(), 143, 29.61),
                571, new Transaction (0, System.currentTimeMillis(), 125, 67.11),
                572, new Transaction (0, System.currentTimeMillis(), 129, 71.00),
                573, new Transaction (0, System.currentTimeMillis(), 640, 33.51),
                574, new Transaction (0, System.currentTimeMillis(), 264, 71.58),
                575, new Transaction (0, System.currentTimeMillis(), 338, 58.74),
                576, new Transaction (0, System.currentTimeMillis(), 111, 90.01)
        );

        Source<Integer, NotUsed> source = Source.from(transactions.keySet());

        Flow<Integer, Pair<HttpRequest, Integer>, NotUsed> getTransactionFlow = Flow.of(Integer.class).map (i ->
                {
                    Transaction t = transactions.get(i);
                    HttpRequest request = HttpRequest.create().withMethod(HttpMethods.POST)
                            .withUri("/api/transaction")
                            .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, new ObjectMapper().writeValueAsString(t)));
                    return new Pair(request, i);
                }
                );

        Unmarshaller<HttpEntity, TransactionResponse> responseUnmarshaller = Jackson.unmarshaller(TransactionResponse.class);

        Sink<Pair<Try<HttpResponse>, Integer>, CompletionStage<Done>> sink = Sink.foreach(response -> {
            System.out.println("got a response");
            if (response.first().isFailure()) {
                System.out.println("Something went wrong " + response.first());
            }
            else {
                Integer key = response.second();

                CompletionStage<TransactionResponse> responseFuture = responseUnmarshaller.unmarshal(response.first().get().entity(), actorSystem);
                responseFuture.whenComplete( (transactionResponse, throwable) -> {
                    if (throwable != null) {
                        System.out.println("THROWABLE " + throwable);
                    } else {
                        transactions.get(key).setId(transactionResponse.getTransactionId());
                        System.out.println("for key " + key + " the ID is " + transactionResponse.getTransactionId());
                    }
                });
            }
        });

        source.via(getTransactionFlow).via(connectionFlow).to(sink).run(actorSystem);
    }
}
