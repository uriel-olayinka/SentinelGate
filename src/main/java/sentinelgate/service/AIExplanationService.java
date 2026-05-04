package sentinelgate.service;

import sentinelgate.model.Account;
import sentinelgate.model.FraudFlag;
import sentinelgate.model.Transaction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AIExplanationService {

    private final HttpClient httpClient;
    private final String apiKey;

    public AIExplanationService() {
        this.apiKey = System.getenv("OPENROUTER_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("WARNING: OPENROUTER_API_KEY environment variable is missing. Fallback mode active.");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ------------------------------------------------------------------
    // Single-shot explanation (used for the initial flag summary)
    // ------------------------------------------------------------------

    public String getExplanation(FraudFlag flag, Account account) {
        if (flag == null) return "No suspicious activity detected.";

        String prompt = buildInitialPrompt(flag, account);
        return callOpenRouter(List.of(Map.of("role", "user", "text", prompt)));
    }

    // ------------------------------------------------------------------
    // Multi-turn conversational follow-up
    // ------------------------------------------------------------------

    /**
     * Holds the conversation history for a single flag review session.
     * Each entry is a map with "role" (user/model) and "text".
     */
    public static class ConversationSession {
        private final List<Map<String, String>> history = new ArrayList<>();

        public void addTurn(String role, String text) {
            history.add(Map.of("role", role, "text", text));
        }

        public List<Map<String, String>> getHistory() {
            return history;
        }
    }

    /**
     * Sends a follow-up user message within an existing session and returns the AI reply.
     * The full conversation history is included so the AI has context.
     */
    public String chat(ConversationSession session, String userMessage) {
        session.addTurn("user", userMessage);
        String reply = callOpenRouter(session.getHistory());
        session.addTurn("model", reply);
        return reply;
    }

    /**
     * Creates a fresh ConversationSession seeded with the initial flag explanation.
     * This is the entry point for starting an interactive review.
     */
    public ConversationSession startSession(FraudFlag flag, Account account) {
        ConversationSession session = new ConversationSession();
        String systemPrompt = buildInitialPrompt(flag, account);
        session.addTurn("user", systemPrompt);
        String firstReply = callOpenRouter(session.getHistory());
        session.addTurn("model", firstReply);
        return session;
    }

    // ------------------------------------------------------------------
    // Prompt builders
    // ------------------------------------------------------------------

    private String buildInitialPrompt(FraudFlag flag, Account account) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are SentinelGate, a fraud analysis assistant for banking analysts. ");
        sb.append("A transaction has been flagged. Explain the risk clearly and professionally in 2-3 sentences. ");
        sb.append("Then wait for follow-up questions.\n\n");

        sb.append("FLAG: ").append(flag.getRuleName()).append(" (").append(flag.getSeverity()).append(" severity)\n");
        sb.append("DETAILS: ").append(flag.getRawContext()).append("\n\n");

        if (account != null) {
            List<Transaction> history = account.getTransactionHistory();
            sb.append("ACCOUNT CONTEXT (").append(account.getAccountId()).append("):\n");
            sb.append("  Total transactions on record: ").append(history.size()).append("\n");
            sb.append("  Average transaction amount: $").append(String.format("%.2f", account.getAverageTransactionAmount())).append("\n");

            // Show last 5 transactions for context
            int start = Math.max(0, history.size() - 5);
            sb.append("  Last ").append(history.size() - start).append(" transactions:\n");
            for (int i = start; i < history.size(); i++) {
                Transaction t = history.get(i);
                sb.append("    - ").append(t.getTransactionDetails())
                        .append(" at ").append(t.getTimestamp()).append("\n");
            }
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------
    // HTTP layer — OpenRouter (OpenAI-compatible API)
    // ------------------------------------------------------------------

    private String callOpenRouter(List<Map<String, String>> turns) {
        try {
            String url = "https://openrouter.ai/api/v1/chat/completions";
            String requestBody = buildRequestBody(turns);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = sendHttpRequest(request);

            if (response.statusCode() == 200 && response.body() != null) {
                String text = extractTextFromJson(response.body());
                return sanitize(text);
            } else {
                System.err.println("API Warning: HTTP " + response.statusCode());
                return getFallbackExplanation();
            }
        } catch (Exception e) {
            System.err.println("AI Service Failure: " + e.getMessage());
            return getFallbackExplanation();
        }
    }

     // Builds an OpenAI-compatible "messages" JSON payload
    private String buildRequestBody(List<Map<String, String>> turns) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"meta-llama/llama-3.1-8b-instruct\",\"messages\":[");
        for (int i = 0; i < turns.size(); i++) {
            Map<String, String> turn = turns.get(i);
            String role = turn.get("role").equals("model") ? "assistant" : turn.get("role");
            String text = escapeJson(turn.get("text"));
            sb.append("{\"role\":\"").append(role).append("\",")
                    .append("\"content\":\"").append(text).append("\"}");
            if (i < turns.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String getFallbackExplanation() {
        return "[AI unavailable] Please review the raw context above manually.";
    }

    private String sanitize(String text) {
        if (text == null || text.isBlank()) return getFallbackExplanation();
        return text.replace("\n", " ").trim();
    }

    // OpenRouter response format: {"choices":[{"message":{"content":"..."}}]}
    private String extractTextFromJson(String json) {
        try {
            int idx = json.indexOf("\"content\":");
            if (idx == -1) return "";
            int start = json.indexOf("\"", idx + 10) + 1;
            int end = start;
            while (end < json.length()) {
                end = json.indexOf("\"", end);
                if (json.charAt(end - 1) != '\\') break;
                end++;
            }
            return json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        } catch (Exception e) {
            return "";
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    // Protected so tests can subclass and intercept
    protected HttpResponse<String> sendHttpRequest(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}