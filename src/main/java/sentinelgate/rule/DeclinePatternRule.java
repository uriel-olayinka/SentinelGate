package sentinelgate.rule;

import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

public class DeclinePatternRule implements FraudRule {

    private static final int DECLINE_THRESHOLD = 3;

    @Override
    public FraudFlag evaluate(Transaction t, Account a) {
        if (t instanceof BankTransaction) {
            BankTransaction bankTx = (BankTransaction) t;

            if (bankTx.getPriorDeclineCount() >= DECLINE_THRESHOLD) {
                String context = String.format("Transaction approved after %d consecutive declined attempts at merchant '%s'.",
                        bankTx.getPriorDeclineCount(), bankTx.getMerchant());

                return new FraudFlag("Suspicious Decline Pattern", "HIGH", context);
            }
        }
        return null;
    }
}
