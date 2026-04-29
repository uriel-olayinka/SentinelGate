package sentinelgate.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.EcommerceOrder;
import sentinelgate.model.Transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadValidTransactions() throws IOException {
        // Arrange: Create a temporary CSV file with one bank and one-ecommerce transaction
        Path csvPath = tempDir.resolve("valid_transactions.csv");
        String csvContent = "id,type,amount,timestamp,location,accountId,merchant,cardType,priorDeclineCount,billingAddress,shippingAddress,isGuestCheckout\n" +
                "TX-001,BANK,50.00,2026-04-29T10:00:00,NY,ACC-1,Target,VISA,0,,,\n" +
                "TX-002,ECOMMERCE,150.00,2026-04-29T11:00:00,Online,ACC-2,,,,123 Main St,123 Main St,false\n";
        Files.writeString(csvPath, csvContent);

        TransactionLoader loader = new TransactionLoader();

        // Act: Load the transactions
        List<Transaction> transactions = loader.loadTransactionsFromCsv(csvPath.toString());

        // Assert
        assertEquals(2, transactions.size(), "Should load exactly two transactions.");
        assertTrue(transactions.get(0) instanceof BankTransaction, "First transaction should map to BankTransaction.");
        assertTrue(transactions.get(1) instanceof EcommerceOrder, "Second transaction should map to EcommerceOrder.");
        assertEquals(150.00, transactions.get(1).getAmount(), 0.01, "Amount should be parsed correctly.");
    }

    @Test
    void testMalformedRowIsSkipped() throws IOException {
        // Arrange: Inject a string ("NOT_A_NUMBER") where a double is expected
        Path csvPath = tempDir.resolve("malformed_transactions.csv");
        String csvContent = "id,type,amount,timestamp,location,accountId,merchant,cardType,priorDeclineCount,billingAddress,shippingAddress,isGuestCheckout\n" +
                "TX-001,BANK,50.00,2026-04-29T10:00:00,NY,ACC-1,Target,VISA,0,,,\n" +
                "TX-BROKEN,BANK,NOT_A_NUMBER,2026-04-29T11:00:00,NY,ACC-1,Target,VISA,0,,,\n" +
                "TX-003,BANK,25.00,2026-04-29T12:00:00,NY,ACC-1,Starbucks,VISA,0,,,\n";
        Files.writeString(csvPath, csvContent);

        TransactionLoader loader = new TransactionLoader();

        // Act
        List<Transaction> transactions = loader.loadTransactionsFromCsv(csvPath.toString());

        // Assert
        assertEquals(2, transactions.size(), "Should skip the malformed row and successfully load the other two.");
        assertEquals("TX-001", transactions.get(0).getId());
        assertEquals("TX-003", transactions.get(1).getId());
    }

    @Test
    void testFileNotFoundHandlesGracefully() {
        // Arrange
        TransactionLoader loader = new TransactionLoader();

        // Act: Pass a path that definitely does not exist
        List<Transaction> transactions = loader.loadTransactionsFromCsv("definitely_does_not_exist.csv");

        // Assert
        assertNotNull(transactions, "List should not be null to prevent NullPointerExceptions downstream.");
        assertTrue(transactions.isEmpty(), "List should be empty when the file cannot be read.");
    }
}
