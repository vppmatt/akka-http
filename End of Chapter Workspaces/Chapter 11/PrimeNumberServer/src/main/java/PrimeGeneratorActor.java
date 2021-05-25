import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.math.BigInteger;
import java.util.Random;

public class PrimeGeneratorActor extends AbstractBehavior<PrimeDatabaseActor.Command> {



    private PrimeGeneratorActor(ActorContext<PrimeDatabaseActor.Command> context) {
        super(context);
    }

    public static Behavior<PrimeDatabaseActor.Command> create() {
        return Behaviors.setup(PrimeGeneratorActor::new);
    }

    @Override
    public Receive<PrimeDatabaseActor.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PrimeDatabaseActor.GeneratePrimeCommand.class, message -> {
                    BigInteger bigInteger = new BigInteger(4000, new Random());
                    BigInteger prime = bigInteger.nextProbablePrime();
                    message.getDatabase().tell(new PrimeDatabaseActor.NewResultCommand(message.getRequestId(), prime));
                    return Behaviors.same();
                })
                .build();
    }
}
