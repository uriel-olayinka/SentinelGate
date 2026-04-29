package sentinelgate.model;

import java.time.LocalDateTime;

public abstract class Transaction {
    private String id;
    private double amount;
    private LocalDateTime timestamp;
    private String location;
    private String accountId;

    public Transaction(String id, double amount, LocalDateTime timestamp, String location, String accountId) {
        this.id = id;
        this.amount = amount;
        this.timestamp = timestamp;
        this.location = location;
        this.accountId = accountId;
    }

    // Getters
    public String getId() { return id; }
    public double getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getLocation() { return location; }
    public String getAccountId() { return accountId; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setLocation(String location) { this.location = location; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public abstract String getTransactionDetails();
}
