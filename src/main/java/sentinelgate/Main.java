package sentinelgate;

import sentinelgate.engine.FraudEngine;
import sentinelgate.engine.ReportGenerator;
import sentinelgate.engine.ReviewManager;
import sentinelgate.loader.TransactionLoader;
import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;
import sentinelgate.rule.*;
import sentinelgate.service.AIExplanationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting SentinelGate...");

        // 1. Initialize Services
        TransactionLoader loader = new TransactionLoader();
        FraudEngine engine = new FraudEngine();
        AIExplanationService aiService = new AIExplanationService();
        ReviewManager reviewManager = new ReviewManager();
        ReportGenerator reportGenerator = new ReportGenerator();

        // 2. Register Rules
        engine.addRule(new LargeAmountRule());
        engine.addRule(new HighFrequencyRule());
        engine.addRule(new LocationMismatchRule());
        engine.addRule(new AddressMismatchRule());
        engine.addRule(new DeclinePatternRule());

        // 3. Load Data
        List<Transaction> sessionTransactions = loader.loadTransactionsFromCsv("transactions.csv");
        System.out.println("Loaded " + sessionTransactions.size() + " transactions.");

        // Need to build Account objects on the fly to hold history
        Map<String, Account> accounts = new HashMap<>();

        // 4. Process Transactions
        for (Transaction tx : sessionTransactions) {
            // Get or create the account history
            accounts.putIfAbsent(tx.getAccountId(), new Account(tx.getAccountId()));
            Account currentAccount = accounts.get(tx.getAccountId());

            // Evaluate the transaction against all rules
            List<FraudFlag> flags = engine.evaluateTransaction(tx, currentAccount);

            // Add the current transaction to history after evaluation so it doesn't flag against itself
            currentAccount.addTransaction(tx);

            // Register any flags with the ReviewManager
            for (FraudFlag flag : flags) {
                reviewManager.addFlag(flag);
            }
        }

        // 5. User Interaction Loop (CLI)
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- Interactive Flag Review ---");

        for (FraudFlag flag : reviewManager.getAllFlags().keySet()) {
            System.out.println("\nALERT: " + flag.getRuleName() + " (" + flag.getSeverity() + " Severity)");
            System.out.println("Context: " + flag.getRawContext());

            System.out.println("\nAsking AI for human-readable explanation...");
            String explanation = aiService.getExplanation(flag);
            System.out.println("AI Explanation: " + explanation);

            System.out.print("\nAction ( [R]eviewed / [D]ismissed / [S]kip ): ");
            String input = scanner.nextLine().trim().toUpperCase();

            if (input.equals("R")) {
                reviewManager.markDisposition(flag, ReviewManager.Disposition.REVIEWED);
            } else if (input.equals("D")) {
                reviewManager.markDisposition(flag, ReviewManager.Disposition.DISMISSED);
            }
        }

        // 6. Generate Final Report
        reportGenerator.printSessionSummary(reviewManager);
        scanner.close();
    }
}
