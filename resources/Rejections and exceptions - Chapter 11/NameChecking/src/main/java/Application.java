import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

public class Application {

    private Route getRoutes() {
        return get( () ->
                pathPrefix("hello" , () ->
                        concat (
                                parameter("name", name -> {
                                    if (name.equals("donna")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject();
                                    }

                                }),
                                parameter("name", name -> {
                                    if (name.equals("susan")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject();
                                    }

                                }),
                                parameter("name", name -> {
                                    if (name.equals("sally")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject();
                                    }

                                })
                        )
                )
        );

    }

    public void run() {
        ActorSystem actorSystem = ActorSystem.create(Behaviors.empty(), "actorSystem");
        Http.get(actorSystem).newServerAt("localhost", 8080).bind(getRoutes());
    }
}
