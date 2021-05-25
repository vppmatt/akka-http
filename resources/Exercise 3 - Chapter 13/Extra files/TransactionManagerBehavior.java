package actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import model.Block;
import model.Transaction;
import model.TransactionStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class TransactionManagerBehavior extends AbstractBehavior<TransactionManagerBehavior.Command> {

    public interface Command {

    }

    public static class NewTransactionCommand implements Command {
        private Transaction transaction;
        private ActorRef<Integer> sender;

        public NewTransactionCommand(Transaction transaction, ActorRef<Integer> sender) {
            this.transaction = transaction;
            this.sender = sender;
        }

        public Transaction getTransaction() {
            return transaction;
        }

        public ActorRef<Integer> getSender() {
            return sender;
        }
    }

    public static class GetTransactionStatusCommand implements Command {
        private int transactionId;
        private ActorRef<TransactionStatus> sender;

        public GetTransactionStatusCommand(int transactionId, ActorRef<TransactionStatus> sender) {
            this.transactionId = transactionId;
            this.sender = sender;
        }

        public int getTransactionId() {
            return transactionId;
        }

        public ActorRef<TransactionStatus> getSender() {
            return sender;
        }
    }

    public static class GenerateBlockCommand implements Command {
        private ActorRef<Block> sender;
        private String previousHash;

        public GenerateBlockCommand(ActorRef<Block> sender, String previousHash) {
            this.sender = sender;
            this.previousHash = previousHash;
        }

        public ActorRef<Block> getSender() {
            return sender;
        }

        public String getPreviousHash() {
            return previousHash;
        }
    }

    private TransactionManagerBehavior(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(TransactionManagerBehavior::new);
    }

    private List<Transaction> transactions = new ArrayList<>();

    private int getNextId() {
        return transactions.size();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(NewTransactionCommand.class , message -> {
                    int id = getNextId();
                    message.transaction.setId(id);
                    transactions.add(message.transaction);
                    System.out.println("Received transaction " + message.transaction);
                    message.getSender().tell(id);
                    return Behaviors.same();
                })
                .onMessage(GetTransactionStatusCommand.class, message -> {
                    List<Transaction> matchingTransactions = transactions.stream().filter( it -> it.getId() == message.getTransactionId()).collect(Collectors.toList());
                    if (matchingTransactions.size() == 1) {
                        Transaction transaction = matchingTransactions.get(0);
                        message.getSender().tell(transaction.getStatus());
                    }
                    else {
                        //no matching transaction found with the ID
                        message.getSender().tell(TransactionStatus.UNKNOWN);
                    }
                    return Behaviors.same();
                })
                .onMessage(GenerateBlockCommand.class, message -> {
                    //first mark all existing active transactions as mined
                    List<Transaction> activeTransactions = transactions.stream().filter( it -> it.getStatus() == TransactionStatus.ACTIVE).collect(Collectors.toList());
                    for (Transaction t : activeTransactions) {
                        t.setStatus(TransactionStatus.COMPLETE);
                    }

                    //now find the pending transactions and make them active
                    List<Transaction> pendingTransactions = transactions.stream().filter( it -> it.getStatus() == TransactionStatus.PENDING).collect(Collectors.toList());
                    Block block = new Block(pendingTransactions, message.getPreviousHash());
                    message.getSender().tell(block);
                    for (Transaction t : pendingTransactions) {
                        t.setStatus(TransactionStatus.ACTIVE);
                    }
                    return Behaviors.same();
                })
                .build();
    }
}
