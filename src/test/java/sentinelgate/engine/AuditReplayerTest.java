package sentinelgate.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditReplayerTest {

    @TempDir
    Path tempDir;

    // --- Helper to capture stdout so we can assert on printed output ---

    private String captureOutput(Runnable action) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    // --- Helper to write a pre-formed audit log to a temp file ---

    private Path writeLog(Path dir, List<String> lines) throws IOException {
        Path logPath = dir.resolve("audit.jsonl");
        Files.write(logPath, lines);
        return logPath;
    }

    // --- Stats aggregation ---

    @Test
    void testPrintStats_CountsDispositionsCorrectly() throws IOException {
        // Arrange: 2 REVIEWED, 1 DISMISSED, 1 PENDING
        Path logPath = writeLog(tempDir, List.of(
                "{\"timestamp\":\"2026-04-29T10:00:00\",\"ruleName\":\"Rule A\",\"severity\":\"HIGH\",\"riskScore\":5,\"rawContext\":\"Context A\",\"disposition\":\"REVIEWED\",\"analystNotes\":\"\"}",
                "{\"timestamp\":\"2026-04-29T10:01:00\",\"ruleName\":\"Rule B\",\"severity\":\"MEDIUM\",\"riskScore\":2,\"rawContext\":\"Context B\",\"disposition\":\"REVIEWED\",\"analystNotes\":\"\"}",
                "{\"timestamp\":\"2026-04-29T10:02:00\",\"ruleName\":\"Rule C\",\"severity\":\"HIGH\",\"riskScore\":3,\"rawContext\":\"Context C\",\"disposition\":\"DISMISSED\",\"analystNotes\":\"Looked fine\"}",
                "{\"timestamp\":\"2026-04-29T10:03:00\",\"ruleName\":\"Rule D\",\"severity\":\"LOW\",\"riskScore\":1,\"rawContext\":\"Context D\",\"disposition\":\"PENDING\",\"analystNotes\":\"\"}"
        ));

        AuditReplayer replayer = new AuditReplayer(logPath.toString());

        // Act
        String output = captureOutput(replayer::printStats);

        // Assert: counts must appear verbatim in the summary output
        assertTrue(output.contains("Total flags logged:    4"), "Should report 4 total entries.");
        assertTrue(output.contains("Marked REVIEWED:       2"), "Should count 2 REVIEWED entries.");
        assertTrue(output.contains("Marked DISMISSED:      1"), "Should count 1 DISMISSED entry.");
        assertTrue(output.contains("Left PENDING:          1"), "Should count 1 PENDING entry.");
    }

    @Test
    void testPrintStats_IdentifiesMostTriggeredRule() throws IOException {
        // Arrange: "High Frequency Anomalies" fires three times — it should win
        Path logPath = writeLog(tempDir, List.of(
                "{\"timestamp\":\"2026-04-29T10:00:00\",\"ruleName\":\"High Frequency Anomalies\",\"severity\":\"HIGH\",\"riskScore\":5,\"rawContext\":\"C\",\"disposition\":\"REVIEWED\",\"analystNotes\":\"\"}",
                "{\"timestamp\":\"2026-04-29T10:01:00\",\"ruleName\":\"High Frequency Anomalies\",\"severity\":\"HIGH\",\"riskScore\":5,\"rawContext\":\"C\",\"disposition\":\"REVIEWED\",\"analystNotes\":\"\"}",
                "{\"timestamp\":\"2026-04-29T10:02:00\",\"ruleName\":\"Large Amount Anomaly\",\"severity\":\"HIGH\",\"riskScore\":3,\"rawContext\":\"C\",\"disposition\":\"REVIEWED\",\"analystNotes\":\"\"}",
                "{\"timestamp\":\"2026-04-29T10:03:00\",\"ruleName\":\"High Frequency Anomalies\",\"severity\":\"HIGH\",\"riskScore\":5,\"rawContext\":\"C\",\"disposition\":\"DISMISSED\",\"analystNotes\":\"\"}"
        ));

        AuditReplayer replayer = new AuditReplayer(logPath.toString());

        // Act
        String output = captureOutput(replayer::printStats);

        // Assert
        assertTrue(output.contains("High Frequency Anomalies"),
                "The most frequently triggered rule should be identified in the stats output.");
    }

    @Test
    void testPrintStats_ShowsDismissedFlagsForReExamination() throws IOException {
        // Arrange
        Path logPath = writeLog(tempDir, List.of(
                "{\"timestamp\":\"2026-04-29T10:00:00\",\"ruleName\":\"Suspicious Decline Pattern\",\"severity\":\"HIGH\",\"riskScore\":3,\"rawContext\":\"4 consecutive declines\",\"disposition\":\"DISMISSED\",\"analystNotes\":\"Analyst was unsure\"}"
        ));

        AuditReplayer replayer = new AuditReplayer(logPath.toString());

        // Act
        String output = captureOutput(replayer::printStats);

        // Assert: dismissed flags must be surfaced for re-examination, with their notes
        assertTrue(output.contains("Suspicious Decline Pattern"),
                "Dismissed flag's rule name should appear in the re-examination section.");
        assertTrue(output.contains("Analyst was unsure"),
                "The original analyst note should be shown alongside the dismissed flag.");
    }

    @Test
    void testPrintStats_MissingFile_HandlesGracefully() {
        // Arrange: point at a file that does not exist
        AuditReplayer replayer = new AuditReplayer("definitely_does_not_exist.jsonl");

        // Act & Assert: should not throw — should print a descriptive message instead
        String output = captureOutput(replayer::printStats);
        assertTrue(output.contains("No audit entries found") || output.length() == 0,
                "Missing log file should be handled gracefully without an exception.");
    }

    // --- JSON parser edge cases ---

    @Test
    void testParser_HandlesCommaInsideContextValue() throws IOException {
        // Arrange: this is the bug the old regex parser had — a comma inside a string value
        // would cause the field to be split, corrupting every subsequent field on the line.
        Path logPath = writeLog(tempDir, List.of(
                "{\"timestamp\":\"2026-04-29T10:00:00\",\"ruleName\":\"Large Amount Anomaly\",\"severity\":\"HIGH\",\"riskScore\":5,\"rawContext\":\"Transaction amount $495.00 is more than 5.0x the account average of $99.00, at merchant Best Buy.\",\"disposition\":\"REVIEWED\",\"analystNotes\":\"\"}"
        ));

        AuditReplayer replayer = new AuditReplayer(logPath.toString());

        // Act: if the parser breaks on the comma in rawContext, the disposition will not parse
        // correctly and the REVIEWED count will be 0 instead of 1.
        String output = captureOutput(replayer::printStats);

        // Assert
        assertTrue(output.contains("Marked REVIEWED:       1"),
                "Parser must not split on commas inside quoted string values — disposition must still be read correctly.");
    }

    @Test
    void testParser_HandlesEscapedQuotesInsideValue() throws IOException {
        // Arrange: merchant names like "Bob's Burgers" get written with escaped quotes
        Path logPath = writeLog(tempDir, List.of(
                "{\"timestamp\":\"2026-04-29T10:00:00\",\"ruleName\":\"Decline Pattern\",\"severity\":\"HIGH\",\"riskScore\":3,\"rawContext\":\"Approved after 4 declines at merchant \\\"Bob's Burgers\\\".\",\"disposition\":\"DISMISSED\",\"analystNotes\":\"\"}"
        ));

        AuditReplayer replayer = new AuditReplayer(logPath.toString());

        // Act
        String output = captureOutput(replayer::printStats);

        // Assert: the escaped quote must not terminate the string early, causing the
        // disposition field to be missed.
        assertTrue(output.contains("Marked DISMISSED:      1"),
                "Escaped quotes inside string values must not prematurely end field parsing.");
    }
}