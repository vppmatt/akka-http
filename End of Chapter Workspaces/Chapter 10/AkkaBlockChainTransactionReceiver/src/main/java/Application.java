import actors.TransactionManagerBehavior;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.function.Function;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Block;
import model.Transaction;
import model.TransactionStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class Application {

    ActorSystem<TransactionManagerBehavior.Command> actorSystem;

    private Route addTransaction(Transaction transaction) {
        System.out.println("Transaction received : " + transaction);

        Source<Transaction, NotUsed> source = Source.single(transaction);

        Flow<Transaction,Integer, NotUsed> newTransactionFlow =
            ActorFlow.ask(actorSystem,
                    Duration.ofSeconds(5),
                    (trans, me) -> new TransactionManagerBehavior.NewTransactionCommand(transaction, me));

        Flow<Integer, ByteString, NotUsed> convertToBytestringFlow =
                Flow.of(Integer.class).map( id -> {
                            Map<String, Integer> map = Map.of("transactionId", id);
                            return ByteString.fromString(
                                    new ObjectMapper().writeValueAsString(map));
                        });

        Source<ByteString,NotUsed> graph = source.via(newTransactionFlow)
                .via(convertToBytestringFlow);
        return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON, graph));

    }


    private Route getStatus(HttpRequest request) {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        int id = 0;
        try {
            id = Integer.parseInt(request.getUri().query().get("id").get());
        }
        catch (Exception e) {
            response.complete(HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST)
                    .withEntity("id parameter was not supplied or was in the incorrect format"));
        }

        System.out.println("Getting status for transaction with id " + id);
        final int finalId = id;

        Source<Integer, NotUsed> source = Source.single(finalId);

        Flow<Integer, TransactionStatus, NotUsed> getStatusFlow =
                ActorFlow.ask(actorSystem,
                        Duration.ofSeconds(5),
                        (id2, me) -> new TransactionManagerBehavior.GetTransactionStatusCommand(id2, me)
                        );
        Flow<TransactionStatus, ByteString, NotUsed> convertResponseToByteString =
                Flow.of(TransactionStatus.class).map (status -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("TransactionId", finalId);
                    result.put("TransactionStatus", status);
                    String json = new ObjectMapper().writeValueAsString(result);
                    return ByteString.fromString(json);
                });
        Source<ByteString, NotUsed> graph = source.via(getStatusFlow)
                .via(convertResponseToByteString);
        return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON, graph));
    }

    private Route mineBlock (HttpRequest request) {
        final String lastHash = request.getUri().query().get("lastHash").get();
        System.out.println("Mining block with last hash " + lastHash);

        Flow<String, Block, NotUsed> flow = ActorFlow.ask(actorSystem,
                Duration.ofSeconds(5),
                (hash, me) -> new TransactionManagerBehavior.GenerateBlockCommand(me, hash)
                );

        Source<ByteString, NotUsed> graph = Source.single(lastHash)
                .via(flow).via(Flow.of(Block.class).map( block -> {
                    String result = new ObjectMapper().writeValueAsString(block);
                    return ByteString.fromString(result);
                }));
        return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON, graph));
    }

    private Route createRoute() {

        Unmarshaller<HttpEntity, Transaction> transactionUnmarshaller = Jackson.unmarshaller(Transaction.class);

        Route addTransactionRoute = post( () ->
                entity(transactionUnmarshaller, transaction -> addTransaction(transaction))
                );
        Route getTransactionStatusRoute = get( () ->
                extractRequest( request -> getStatus(request))
                );
        Route transactionRoutes = path("transaction",
                () -> concat(addTransactionRoute, getTransactionStatusRoute));
        Route miningRoute = path("mining", () -> put( () ->
                extractRequest( request -> mineBlock(request)))
                );

        Route allRoutes = pathPrefix("api" , () -> concat(transactionRoutes, miningRoute));
        return allRoutes;
    }

    public void run() {
        actorSystem = ActorSystem.create(TransactionManagerBehavior.create(), "actorSystem");
        Http.get(actorSystem).newServerAt("localhost", 8080).bind(createRoute());
    }
}
