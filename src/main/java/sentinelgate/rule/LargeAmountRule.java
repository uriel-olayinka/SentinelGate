package sentinelgate.rule;

import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

public class LargeAmountRule implements FraudRule {
    // Label anything 5x the average transaction as suspicious
    private static final double MULTIPLIER_THRESHOLD = 5.0;

    @Override
    public FraudFlag evaluate(Transaction t, Account a) {
        double average = a.getAverageTransactionAmount();

        // If there is no transaction history, can't reliably flag a transaction as unusually large
        if (average == 0.0) {
            return null;
        }

        if (t.getAmount() > (average * MULTIPLIER_THRESHOLD)) {
            String context = String.format("Transaction amount $%.2f is more than %.1fx the account average of $%.2f.",
                    t.getAmount(), MULTIPLIER_THRESHOLD, average);

            // Returning a high-severity flag with context for the AI
            return new FraudFlag("Large Amount Anomaly", "HIGH", context);
        }
        return null; // Transaction is fine
    }
}
