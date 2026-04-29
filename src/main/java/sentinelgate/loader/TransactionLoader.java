package sentinelgate.loader;

import sentinelgate.model.BankTransaction;
import sentinelgate.model.EcommerceOrder;
import sentinelgate.model.Transaction;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class TransactionLoader {
    // Assuming standard ISO date format (yyyy-MM-ddTHH:mm:ss)
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /*
    Loads transactions from a CSV file.
    Expected CSV header:
        id,type,amount,timestamp,location,accountId,merchant,cardType,priorDeclineCount,billingAddress,shippingAddress,isGuestCheckout
     */
    public List<Transaction> loadTransactionsFromCsv(String filePath) {
        List<Transaction> transactions = new ArrayList<>();
        String line = "";
        int lineNumber = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Read & skip header line
            br.readLine();
            lineNumber++;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                // Skipping empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    String[] values = line.split(",");

                    // Ensure we have base transaction fields + type
                    if (values.length < 6) {
                        throw new IllegalArgumentException("Missing base transaction fields.");
                    }

                    String id = values[0].trim();
                    String type = values[1].trim().toUpperCase();
                    double amount = Double.parseDouble(values[2].trim());
                    LocalDateTime timestamp = LocalDateTime.parse(values[3].trim(), formatter);
                    String location = values[4].trim();
                    String accountId = values[5].trim();

                    if (type.equals("BANK")) {
                        String merchant = values[6].trim();
                        String cardType = values[7].trim();
                        int priorDeclineCount = Integer.parseInt(values[8].trim());
                        transactions.add(new BankTransaction(id, amount, timestamp, location, accountId, merchant, cardType, priorDeclineCount));
                    } else if (type.equals("ECOMMERCE")) {
                        String billingAddress = values[9].trim();
                        String shippingAddress = values[10].trim();
                        boolean isGuestCheckout = Boolean.parseBoolean(values[11].trim());
                        transactions.add(new EcommerceOrder(id, amount, timestamp, location, accountId, billingAddress, shippingAddress, isGuestCheckout));
                    } else {
                        System.err.println("Unknown transaction type '" + type + "' on line " + lineNumber + ". Skipping transaction.");
                    }

                } catch (NumberFormatException e) {
                    System.err.println("Error parsing number on line " + lineNumber + ": " + line + " -> " + e.getMessage());
                } catch (DateTimeParseException e) {
                    System.err.println("Error parsing date on line " + lineNumber + ": " + line + " -> " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected error on line " + lineNumber + ": " + line + " -> " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file at " + filePath + ". " + e.getMessage());
        }
        return transactions;
    }
}
