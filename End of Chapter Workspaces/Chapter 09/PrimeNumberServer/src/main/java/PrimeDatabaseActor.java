import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.headers.AcceptRanges;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrimeDatabaseActor extends AbstractBehavior<PrimeDatabaseActor.Command> {

    public interface Command {}

    public static class NewRequestCommand implements Command {
        private ActorRef<Integer> sender;

        public NewRequestCommand(ActorRef<Integer> sender) {
            this.sender = sender;
        }

        public ActorRef<Integer> getSender() {
            return sender;
        }
    }

    public static class NewResultCommand implements Command {
        private Integer requestId;
        private BigInteger result;

        public NewResultCommand(Integer resultId, BigInteger result) {
            this.requestId = resultId;
            this.result = result;
        }

        public Integer getResultId() {
            return requestId;
        }

        public BigInteger getResult() {
            return result;
        }
    }

    public static class GetResultCommand implements Command {
        private Integer requestId;
        private ActorRef<BigInteger> sender;

        public GetResultCommand(Integer resultId, ActorRef<BigInteger> sender) {
            this.requestId = resultId;
            this.sender = sender;
        }

        public Integer getResultId() {
            return requestId;
        }

        public ActorRef<BigInteger> getSender() {
            return sender;
        }
    }

    public static class GeneratePrimeCommand implements PrimeDatabaseActor.Command {
        private ActorRef<PrimeDatabaseActor.Command> database;
        private Integer requestId;

        public GeneratePrimeCommand(ActorRef<PrimeDatabaseActor.Command> database, Integer requestId) {
            this.database = database;
            this.requestId = requestId;
        }

        public ActorRef<PrimeDatabaseActor.Command> getDatabase() {
            return database;
        }

        public Integer getRequestId() {
            return requestId;
        }
    }

    private List<ActorRef<Command>> generators;

    private PrimeDatabaseActor(ActorContext<Command> context) {
        super(context);
        generators = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ActorRef<Command> primeGenerator = getContext().spawn(PrimeGeneratorActor.create(), "generator" + i);
            generators.add(primeGenerator);
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(PrimeDatabaseActor::new);
    }

    private Map<Integer, BigInteger> database = new HashMap<>();



    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(NewRequestCommand.class, message -> {
                    int nextId = database.size() + 1;
                    database.put(nextId, BigInteger.ZERO);
                    int generatorId = nextId % 4;
                    ActorRef<Command> primeGenerator = generators.get(generatorId);
                    primeGenerator.tell(new GeneratePrimeCommand(getContext().getSelf(),nextId));
                    message.getSender().tell(nextId);
                    return Behaviors.same();
                })
                .onMessage(NewResultCommand.class, message -> {
                    database.put(message.getResultId(), message.getResult());
                    return Behaviors.same();
                })
                .onMessage(GetResultCommand.class, message -> {
                    message.getSender().tell(database.get(message.requestId));
                    return Behaviors.same();
                })
                .build();
    }

}
