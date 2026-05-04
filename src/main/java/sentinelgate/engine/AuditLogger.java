package sentinelgate.engine;

import sentinelgate.model.FraudFlag;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuditLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final String logFilePath;
    private final List<AuditEntry> entries = new ArrayList<>();

    public AuditLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    /**
     * Records a single review decision in memory.
     */
    public void log(FraudFlag flag, ReviewManager.Disposition disposition, String analystNotes, int riskScore) {
        entries.add(new AuditEntry(
                LocalDateTime.now().format(FORMATTER),
                flag.getRuleName(),
                flag.getSeverity(),
                riskScore,
                flag.getRawContext(),
                disposition.name(),
                analystNotes == null ? "" : analystNotes.trim()
        ));
    }

    /**
     * Flushes all in-memory entries to a JSON file.
     */
    public void flush() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            for (AuditEntry entry : entries) {
                writer.write(entry.toJson());
                writer.newLine();
            }
            System.out.println("\nAudit log written to: " + logFilePath);
        } catch (IOException e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }

    // --- Stats helpers used by replay mode ---

    public List<AuditEntry> getEntries() {
        return entries;
    }

    // --- Inner record to hold one decision ---

    public static class AuditEntry {
        public final String timestamp;
        public final String ruleName;
        public final String severity;
        public final int riskScore;
        public final String rawContext;
        public final String disposition;
        public final String analystNotes;

        public AuditEntry(String timestamp, String ruleName, String severity,
                          int riskScore, String rawContext, String disposition, String analystNotes) {
            this.timestamp    = timestamp;
            this.ruleName     = ruleName;
            this.severity     = severity;
            this.riskScore    = riskScore;
            this.rawContext   = rawContext;
            this.disposition  = disposition;
            this.analystNotes = analystNotes;
        }

        public String toJson() {
            return String.format(
                    "{\"timestamp\":\"%s\",\"ruleName\":\"%s\",\"severity\":\"%s\"," +
                            "\"riskScore\":%d,\"rawContext\":\"%s\",\"disposition\":\"%s\",\"analystNotes\":\"%s\"}",
                    escape(timestamp), escape(ruleName), escape(severity),
                    riskScore, escape(rawContext), escape(disposition), escape(analystNotes)
            );
        }

        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        }
    }
}