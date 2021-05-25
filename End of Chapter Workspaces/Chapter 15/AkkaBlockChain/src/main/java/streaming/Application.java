package streaming;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.*;
import akka.stream.javadsl.*;
import akka.stream.typed.javadsl.ActorFlow;
import akka.util.ByteString;
import blockchain.MiningManagerBehavior;
import blockchain.BlockChainSystemBehavior;
import blockchain.TransactionManagerBehavior;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

public class Application {

    private int transId = -1;
    private Random random = new Random();
    private ActorSystem<MiningManagerBehavior.Command> actorSystem;
    private ActorRef<TransactionManagerBehavior.Command> transactionManager;

    private Route getTransactionStatusRoute() {
        return get( () ->
                extractRequest( request ->
                        parameter("id", id -> {
                            System.out.println("Getting status for transaction " + id);
                            Integer idInt = Integer.parseInt(id);
                            Source<Integer, NotUsed> source = Source.single(idInt);
                            Flow<Integer, TransactionStatus, NotUsed> transactionStatusFlow =
                                ActorFlow.ask(transactionManager,
                                        Duration.ofSeconds(5),
                                        (i, me) ->
                                                new TransactionManagerBehavior.GetTransactionStatusCommand(i, me));
                            Flow<TransactionStatus, ByteString, NotUsed> convertResponseFlow =
                                    Flow.of(TransactionStatus.class).map (status -> {
                                        Map<String, Object> result =
                                                Map.of("transactionId", id,
                                                        "status", status);
                                        String json = new ObjectMapper().writeValueAsString(result);
                                        return ByteString.fromString(json);
                                    });
                            Source<ByteString, NotUsed> graph = source
                                    .via(transactionStatusFlow).via(convertResponseFlow);
                            request.discardEntityBytes(actorSystem);
                            return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON, graph));
                        })
                )
        );
    }

    private Route handleNewTransactionRoute() {
        Unmarshaller<HttpEntity, Transaction> transactionUnmarshaller =
                Jackson.unmarshaller(Transaction.class);

        List<HttpHeader> headers = List.of(
                RawHeader.create("Access-Control-Allow-Origin", "*"),
                RawHeader.create("Access-Control-Allow-Headers",
                        "Content-Type, Access-Control-Allow-Headers"),
                RawHeader.create("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        );

        System.out.println("received a transaction");

        return concat(
                post( () ->
                        respondWithHeaders(headers, () ->
                            entity( transactionUnmarshaller, transaction -> {
                                System.out.println("receiving a transaction");
                                Source<Transaction, NotUsed> source = Source.single(transaction);
                                Flow<Transaction, Integer, NotUsed> receiveTransactionsFlow =
                                        ActorFlow.ask(transactionManager,
                                                Duration.ofSeconds(5),
                                                (trans, me) ->
                                                        new TransactionManagerBehavior.NewTransactionCommand(trans, me))
                                        ;
                                Flow<Integer, ByteString, NotUsed> convertResponseFlow =
                                        Flow.of(Integer.class).map (id -> {
                                            System.out.println("new transaction " + id);
                                            Map<String, Integer> result = Map.of("transactionId", id);
                                            String json = new ObjectMapper().writeValueAsString(result);
                                            return ByteString.fromString(json);
                                        });
                                Source<ByteString, NotUsed> graph = source.via(receiveTransactionsFlow)
                                        .via(convertResponseFlow);
                                return extractRequest( r -> {
                                            r.discardEntityBytes(actorSystem);
                                            return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON, graph));
                                        }
                                );

                            })
                        )
                ),
                options( () -> complete(HttpResponse.create().withStatus(200)
                        .withHeaders(headers)))
            );
    }

    private Route monitorTransactionStatusRoute() {

        Flow<Message, Message, NotUsed> websocketHandler = Flow.of(Message.class).map (incomingMessage ->
        {
            if (incomingMessage.isText()) {
                String rawMessage = incomingMessage.asTextMessage().getStrictText();
                String[] messageParts = rawMessage.split(" ");
                if (messageParts[0].equals("monitor")) {
                    int id = Integer.parseInt(messageParts[1]);

                    Flow<Integer, TransactionStatus, NotUsed> transactionStatusFlow =
                            ActorFlow.ask(transactionManager,
                                    Duration.ofSeconds(5),
                                    (i, me) ->
                                            new TransactionManagerBehavior.GetTransactionStatusCommand(i, me));

                    Source<String, NotUsed> source = Source.single(id)
                            .via(transactionStatusFlow)
                            .map(status -> status.toString());

                    return TextMessage.create(source);
                } else {
                    return TextMessage.create("Error - unknown message request. " + messageParts[0] +
                            ". Try 'monitor'.");
                }
            } else {
                return TextMessage.create("Error - we only accept text format messages");
            }
        });

        return get( () ->
                handleWebSocketMessages(websocketHandler)
                );
    }

    private Route getBlockChainRoute() {
        return get( ()->
                extractRequest( request ->
                {
                    CompletionStage<BlockChain> blockChainFuture = AskPattern.ask(
                    actorSystem,
                    me -> new BlockChainSystemBehavior.GetCopyOfBlockChain(me),
                    Duration.ofSeconds(5),
                    actorSystem.scheduler());
                    request.discardEntityBytes(actorSystem);
                    return onComplete(blockChainFuture, blockChainOption -> {
                        try {
                            System.out.println("Sending back the blockchain");
                            System.out.println(new ObjectMapper().writeValueAsString(blockChainOption.get()));
                            return complete(HttpEntities.create(ContentTypes.APPLICATION_JSON,
                                    new ObjectMapper().writeValueAsString(blockChainOption.get())));
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                            return complete(StatusCodes.INTERNAL_SERVER_ERROR);
                        }
                    });

                })
        );
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

    private Route allRoutes() {
        return pathPrefix("api", () ->
                concat(
                        path("transaction" , () -> (
                            concat(handleNewTransactionRoute(), getTransactionStatusRoute())
                        )),
                        path("blockchain", () -> getBlockChainRoute()),
                        path("monitor", () -> monitorTransactionStatusRoute())
                        )
                );
    }

    private void startServer() {
        System.out.println("Starting server");
        CompletionStage<ServerBinding> serverBinding = Http.get(actorSystem)
                .newServerAt("localhost", 443)
                .enableHttps(createHttpsContext())
                .bind(allRoutes());
        serverBinding.whenComplete( (server, throwable) -> {
            if (throwable != null) {
                System.out.println("Something went wrong " + throwable);
            }
            else {
                System.out.println("Server is running");
                startMiningSystem();
            }
        });
    }


    public void run() {
        System.out.println("Starting actor system");
        actorSystem =
                ActorSystem.create(BlockChainSystemBehavior.create(), "BlockChainMiner");

        System.out.println("Getting transaction manager");
        CompletionStage<ActorRef<TransactionManagerBehavior.Command>> transactionManagerFuture =
                AskPattern.ask(actorSystem,
                me -> new BlockChainSystemBehavior.GetTransactionManagerActorRefCommand(me),
                Duration.ofSeconds(5), actorSystem.scheduler());

        transactionManagerFuture.whenComplete( (result, throwable) -> {
            if (throwable != null) {
                System.out.println("Something went wrong - didn't get the transaction manager" + throwable);
            } else {
                transactionManager = result;
                System.out.println("Got the transaction manager");
                startServer();
            }
        } );

    }

    public void startMiningSystem() {


        Flow<Block, HashResult, NotUsed> miningProcess = ActorFlow
                .ask (actorSystem, Duration.ofSeconds(30), (block, self) -> {
                        System.out.println("Starting the mining process with " + block);
                         return   new MiningManagerBehavior.MineBlockCommand(block, self, 6);
                        }
                );

        Source<String, NotUsed> firstHashValue = Source.single("0");

        Flow<String, Block, NotUsed> getNextBlockToMine =
                ActorFlow.ask(transactionManager,
                        Duration.ofSeconds(9999),
                        (lastHash, me) ->
                                new TransactionManagerBehavior.GenerateBlockCommand(me, lastHash)
                        );

        Sink<Block, CompletionStage<Done>> sink = Sink.foreach(block -> {
            actorSystem.tell(new BlockChainSystemBehavior.AddBlockToBlockChain(block));
            CompletionStage<BlockChain> blockChainFuture = AskPattern.ask(
                    actorSystem,
                    me -> new BlockChainSystemBehavior.GetCopyOfBlockChain(me),
                    Duration.ofSeconds(5),
                    actorSystem.scheduler());
            blockChainFuture.whenComplete( (blockChain, throwable) -> {
                blockChain.printAndValidate();
            }
            );
        });

        RunnableGraph<NotUsed> miningGraph = RunnableGraph.fromGraph(
                GraphDSL.create(builder -> {

                    UniformFanInShape<String, String> receiveHashes = builder.add(Merge.create(2));
                    FlowShape<String, Block> applyLastHashToBlock = builder.add(getNextBlockToMine);

                    UniformFanOutShape<Block, Block> broadcast = builder.add(Broadcast.create(2));
                    FlowShape<Block, HashResult> mineBlock = builder.add(miningProcess);

                    UniformFanOutShape<HashResult, HashResult> duplicateHashResult = builder.add(Broadcast.create(2));

                    FanInShape2<Block, HashResult, Block> receiveHashResult =
                            builder.add(ZipWith.create( (block, hashResult) -> {
                                block.setHash(hashResult.getHash());
                                block.setNonce(hashResult.getNonce());
                                return block;
                            } ));

                    SinkShape<Block> sinkShape = builder.add(sink);

                    builder.from(builder.add(firstHashValue))
                            .viaFanIn(receiveHashes); //connect something else to inlet2 of receiveHashes

                    builder.from(receiveHashes)
                            .via(applyLastHashToBlock)
                            .viaFanOut(broadcast);

                    builder.from(broadcast)
                            .toInlet(receiveHashResult.in0());
                    builder.from(broadcast)
                            .via(mineBlock)
                            .viaFanOut(duplicateHashResult)
                            .toInlet(receiveHashResult.in1());

                    builder.from(duplicateHashResult)
                            .via(builder.add(Flow.of(HashResult.class).map( hr -> hr.getHash())))
                            .viaFanIn(receiveHashes);

                    builder.from(receiveHashResult.out()).to(sinkShape);

                    return ClosedShape.getInstance();
                })
        );

        Transaction transaction0 = new Transaction(0, System.currentTimeMillis(), 0,0);
        CompletionStage<Integer> sendingFirstTransactionFutrue = AskPattern.ask(transactionManager,
                me -> new TransactionManagerBehavior.NewTransactionCommand(transaction0, me),
                Duration.ofSeconds(5),
                actorSystem.scheduler());

        sendingFirstTransactionFutrue.whenComplete( (id, throwable) -> {
            System.out.println("Starting mining graph");
            miningGraph.run(actorSystem);
        });


    }
}
