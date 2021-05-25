import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Flow;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

public class Application {

    ActorSystem<PrimeDatabaseActor.Command> actorSystem;

    private CompletionStage<Integer> newPrimeNumberRequest() {
        CompletionStage<Integer> requestId = AskPattern.ask(actorSystem,
                me -> new PrimeDatabaseActor.NewRequestCommand(me),
                Duration.ofSeconds(5),
                actorSystem.scheduler());
        return requestId;
    }

    private CompletableFuture<HttpResponse> newUpdateRequest(String requestId) {
        CompletionStage<BigInteger> resultValue = AskPattern.ask(actorSystem,
                me -> new PrimeDatabaseActor
                        .GetResultCommand(Integer.parseInt(requestId), me),
                Duration.ofSeconds(5),
                actorSystem.scheduler());
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        resultValue.whenComplete( (value, throwable) -> {
            response.complete(HttpResponse.create()
                    .withStatus(200).withEntity(value.toString()));
        });
        return response;
    }

    public Route createRoute() {

        return get( () ->
                concat(
                        pathEndOrSingleSlash( () -> {
                                    System.out.println("Received new request");
                                    return onComplete(newPrimeNumberRequest(),
                                            requestId -> complete(requestId.get().toString()) );
                                }
                        ),
                        path(segment("result").slash(remaining()), requestId -> {
                            System.out.println("Received progress update request");
                            return completeWithFuture(newUpdateRequest(requestId));
                        })
                )
        );

    }

    public void run() {
        actorSystem = ActorSystem.
                create(PrimeDatabaseActor.create(), "actorSystem");
        Http.get(actorSystem).newServerAt("localhost",8080).
                bind(createRoute());
    }
}
