package sentinelgate.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.FraudFlag;
import sentinelgate.rule.FraudRule;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FraudEngineTest {

    private FraudEngine engine;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        engine = new FraudEngine();
        testAccount = new Account("ACC-TEST");

        // We can create simple anonymous classes/lambdas to act as "mock" rules
        // to strictly test the engine's aggregation capability without relying on real rule logic.

        FraudRule ruleThatAlwaysFlags = (t, a) -> new FraudFlag("Always Flags", "LOW", "Test Context");
        FraudRule ruleThatNeverFlags = (t, a) -> null;
        FraudRule anotherRuleThatFlags = (t, a) -> new FraudFlag("Another Flag", "HIGH", "Test Context 2");

        engine.addRule(ruleThatAlwaysFlags);
        engine.addRule(ruleThatNeverFlags);
        engine.addRule(anotherRuleThatFlags);
    }

    @Test
    void testEngineAggregatesMultipleFlags() {
        // Arrange
        BankTransaction tx = new BankTransaction("TX-1", 100.0, LocalDateTime.now(), "NY", "ACC-TEST", "Store", "VISA", 0);

        // Act
        List<FraudFlag> result = engine.evaluateTransaction(tx, testAccount);

        // Assert
        assertNotNull(result, "Engine should return a list, not null.");
        assertEquals(2, result.size(), "Engine should aggregate exactly two flags from the three registered rules.");

        // Verify both flags are present
        boolean hasLowFlag = result.stream().anyMatch(f -> f.getSeverity().equals("LOW"));
        boolean hasHighFlag = result.stream().anyMatch(f -> f.getSeverity().equals("HIGH"));

        assertTrue(hasLowFlag, "List should contain the LOW severity flag.");
        assertTrue(hasHighFlag, "List should contain the HIGH severity flag.");
    }

    @Test
    void testEngineWithNoRulesReturnsEmptyList() {
        // Arrange
        FraudEngine emptyEngine = new FraudEngine();
        BankTransaction tx = new BankTransaction("TX-1", 100.0, LocalDateTime.now(), "NY", "ACC-TEST", "Store", "VISA", 0);

        // Act
        List<FraudFlag> result = emptyEngine.evaluateTransaction(tx, testAccount);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Engine with no rules should return an empty list.");
    }
}