package sentinelgate.engine;

import sentinelgate.model.FraudFlag;
import java.util.Map;

public class ReportGenerator {

    public void printSessionSummary(ReviewManager reviewManager) {
        Map<FraudFlag, ReviewManager.Disposition> flags = reviewManager.getAllFlags();

        int totalFlags = flags.size();
        int reviewed = 0;
        int dismissed = 0;
        int pending = 0;

        for (ReviewManager.Disposition status : flags.values()) {
            switch (status) {
                case REVIEWED: reviewed++; break;
                case DISMISSED: dismissed++; break;
                case PENDING: pending++; break;
            }
        }

        System.out.println("\n========================================");
        System.out.println("      SENTINELGATE SESSION SUMMARY      ");
        System.out.println("========================================");
        System.out.println("Total Suspicious Transactions Flagged: " + totalFlags);
        System.out.println("Marked as REVIEWED:  " + reviewed);
        System.out.println("Marked as DISMISSED: " + dismissed);
        System.out.println("Left as PENDING:     " + pending);
        System.out.println("========================================\n");
    }
}
