package sentinelgate.rule;

import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

public interface FraudRule {
    /*
    Evaluates a transaction against a specific rule.
    Returns a FraudFlag if suspicious, or null if it passes safely.
     */
    FraudFlag evaluate(Transaction t, Account a);
}
