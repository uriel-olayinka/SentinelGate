package sentinelgate.rule;

import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

import java.util.List;

public class LocationMismatchRule implements FraudRule {

    @Override
    public FraudFlag evaluate(Transaction t, Account a) {
        List<Transaction> history = a.getTransactionHistory();

        if (history.isEmpty()) {
            return null;
        }

        // Get the most recent transaction (assuming history is chronologically ordered)
        Transaction lastTransaction = history.get(history.size() - 1);

        String currentLocation = t.getLocation();
        String lastLocation = lastTransaction.getLocation();

        if (currentLocation != null && lastLocation != null && !currentLocation.equalsIgnoreCase(lastLocation)) {
            String context = String.format("Transaction location '%s' differs from the most recent transaction location '%s'.",
                    currentLocation, lastLocation);

            return new FraudFlag("Geographic Location Mismatch", "MEDIUM", context);
        }
        return null;
    }
}
