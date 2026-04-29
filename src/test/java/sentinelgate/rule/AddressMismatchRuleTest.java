package sentinelgate.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.EcommerceOrder;
import sentinelgate.model.FraudFlag;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AddressMismatchRuleTest {

    private AddressMismatchRule rule;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        rule = new AddressMismatchRule();
        testAccount = new Account("ACC-ECOM");
    }

    @Test
    void testMatchingAddresses_ReturnsNull() {
        EcommerceOrder order = new EcommerceOrder("TX-1", 100.0, LocalDateTime.now(), "Online", "ACC-ECOM",
                "123 Main St", "123 Main St", false);

        assertNull(rule.evaluate(order, testAccount), "Matching addresses should not flag.");
    }

    @Test
    void testMismatchedAddresses_RegisteredUser_ReturnsMediumSeverity() {
        EcommerceOrder order = new EcommerceOrder("TX-2", 100.0, LocalDateTime.now(), "Online", "ACC-ECOM",
                "123 Main St", "999 Other St", false); // Not a guest

        FraudFlag result = rule.evaluate(order, testAccount);

        assertNotNull(result);
        assertEquals("MEDIUM", result.getSeverity(), "Mismatch for registered user should be MEDIUM severity.");
    }

    @Test
    void testMismatchedAddresses_GuestCheckout_ReturnsHighSeverity() {
        EcommerceOrder order = new EcommerceOrder("TX-3", 100.0, LocalDateTime.now(), "Online", "ACC-ECOM",
                "123 Main St", "999 Other St", true); // Is a guest

        FraudFlag result = rule.evaluate(order, testAccount);

        assertNotNull(result);
        assertEquals("HIGH", result.getSeverity(), "Mismatch for guest checkout should elevate to HIGH severity.");
    }

    @Test
    void testBankTransaction_IsIgnored() {
        BankTransaction bankTx = new BankTransaction("TX-4", 100.0, LocalDateTime.now(), "NY", "ACC-ECOM", "Store", "VISA", 0);
        assertNull(rule.evaluate(bankTx, testAccount), "Rule should safely ignore BankTransactions.");
    }
}