package sentinelgate.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sentinelgate.model.FraudFlag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerTest {

    @TempDir
    Path tempDir;

    // --- AuditEntry.toJson() serialization ---

    @Test
    void testToJson_ProducesWellFormedEntry() {
        // Arrange
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry(
                "2026-04-29T10:00:00", "Large Amount Anomaly", "HIGH",
                5, "Transaction amount $495.00 is more than 5.0x the average.",
                "REVIEWED", "Confirmed legitimate purchase"
        );

        // Act
        String json = entry.toJson();

        // Assert: spot-check every field is present and correctly keyed
        assertTrue(json.contains("\"ruleName\":\"Large Amount Anomaly\""),
                "JSON should contain the rule name.");
        assertTrue(json.contains("\"severity\":\"HIGH\""),
                "JSON should contain the severity.");
        assertTrue(json.contains("\"riskScore\":5"),
                "JSON should contain the numeric risk score without quotes.");
        assertTrue(json.contains("\"disposition\":\"REVIEWED\""),
                "JSON should contain the disposition.");
        assertTrue(json.contains("\"analystNotes\":\"Confirmed legitimate purchase\""),
                "JSON should contain the analyst notes.");
    }

    @Test
    void testToJson_EscapesQuotesInContext() {
        // Arrange: context strings from fraud rules can contain quotes (e.g. merchant names)
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry(
                "2026-04-29T10:00:00", "Decline Pattern", "HIGH",
                3, "Approved after 4 declines at merchant \"Bob's Burgers\".",
                "REVIEWED", ""
        );

        // Act
        String json = entry.toJson();

        // Assert: the embedded quotes must be escaped so the JSON stays valid
        assertTrue(json.contains("\\\"Bob's Burgers\\\""),
                "Quotes inside context strings must be escaped to keep the JSON valid.");
    }

    @Test
    void testToJson_EscapesBackslashesInContext() {
        // Arrange
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry(
                "2026-04-29T10:00:00", "Some Rule", "MEDIUM",
                2, "Path was C:\\Users\\analyst", "DISMISSED", ""
        );

        // Act
        String json = entry.toJson();

        // Assert
        assertTrue(json.contains("C:\\\\Users\\\\analyst"),
                "Backslashes inside field values must be double-escaped.");
    }

    @Test
    void testToJson_NullAnalystNotes_DoesNotCrash() {
        // Edge case: analyst skips the notes prompt — null should not cause a NullPointerException
        AuditLogger.AuditEntry entry = new AuditLogger.AuditEntry(
                "2026-04-29T10:00:00", "Some Rule", "LOW",
                1, "Some context", "PENDING", null
        );

        // Act & Assert
        assertDoesNotThrow(entry::toJson, "toJson() should handle null analystNotes safely.");
    }

    // --- flush() file output ---

    @Test
    void testFlush_WritesOneLinePerEntry() throws IOException {
        // Arrange
        Path logPath = tempDir.resolve("test_audit.jsonl");
        AuditLogger logger = new AuditLogger(logPath.toString());

        FraudFlag flag1 = new FraudFlag("Rule A", "HIGH",   "Context 1");
        FraudFlag flag2 = new FraudFlag("Rule B", "MEDIUM", "Context 2");

        logger.log(flag1, ReviewManager.Disposition.REVIEWED,  "Note A", 5);
        logger.log(flag2, ReviewManager.Disposition.DISMISSED, "Note B", 2);

        // Act
        logger.flush();

        // Assert: each logged entry should produce exactly one line in the file
        List<String> lines = Files.readAllLines(logPath);
        assertEquals(2, lines.size(), "Each logged entry should produce exactly one line in the output file.");
        assertTrue(lines.get(0).contains("Rule A"), "First line should correspond to the first logged flag.");
        assertTrue(lines.get(1).contains("Rule B"), "Second line should correspond to the second logged flag.");
    }

    @Test
    void testFlush_AppendsToExistingFile() throws IOException {
        // Arrange: simulate two separate sessions writing to the same log file
        Path logPath = tempDir.resolve("test_audit.jsonl");

        AuditLogger session1 = new AuditLogger(logPath.toString());
        session1.log(new FraudFlag("Rule A", "HIGH", "Context"), ReviewManager.Disposition.REVIEWED, "", 3);
        session1.flush();

        AuditLogger session2 = new AuditLogger(logPath.toString());
        session2.log(new FraudFlag("Rule B", "LOW", "Context"), ReviewManager.Disposition.DISMISSED, "", 1);
        session2.flush();

        // Act
        List<String> lines = Files.readAllLines(logPath);

        // Assert: both sessions' entries should be present — flush() must append, not overwrite
        assertEquals(2, lines.size(),
                "Flushing a second session should append to the log, not overwrite it.");
    }
}