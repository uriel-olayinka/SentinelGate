package sentinelgate.model;

import java.util.ArrayList;
import java.util.List;

public class Account {
    private String accountId;
    private List<Transaction> transactionHistory;

    public Account(String accountId) {
        this.accountId = accountId;
        this.transactionHistory = new ArrayList<>();
    }

    // Getters
    public String getAccountId() { return accountId; }
    public List<Transaction> getTransactionHistory() { return transactionHistory; }

    // Core functionality
    public void addTransaction(Transaction transaction) {
        transactionHistory.add(transaction);
    }

    // Helper method that is helpful for LargeAmountRule
    public double getAverageTransactionAmount() {
        if (transactionHistory.isEmpty()) {
            return 0.0; // avoid dividing by zero
        }

        double sum = 0;
        for (Transaction t : transactionHistory) {
            sum += t.getAmount();
        }
        return sum / transactionHistory.size();
    }

}
