import actors.TransactionManagerBehavior;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.function.Function;
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

    Function<HttpRequest, CompletionStage<HttpResponse>> addTransaction = (request) -> {
        System.out.println("Adding a transaction");
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        Unmarshaller<HttpEntity, Transaction> transactionUnmarshaller = Jackson.unmarshaller(Transaction.class);

        CompletionStage<Transaction> transactionFuture = transactionUnmarshaller.unmarshal(request.entity(), actorSystem);

        transactionFuture.whenComplete( (transaction, throwable) -> {
            System.out.println("transaction has been unmarshalled");
            if (throwable != null  || transaction == null) {
                System.out.println("Something went wrong " + throwable);
                response.complete(HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST));
                request.discardEntityBytes(actorSystem);
            }
            else {
                System.out.println("Transaction received : " + transaction);
                CompletionStage<Integer> transactionIdFuture = AskPattern.ask(actorSystem,
                       me -> new TransactionManagerBehavior.NewTransactionCommand(transaction, me),
                        Duration.ofSeconds(5),
                        actorSystem.scheduler());

                transactionIdFuture.whenComplete( (transactionId, throwable2) -> {
                    Map<String, Integer> result = new HashMap<>();
                    result.put("transactionId", transactionId);
                    try {
                        String json = new ObjectMapper().writeValueAsString(result);
                        request.discardEntityBytes(actorSystem);
                        response.complete(HttpResponse.create().withStatus(200).withEntity(json));
                    } catch (JsonProcessingException e) {
                        System.out.println(e);
                        request.discardEntityBytes(actorSystem);
                        response.complete(HttpResponse.create().withStatus(StatusCodes.NOT_ACCEPTABLE));
                    }
                });
            }
        });

        return response;
    };

    Function<HttpRequest, CompletionStage<HttpResponse>> getStatus = (request) -> {
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
        CompletionStage<TransactionStatus> transactionStatusFuture = AskPattern.ask(actorSystem,
                me -> new TransactionManagerBehavior.GetTransactionStatusCommand(finalId, me),
                Duration.ofSeconds(5), actorSystem.scheduler());

        transactionStatusFuture.whenComplete( (transactionStatus, throwable) -> {
            if (throwable != null) {
                System.out.println("Something went wrong " + throwable);
                request.discardEntityBytes(actorSystem);
                response.complete(HttpResponse.create().withStatus(StatusCodes.NOT_ACCEPTABLE));
            }
            else {
                Map<String, Object> result = new HashMap<>();
                result.put("TransactionId", finalId);
                result.put("TransactionStatus", transactionStatus);
                try {
                    String json = new ObjectMapper().writeValueAsString(result);
                    request.discardEntityBytes(actorSystem);
                    response.complete(HttpResponse.create().withStatus(200).withEntity(json));
                } catch (JsonProcessingException e) {
                    request.discardEntityBytes(actorSystem);
                    response.complete(HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST));
                }
            }
        });
        return response;
    };

    Function<HttpRequest, CompletionStage<HttpResponse>> mineBlock = (request) -> {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();

        try {
            final String lastHash = request.getUri().query().get("lastHash").get();
            System.out.println("Mining block with last hash " + lastHash);
            CompletionStage<Block> blockFuture = AskPattern.ask(actorSystem,
                    me -> new TransactionManagerBehavior.GenerateBlockCommand(me, lastHash),
                    Duration.ofSeconds(5), actorSystem.scheduler());
            request.discardEntityBytes(actorSystem);
            response.complete(HttpResponse.create().withStatus(200));
        }
        catch (Exception e) {
            request.discardEntityBytes(actorSystem);
            response.complete(HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST)
                    .withEntity("lastHash parameter was not supplied or was in the incorrect format"));
        }
        return response;
    };


    private Route createRoute() {
        Route addTransactionRoute = post( () -> handle(addTransaction) );
        Route getTransactionStatusRoute = get( () -> handle(getStatus));
        Route transactionRoutes = path("transaction",
                () -> concat(addTransactionRoute, getTransactionStatusRoute));
        Route miningRoute = path("mining", () -> put( () -> handle(mineBlock)));

        Route allRoutes = pathPrefix("api" , () -> concat(transactionRoutes, miningRoute));
        return allRoutes;
    }

    public void run() {
        actorSystem = ActorSystem.create(TransactionManagerBehavior.create(), "actorSystem");
        Http.get(actorSystem).newServerAt("localhost", 8080).bind(createRoute());
    }
}
