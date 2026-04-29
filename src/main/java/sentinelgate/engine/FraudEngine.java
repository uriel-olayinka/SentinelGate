package sentinelgate.engine;

import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;
import sentinelgate.rule.FraudRule;

import java.util.ArrayList;
import java.util.List;

public class FraudEngine {
    private List<FraudRule> rules;

    public FraudEngine() {
        this.rules = new ArrayList<>();
    }

    // Method to register new rules
    public void addRule(FraudRule rule) {
        rules.add(rule);
    }

    /*
    Runs all registerd rules against a transaction.
    Returns a list of all flags triggered (could be empty)
     */
    public List<FraudFlag> evaluateTransaction(Transaction t, Account a) {
        List<FraudFlag> triggeredFlags = new ArrayList<>();

        for (FraudRule rule : rules) {
            FraudFlag result = rule.evaluate(t, a);
            if (result != null) {
                triggeredFlags.add(result);
            }
        }
        return triggeredFlags;
    }
}
