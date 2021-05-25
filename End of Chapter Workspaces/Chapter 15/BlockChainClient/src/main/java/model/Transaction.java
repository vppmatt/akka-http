package model;

public class Transaction {
    private int id;
    private long timestamp;
    private int accountNumber;
    private double amount;
    private TransactionStatus status;

    public Transaction() {
        super();
        timestamp = System.currentTimeMillis();
        this.status = TransactionStatus.PENDING;
    }

    public Transaction(int id, long timestamp, int accountNumber, double amount) {
        super();
        this.status = TransactionStatus.PENDING;
        this.id = id;
        this.timestamp = timestamp;
        this.accountNumber = accountNumber;
        this.amount = amount;
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getAccountNumber() {
        return accountNumber;
    }
    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }
    public double getAmount() {
        return amount;
    }
    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "Transaction [id=" + id + ", Timestamp=" + timestamp + ", accountNumber=" + accountNumber + ", amount="
                + amount + "]";
    }

}
