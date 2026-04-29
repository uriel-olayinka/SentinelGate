package sentinelgate.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.FraudFlag;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HighFrequencyRuleTest {

    private HighFrequencyRule rule;
    private Account testAccount;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        rule = new HighFrequencyRule();
        testAccount = new Account("ACC-FREQ");
        baseTime = LocalDateTime.now();
    }

    private BankTransaction createTxAtTime(LocalDateTime time) {
        return new BankTransaction("TX-TIME", 10.0, time, "NY", "ACC-FREQ", "Store", "VISA", 0);
    }

    @Test
    void testNormalFrequency_ReturnsNull() {
        // Arrange: 5 transactions spread out over 10 minutes
        testAccount.addTransaction(createTxAtTime(baseTime.minusMinutes(10)));
        testAccount.addTransaction(createTxAtTime(baseTime.minusMinutes(8)));
        testAccount.addTransaction(createTxAtTime(baseTime.minusMinutes(5)));
        testAccount.addTransaction(createTxAtTime(baseTime.minusMinutes(2)));

        BankTransaction currentTx = createTxAtTime(baseTime);

        // Act
        FraudFlag result = rule.evaluate(currentTx, testAccount);

        // Assert
        assertNull(result, "Should not flag normal transaction frequency.");
    }

    @Test
    void testHighFrequency_ReturnsFlag() {
        // Arrange: 7 previous transactions within the last 2 minutes
        for (int i = 1; i <= 7; i++) {
            testAccount.addTransaction(createTxAtTime(baseTime.minusMinutes(1)));
        }

        // The 8th transaction happens right now
        BankTransaction currentTx = createTxAtTime(baseTime);

        // Act
        FraudFlag result = rule.evaluate(currentTx, testAccount);

        // Assert
        assertNotNull(result, "Should flag when 8 transactions happen within 3 minutes.");
        assertEquals("HIGH", result.getSeverity());
        assertEquals("High Frequency Anomalies", result.getRuleName());
    }
}