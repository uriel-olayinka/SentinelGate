package sentinelgate;

import sentinelgate.engine.*;
import sentinelgate.loader.TransactionLoader;
import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;
import sentinelgate.rule.*;
import sentinelgate.service.AIExplanationService;

import java.util.*;

public class Main {

    private static final String AUDIT_LOG_PATH = "sentinelgate_audit.jsonl";
    private static final String DIVIDER = "========================================";

    public static void main(String[] args) {

        // --- Replay / stats mode ---
        // Run with: java sentinelgate.Main --stats
        if (args.length > 0 && args[0].equalsIgnoreCase("--stats")) {
            new AuditReplayer(AUDIT_LOG_PATH).printStats();
            return;
        }

        System.out.println(DIVIDER);
        System.out.println("         SENTINELGATE  v2.0               ");
        System.out.println(DIVIDER);

        // 1. Initialize services
        TransactionLoader loader      = new TransactionLoader();
        FraudEngine       engine      = new FraudEngine();
        AIExplanationService aiService = new AIExplanationService();
        ReviewManager     reviewManager = new ReviewManager();
        ReportGenerator   reportGenerator = new ReportGenerator();
        RiskScorer        scorer        = new RiskScorer();
        AuditLogger       auditLogger   = new AuditLogger(AUDIT_LOG_PATH);

        // 2. Register rules
        engine.addRule(new LargeAmountRule());
        engine.addRule(new HighFrequencyRule());
        engine.addRule(new LocationMismatchRule());
        engine.addRule(new AddressMismatchRule());
        engine.addRule(new DeclinePatternRule());

        // 3. Load transactions
        List<Transaction> sessionTransactions = loader.loadTransactionsFromCsv("transactions.csv");
        System.out.println("Loaded " + sessionTransactions.size() + " transactions.\n");

        // 4. Process transactions — build accounts, evaluate rules, collect flags with context
        Map<String, Account> accounts = new HashMap<>();

        // Maps each FraudFlag to: [0] its Account, [1] all flags from the same transaction
        Map<FraudFlag, Account>          flagAccount     = new LinkedHashMap<>();
        Map<FraudFlag, List<FraudFlag>>  flagSiblings    = new LinkedHashMap<>();

        for (Transaction tx : sessionTransactions) {
            accounts.putIfAbsent(tx.getAccountId(), new Account(tx.getAccountId()));
            Account currentAccount = accounts.get(tx.getAccountId());

            List<FraudFlag> flags = engine.evaluateTransaction(tx, currentAccount);

            // Add to history AFTER evaluation so a tx doesn't flag against itself
            currentAccount.addTransaction(tx);

            for (FraudFlag flag : flags) {
                reviewManager.addFlag(flag);
                flagAccount.put(flag, currentAccount);
                flagSiblings.put(flag, flags);  // full sibling list for co-occurrence scoring
            }
        }

        // 5. Rank flags by risk score (highest first)
        List<Map.Entry<FraudFlag, Integer>> rankedFlags = scorer.rankFlags(flagSiblings);

        int total = rankedFlags.size();
        if (total == 0) {
            System.out.println("No suspicious transactions detected this session.");
            reportGenerator.printSessionSummary(reviewManager);
            return;
        }

        System.out.println("Detected " + total + " suspicious flag(s). Beginning review (highest risk first).\n");

        // 6. Interactive review loop
        Scanner scanner = new Scanner(System.in);

        for (int i = 0; i < rankedFlags.size(); i++) {
            FraudFlag flag    = rankedFlags.get(i).getKey();
            int       score   = rankedFlags.get(i).getValue();
            Account   account = flagAccount.get(flag);

            printFlagHeader(flag, score, i + 1, total, account, accounts);

            System.out.println("Consulting AI...");
            AIExplanationService.ConversationSession session = aiService.startSession(flag, account);
            System.out.println("\nAI: " + session.getHistory().get(1).get("text"));

            // Follow-up chat loop — analyst can ask questions before deciding
            String analystNotes = "";
            boolean decided = false;

            while (!decided) {
                System.out.println("\nOptions: [R]eviewed  [D]ismissed  [S]kip  or type a question to consult the AI");
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("R") || input.equalsIgnoreCase("D") || input.equalsIgnoreCase("S")) {
                    decided = true;
                    if (input.equalsIgnoreCase("R")) {
                        reviewManager.markDisposition(flag, ReviewManager.Disposition.REVIEWED);
                        System.out.print("Optional note (press Enter to skip): ");
                        analystNotes = scanner.nextLine().trim();
                        auditLogger.log(flag, ReviewManager.Disposition.REVIEWED, analystNotes, score);
                        System.out.println("✔ Marked REVIEWED.");
                    } else if (input.equalsIgnoreCase("D")) {
                        reviewManager.markDisposition(flag, ReviewManager.Disposition.DISMISSED);
                        System.out.print("Reason for dismissal (press Enter to skip): ");
                        analystNotes = scanner.nextLine().trim();
                        auditLogger.log(flag, ReviewManager.Disposition.DISMISSED, analystNotes, score);
                        System.out.println("✔ Marked DISMISSED.");
                    } else {
                        auditLogger.log(flag, ReviewManager.Disposition.PENDING, "", score);
                        System.out.println("— Skipped (left as PENDING).");
                    }
                } else if (!input.isEmpty()) {
                    // Treat anything else as a chat message to the AI
                    String reply = aiService.chat(session, input);
                    System.out.println("\nAI: " + reply);
                }
            }

            System.out.println();
        }

        // 7. Flush audit log, then print session summary
        auditLogger.flush();
        reportGenerator.printSessionSummary(reviewManager);

        System.out.println("Tip: Run with --stats to review historical audit data and dismissed flags.");
        scanner.close();
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------

    private static void printFlagHeader(FraudFlag flag, int score, int position, int total,
                                        Account account, Map<String, Account> allAccounts) {
        // Calculate a word based on the 1-9 math so it makes intuitive sense
        String riskLevel;
        if (score >= 7) {
            riskLevel = "CRITICAL";
        } else if (score >= 4) {
            riskLevel = "SEVERE";
        } else {
            riskLevel = "ELEVATED";
        }

        System.out.println("\n" + DIVIDER);
        System.out.printf("  FLAG %d of %d  |  Overall Risk: %d/%d (%s)  |  Rule Severity: %s%n",
                position, total, score, RiskScorer.MAX_SCORE, riskLevel, flag.getSeverity());
        System.out.println(DIVIDER);
        System.out.println("Rule:    " + flag.getRuleName());
        System.out.println("Context: " + flag.getRawContext());

        // Cross-account context panel
        if (account != null) {
            List<Transaction> history = account.getTransactionHistory();
            long flagCount = allAccounts.values().stream()
                    .filter(a -> a.getAccountId().equals(account.getAccountId()))
                    .count(); // always 1, but keeps the structure clear

            System.out.println("\n--- Account: " + account.getAccountId() + " ---");
            System.out.println("  Transactions on record : " + history.size());
            System.out.printf("  Average tx amount      : $%.2f%n", account.getAverageTransactionAmount());
        }
    }
}