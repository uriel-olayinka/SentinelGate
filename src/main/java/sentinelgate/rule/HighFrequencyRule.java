package sentinelgate.rule;

import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

import java.time.LocalDateTime;
import java.util.List;

public class HighFrequencyRule implements FraudRule {

    // Thresholds: 8 transactions in 3 minutes is highly suspicious
    private static final int TIME_WINDOW_MINUTES = 3;
    private static final int FREQUENCY_THRESHOLD = 8;

    @Override
    public FraudFlag evaluate(Transaction t, Account a) {
        List<Transaction> history = a.getTransactionHistory();
        if (history.isEmpty()) {
            return null;
        }

        LocalDateTime timeWindowStart = t.getTimestamp().minusMinutes(TIME_WINDOW_MINUTES);
        int recentTransactionCount = 0;

        for (Transaction pastTx : history) {
            // Count transactions that occurred within the defined time window prior to the current one
            if (pastTx.getTimestamp().isAfter(timeWindowStart)
                    && pastTx.getTimestamp().isBefore(t.getTimestamp())
                    || pastTx.getTimestamp().equals(t.getTimestamp())) {
                recentTransactionCount++;
            }
        }

        // Include the current transaction in the count
        recentTransactionCount++;

        if (recentTransactionCount >= FREQUENCY_THRESHOLD) {
            String context = String.format("Account experienced %d transactions within a %d-minute window.",
                    recentTransactionCount, TIME_WINDOW_MINUTES);
            return new FraudFlag("High Frequency Anomalies", "HIGH", context);
        }
        return null;
    }
}
