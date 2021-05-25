import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

public class Application {

    public Route getRoutes() {
        return get( () ->
                    concat(
                        path("add", () ->
                            parameterMap( map -> {
                                if (map.containsKey("first") && map.containsKey("second")) {
                                    int total = Integer.parseInt(map.get("first")) + Integer.parseInt(map.get("second"));
                                    return complete("{\"total\":" + total + "}");
                                }
                                else {
                                    return reject();
                                }
                            } )
                        ),
                    pathPrefix("hello" , () ->
                            concat (
                                parameter("name", name -> {
                                    if (name.equals("donna")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject(Rejections.invalidRequiredValueForQueryParam(
                                                "name", "donna", name));
                                    }

                                }),
                                parameter("name", name -> {
                                    if (name.equals("susan")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject(Rejections.invalidRequiredValueForQueryParam(
                                                "name", "susan", name));
                                    }

                                }),
                                parameter("name", name -> {
                                    if (name.equals("sally")) {
                                        return complete(StatusCodes.OK);
                                    }
                                    else {
                                        return reject(Rejections.invalidRequiredValueForQueryParam(
                                                "name", "sally", name));
                                    }

                                })
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
