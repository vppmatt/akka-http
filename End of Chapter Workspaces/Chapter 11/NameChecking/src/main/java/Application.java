import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

public class Application {

    private RejectionHandler rejectionHandler = RejectionHandler
            .newBuilder().handleAll(InvalidRequiredValueForQueryParamRejection.class,
                    rejList -> {
                        StringBuilder sb = new StringBuilder();
                        for (InvalidRequiredValueForQueryParamRejection rej : rejList) {
                            sb.append(rej.actualValue() + " didn't match with " +
                                    rej.expectedValue() + "    ");
                        }

                        return complete(HttpResponse.create()
                        .withEntity(sb.toString()).withStatus(StatusCodes.NOT_ACCEPTABLE));
                    }
                    ).build();

    private ExceptionHandler exceptionHandler = new ExceptionHandlerBuilder()
            .match(IllegalArgumentException.class, err -> {
                return complete(HttpResponse.create().withEntity("Not the right number of characters")
                .withStatus(400));
            })
            .build();

    private Route getRoutes() {
        return get( () ->
                handleExceptions(exceptionHandler, () ->
                    pathPrefix("hello" , () ->
                        handleRejections(rejectionHandler, () ->
                            concat (
                                parameter("name", name -> {
                                    if (name.length() != 5) {
                                        throw new IllegalArgumentException();
                                    }
                                    if (name.equals("donna")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject(Rejections.
                                                invalidRequiredValueForQueryParam("name",
                                                        "donna", name));
                                    }

                                }),
                                parameter("name", name -> {
                                    if (name.equals("susan")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject(Rejections.
                                                invalidRequiredValueForQueryParam("name",
                                                        "susan", name));
                                    }

                                }),
                                parameter("name", name -> {
                                    if (name.equals("sally")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject(Rejections.
                                                invalidRequiredValueForQueryParam("name",
                                                        "sally", name));
                                    }

                                })
                            )
                        )
                    )
                )
        );

    }

    public void run() {
        ActorSystem actorSystem = ActorSystem.create(Behaviors.empty(), "actorSystem");
        Http.get(actorSystem).newServerAt("localhost", 8080).bind(getRoutes());
    }
}
