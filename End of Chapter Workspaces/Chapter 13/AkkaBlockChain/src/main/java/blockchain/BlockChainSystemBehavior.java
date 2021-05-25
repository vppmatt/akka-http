package blockchain;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;
import model.Block;
import model.BlockChain;

public class BlockChainSystemBehavior extends AbstractBehavior<MiningManagerBehavior.Command> {

    private PoolRouter<MiningManagerBehavior.Command> managerPoolRouter;
    private ActorRef<MiningManagerBehavior.Command> managers;
    private ActorRef<TransactionManagerBehavior.Command> transactionManager;
    private BlockChain blockChain;

    public static class GetTransactionManagerActorRefCommand implements
            MiningManagerBehavior.Command {

        private ActorRef<ActorRef<TransactionManagerBehavior.Command>> sender;

        public GetTransactionManagerActorRefCommand(ActorRef<ActorRef<TransactionManagerBehavior.Command>> sender) {
            this.sender = sender;
        }

        public ActorRef<ActorRef<TransactionManagerBehavior.Command>> getSender() {
            return sender;
        }
    }

    public static class AddBlockToBlockChain implements MiningManagerBehavior.Command {
        private Block block;

        public AddBlockToBlockChain(Block block) {
            this.block = block;
        }

        public Block getBlock() {
            return block;
        }
    }

    public static class GetCopyOfBlockChain implements MiningManagerBehavior.Command {
        private ActorRef<BlockChain> sender;

        public GetCopyOfBlockChain(ActorRef<BlockChain> sender) {
            this.sender = sender;
        }

        public ActorRef<BlockChain> getSender() {
            return sender;
        }
    }

    private BlockChainSystemBehavior(ActorContext<MiningManagerBehavior.Command> context) {
        super(context);
        managerPoolRouter = Routers.pool(3,
                Behaviors.supervise(MiningManagerBehavior.create()).onFailure(SupervisorStrategy.restart()));
        managers = getContext().spawn(managerPoolRouter, "managerPool");
        transactionManager= getContext().spawn(TransactionManagerBehavior.create(), "transactionManager");
        blockChain = new BlockChain();
    }

    public static Behavior<MiningManagerBehavior.Command> create() {
        return Behaviors.setup(BlockChainSystemBehavior::new);
    }

    @Override
    public Receive<MiningManagerBehavior.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetTransactionManagerActorRefCommand.class, message -> {
                    message.getSender().tell(transactionManager);
                    return Behaviors.same();
                })
                .onMessage(AddBlockToBlockChain.class, message -> {
                    blockChain.addBlock(message.getBlock());
                    return Behaviors.same();
                })
                .onMessage(GetCopyOfBlockChain.class, message -> {
                    message.getSender().tell(blockChain);
                    return Behaviors.same();
                })
                .onAnyMessage(message -> {
                    managers.tell(message);
                    return Behaviors.same();
                })
                .build();
    }
}
