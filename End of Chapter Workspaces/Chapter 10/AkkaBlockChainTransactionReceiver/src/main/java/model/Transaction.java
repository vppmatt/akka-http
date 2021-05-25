package model;

public class Transaction {
    private int id;
    private long Timestamp;
    private int accountNumber;
    private double amount;
    private TransactionStatus status;

    public Transaction() {
        super();
        Timestamp = System.currentTimeMillis();
        this.status = TransactionStatus.PENDING;
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public long getTimestamp() {
        return Timestamp;
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
        return "Transaction [id=" + id + ", Timestamp=" + Timestamp + ", accountNumber=" + accountNumber + ", amount="
                + amount + "]";
    }

}
