package sentinelgate.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.FraudFlag;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LargeAmountRuleTest {

    private LargeAmountRule rule;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        rule = new LargeAmountRule();
        testAccount = new Account("ACC-123");

        // Set up baseline history: Average will be $50.00
        testAccount.addTransaction(createTestTransaction(50.00));
        testAccount.addTransaction(createTestTransaction(50.00));
    }

    private BankTransaction createTestTransaction(double amount) {
        return new BankTransaction("TX-000", amount, LocalDateTime.now(), "NY", "ACC-123", "Store", "VISA", 0);
    }

    @Test
    void testNormalAmount_ReturnsNull() {
        // Normal case: A $60 transaction should NOT flag (threshold is 5x the $50 average)
        BankTransaction tx = createTestTransaction(60.00);
        FraudFlag result = rule.evaluate(tx, testAccount);

        assertNull(result, "Normal transaction should not generate a flag");
    }

    @Test
    void testLargeAmount_ReturnsHighSeverityFlag() {
        // Failure scenario/Anomaly: A $300 transaction (6x the $50 average) should flag
        BankTransaction tx = createTestTransaction(300.00);
        FraudFlag result = rule.evaluate(tx, testAccount);

        assertNotNull(result, "Large transaction should generate a flag");
        assertEquals("HIGH", result.getSeverity());
        assertEquals("Large Amount Anomaly", result.getRuleName());
    }

    void testEmptyHistory_ReturnsNullSafely() {
        // Edge case: A brand new account with no history shouldn't crash or flag
        Account newAccount = new Account("ACC-999");
        BankTransaction tx = createTestTransaction(500.00);

        FraudFlag result = rule.evaluate(tx, newAccount);

        assertNull(result, "Accounts with no history should safely return null.");
    }
}
