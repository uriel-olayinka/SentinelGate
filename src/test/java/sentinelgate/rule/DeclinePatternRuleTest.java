package sentinelgate.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.EcommerceOrder;
import sentinelgate.model.FraudFlag;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DeclinePatternRuleTest {

    private DeclinePatternRule rule;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        rule = new DeclinePatternRule();
        testAccount = new Account("ACC-DEC");
    }

    @Test
    void testLowDeclineCount_ReturnsNull() {
        BankTransaction tx = new BankTransaction("TX-1", 50.0, LocalDateTime.now(), "NY", "ACC-DEC", "Store", "VISA", 1);
        assertNull(rule.evaluate(tx, testAccount), "1 prior decline should not trigger the rule.");
    }

    @Test
    void testHighDeclineCount_ReturnsHighSeverityFlag() {
        BankTransaction tx = new BankTransaction("TX-2", 50.0, LocalDateTime.now(), "NY", "ACC-DEC", "Store", "VISA", 4);

        FraudFlag result = rule.evaluate(tx, testAccount);

        assertNotNull(result);
        assertEquals("HIGH", result.getSeverity());
        assertEquals("Suspicious Decline Pattern", result.getRuleName());
    }

    @Test
    void testEcommerceOrder_IsIgnored() {
        EcommerceOrder ecomTx = new EcommerceOrder("TX-3", 100.0, LocalDateTime.now(), "Online", "ACC-DEC", "NY", "NY", false);
        assertNull(rule.evaluate(ecomTx, testAccount), "Rule should safely ignore EcommerceOrders.");
    }
}