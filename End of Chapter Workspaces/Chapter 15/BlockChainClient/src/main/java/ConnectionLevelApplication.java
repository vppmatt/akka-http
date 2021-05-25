import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.OutgoingConnection;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import model.BlockChain;


import java.util.concurrent.CompletionStage;

public class ConnectionLevelApplication {

    public void run() {
        ActorSystem actorSystem = ActorSystem.create(Behaviors.empty(), "actorSystem");

        //non HTTPS connection
        //Flow<HttpRequest, HttpResponse, CompletionStage<OutgoingConnection>> connectionFlow =
        //        Http.get(actorSystem).outgoingConnection("localhost");

        //HTTPS with a trusted certificate
        //Flow<HttpRequest, HttpResponse, CompletionStage<OutgoingConnection>> connectionFlow =
        //        Http.get(actorSystem).outgoingConnection(ConnectHttp.toHostHttps("localhost"));

        //HTTPS with a non trusted certificate - ONLY USE FOR LOCALHOST!
        Flow<HttpRequest, HttpResponse, CompletionStage<OutgoingConnection>> connectionFlow =
                Http.get(actorSystem).outgoingConnection(ConnectHttp
                        .toHostHttps("localhost")
                        .withCustomHttpsContext(HttpsConnectionContext.
                            httpsClient(UntrustedServerCertificateUtility.getSSLContext())));

        Source<HttpRequest, NotUsed> source = Source.single(
                HttpRequest.create().withMethod(HttpMethods.GET)
                .withUri("/api/blockchain"));

        Unmarshaller<HttpEntity, BlockChain> blockChainUnmarshaller = Jackson.unmarshaller(BlockChain.class);
        Sink<HttpResponse, CompletionStage<Done>> sink = Sink.foreach(response -> {
            CompletionStage<BlockChain> blockchainFuture = blockChainUnmarshaller.unmarshal(response.entity(), actorSystem);
            blockchainFuture.whenComplete( (blockchain, throwable) -> {
                if (throwable != null) {
                    System.out.println("THROWABLE " + throwable);
                }
                else {
                    blockchain.printAndValidate();
                }
            });
        });

        source.via(connectionFlow).to(sink).run(actorSystem);
    }
}