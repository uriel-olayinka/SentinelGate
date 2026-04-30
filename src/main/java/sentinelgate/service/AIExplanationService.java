package sentinelgate.service;

import sentinelgate.model.FraudFlag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AIExplanationService {

    private final HttpClient httpClient;
    private final String apiUrl;

    public AIExplanationService() {
        // Pull the key from the system environment securely
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("WARNING: GEMINI_API_KEY environment variable is missing.");
            this.apiUrl = ""; // Will trigger the fallback mechanism cleanly
        } else {
            this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + apiKey;
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /*
    Takes a FraudFlag and returns a human-readable explanation
     */
    public String getExplanation(FraudFlag flag) {
        if (flag == null) {
            return "No suspicious activity detected.";
        }

        try {
            String prompt = buildPrompt(flag);

            // Constructing a lightweight, standard JSON payload string
            String requestBody = String.format("{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]}", escapeJson(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = sendHttpRequest(request);

            if (response.statusCode() == 200 && response.body() != null) {
                String rawAiText = extractTextFromJson(response.body());
                return sanitizeAndValidate(rawAiText, flag);
            } else {
                System.err.println("API Warning: Received HTTP " + response.statusCode());
                return getFallbackExplanation(flag);
            }
        } catch (Exception e) {
            // Catches timeouts, network drops, and parse errors cleanly
            System.err.println("AI Service Failure: " + e.getMessage() + ". Falling back to template.");
            return getFallbackExplanation(flag);
        }
    }

    private String buildPrompt(FraudFlag flag) {
        return "You are a helpful banking assistant. Explain the following flagged transaction to a user in one short, clear, and professional sentence. " +
                "Rule: " + flag.getRuleName() + ". " +
                "Details: " + flag.getRawContext();
    }

    // The fallback mechanism ensures that the system never stalls or crashes
    private String getFallbackExplanation(FraudFlag flag) {
        return "Transaction flagged by system rule [" + flag.getRuleName() + "]: " + flag.getRawContext();
    }

    private String sanitizeAndValidate(String aiOutput, FraudFlag flag) {
        if (aiOutput == null || aiOutput.trim().isEmpty()) {
            return getFallbackExplanation(flag);
        }

        // Remove rogue newlines and ensure the explanation isn't a giant paragraph
        String clean = aiOutput.replace("\n", " ").trim();
        if (clean.length() > 250) {
            clean = clean.substring(0, 247) + "...";
        }
        return clean;
    }

    // A lightweight JSON parser to grab the "text" value without external libraries
    private String extractTextFromJson(String jsonResponse) {
        try {
            int textKeyIndex = jsonResponse.indexOf("\"text\":");
            if (textKeyIndex == -1) return "";

            int startQuote = jsonResponse.indexOf("\"", textKeyIndex + 7);
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);

            // Handle escaped quotes inside the AI's response safely
            while (jsonResponse.charAt(endQuote - 1) == '\\') {
                endQuote = jsonResponse.indexOf("\"", endQuote + 1);
            }

            String text = jsonResponse.substring(startQuote + 1, endQuote);
            // Clean up standard JSON escape characters
            return text.replace("\\n", " ").replace("\\\"", "\"");
        } catch (Exception e) {
            return ""; // Triggers the fallback up the chain
        }
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"");
    }

    // Making this "protected" so Mockito can intercept it during tests
    protected HttpResponse<String> sendHttpRequest(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
