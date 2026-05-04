package sentinelgate.engine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuditReplayer {

    private final String logFilePath;

    public AuditReplayer(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    /**
     * Reads the audit log (one JSON object per line) and prints a rich stats summary.
     */
    public void printStats() {
        List<Map<String, String>> entries = readEntries();

        if (entries.isEmpty()) {
            System.out.println("No audit entries found at: " + logFilePath);
            return;
        }

        int total = entries.size();
        int reviewed = 0, dismissed = 0, pending = 0;
        Map<String, Integer> ruleCounts = new HashMap<>();
        int totalRiskScore = 0;

        for (Map<String, String> e : entries) {
            String disp = e.getOrDefault("disposition", "PENDING");
            switch (disp) {
                case "REVIEWED"  -> reviewed++;
                case "DISMISSED" -> dismissed++;
                default          -> pending++;
            }

            String rule = e.getOrDefault("ruleName", "Unknown");
            ruleCounts.merge(rule, 1, Integer::sum);

            try {
                totalRiskScore += Integer.parseInt(e.getOrDefault("riskScore", "0"));
            } catch (NumberFormatException ignored) {}
        }

        String mostCommonRule = ruleCounts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("N/A");

        System.out.println("\n========================================");
        System.out.println("         SENTINELGATE AUDIT STATS       ");
        System.out.println("========================================");
        System.out.printf("  Total flags logged:    %d%n", total);
        System.out.printf("  Marked REVIEWED:       %d%n", reviewed);
        System.out.printf("  Marked DISMISSED:      %d%n", dismissed);
        System.out.printf("  Left PENDING:          %d%n", pending);
        System.out.printf("  Avg risk score:        %.1f / %d%n", (double) totalRiskScore / total, RiskScorer.MAX_SCORE);
        System.out.printf("  Most triggered rule:   %s%n", mostCommonRule);
        System.out.println("========================================");

        // Print dismissed flags so analysts can reconsider
        List<Map<String, String>> dismissed_entries = entries.stream()
                .filter(e -> "DISMISSED".equals(e.get("disposition")))
                .toList();

        if (!dismissed_entries.isEmpty()) {
            System.out.println("\n--- Previously Dismissed Flags (for re-examination) ---");
            for (Map<String, String> e : dismissed_entries) {
                System.out.printf("[%s] %s (Score: %s)%n  Context: %s%n  Notes: %s%n  At: %s%n%n",
                        e.get("severity"), e.get("ruleName"), e.get("riskScore"),
                        e.get("rawContext"), e.get("analystNotes"), e.get("timestamp"));
            }
        }
    }

    // --- Minimal JSON parser (no external deps) ---

    private List<Map<String, String>> readEntries() {
        List<Map<String, String>> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    results.add(parseJsonLine(line));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read audit log: " + e.getMessage());
        }
        return results;
    }

    /**
     * Parses a single JSON object line into a key->value map.
     *
     * Uses a character-by-character state machine instead of regex splitting so that
     * commas inside quoted string values (e.g. "amount $495.00 is more than 5.0x, at merchant")
     * are correctly treated as part of the value rather than field delimiters.
     */
    private Map<String, String> parseJsonLine(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();

        int i = 0;
        int len = json.length();

        // Skip opening brace
        if (i < len && json.charAt(i) == '{') i++;

        while (i < len && json.charAt(i) != '}') {
            // Skip whitespace and commas between pairs
            while (i < len && (json.charAt(i) == ',' || json.charAt(i) == ' ')) i++;

            // Read key (always a quoted string in our format)
            if (i >= len || json.charAt(i) != '"') break;
            String key = readQuotedString(json, i);
            i += key.length() + 2; // +2 for the surrounding quotes

            // Skip the colon separator
            while (i < len && (json.charAt(i) == ':' || json.charAt(i) == ' ')) i++;

            // Read value — either a quoted string or a bare number
            String value;
            if (i < len && json.charAt(i) == '"') {
                value = readQuotedString(json, i);
                i += value.length() + 2;
            } else {
                // Bare value (e.g. riskScore integer) — read until comma or closing brace
                int start = i;
                while (i < len && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                value = json.substring(start, i).trim();
            }

            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }

        return map;
    }

    /**
     * Reads a quoted JSON string starting at the opening quote at position {@code start}.
     * Correctly handles backslash-escaped characters so escaped quotes do not end the string early.
     * Returns the raw content between the outer quotes (escape sequences are left as-is).
     */
    private String readQuotedString(String json, int start) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1; // skip opening quote
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                // Consume the escape sequence as a unit so an escaped quote doesn't end the string
                sb.append(c);
                sb.append(json.charAt(i + 1));
                i += 2;
            } else if (c == '"') {
                break; // closing quote
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}