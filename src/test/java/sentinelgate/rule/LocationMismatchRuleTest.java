package sentinelgate.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.FraudFlag;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LocationMismatchRuleTest {

    private LocationMismatchRule rule;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        rule = new LocationMismatchRule();
        testAccount = new Account("ACC-LOC");
    }

    private BankTransaction createTxWithLocation(String location) {
        return new BankTransaction("TX", 50.0, LocalDateTime.now(), location, "ACC-LOC", "Store", "VISA", 0);
    }

    @Test
    void testEmptyHistory_ReturnsNullSafely() {
        BankTransaction tx = createTxWithLocation("New York");
        assertNull(rule.evaluate(tx, testAccount), "Should not flag if there is no history to compare against.");
    }

    @Test
    void testSameLocation_ReturnsNull() {
        testAccount.addTransaction(createTxWithLocation("New York"));

        BankTransaction currentTx = createTxWithLocation("New York");
        assertNull(rule.evaluate(currentTx, testAccount), "Consistent locations should not flag.");
    }

    @Test
    void testDifferentLocation_ReturnsMediumSeverityFlag() {
        testAccount.addTransaction(createTxWithLocation("New York"));

        BankTransaction currentTx = createTxWithLocation("London");
        FraudFlag result = rule.evaluate(currentTx, testAccount);

        assertNotNull(result);
        assertEquals("MEDIUM", result.getSeverity());
        assertEquals("Geographic Location Mismatch", result.getRuleName());
    }
}