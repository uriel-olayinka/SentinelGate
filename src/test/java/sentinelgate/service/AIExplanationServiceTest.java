package sentinelgate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.Account;
import sentinelgate.model.BankTransaction;
import sentinelgate.model.FraudFlag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the updated AIExplanationService, covering:
 *   - the original single-shot explanation (carried over)
 *   - the new startSession() method with account context injection
 *   - the new chat() multi-turn conversation management
 */
class AIExplanationServiceTest {

    private AIExplanationService aiService;
    private FraudFlag testFlag;
    private Account testAccount;

    // Variables to control what our fake HTTP client does
    private int mockStatusCode;
    private String mockBody;
    private boolean forceNetworkFailure;

    // Captures the raw request body sent to the API so we can assert on its contents
    private String capturedRequestBody;

    @BeforeEach
    void setUp() {
        testFlag   = new FraudFlag("Test Rule", "HIGH", "Raw test context");
        testAccount = new Account("ACC-123");
        testAccount.addTransaction(new BankTransaction("TX-001", 50.0,  LocalDateTime.now().minusHours(2), "NY", "ACC-123", "Target",     "VISA", 0));
        testAccount.addTransaction(new BankTransaction("TX-002", 75.0,  LocalDateTime.now().minusHours(1), "NY", "ACC-123", "Starbucks",  "VISA", 0));
        testAccount.addTransaction(new BankTransaction("TX-003", 500.0, LocalDateTime.now(),               "NY", "ACC-123", "Apple Store","VISA", 0));

        mockStatusCode      = 200;
        mockBody            = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"This transaction looks suspicious.\"}]}}]}";
        forceNetworkFailure = false;
        capturedRequestBody = null;

        aiService = new AIExplanationService() {
            @Override
            protected HttpResponse<String> sendHttpRequest(HttpRequest request) throws IOException {
                // Capture the body for inspection in tests that need it
                capturedRequestBody = request.bodyPublisher()
                        .map(p -> {
                            var subscriber = new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                                final StringBuilder sb = new StringBuilder();
                                public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
                                public void onNext(java.nio.ByteBuffer b) { sb.append(java.nio.charset.StandardCharsets.UTF_8.decode(b)); }
                                public void onError(Throwable t) {}
                                public void onComplete() {}
                            };
                            p.subscribe(subscriber);
                            return subscriber.sb.toString();
                        })
                        .orElse("");

                if (forceNetworkFailure) {
                    throw new IOException("Connection timed out");
                }
                return new TestHttpResponse(mockStatusCode, mockBody);
            }
        };
    }

    // --- Original single-shot explanation tests (carried over) ---

    @Test
    void testSuccessfulApiResponse_ReturnsCleanExplanation() {
        // Arrange
        mockStatusCode = 200;
        mockBody = "{\"choices\":[{\"message\":{\"content\":\"The transaction was unusually large.\"}}]}";

        // Act
        String result = aiService.getExplanation(testFlag, testAccount);

        // Assert
        assertEquals("The transaction was unusually large.", result,
                "Service should extract and return the text from a successful API response.");
    }

    @Test
    void testApiErrorResponse_TriggersFallback() {
        // Arrange
        mockStatusCode = 500;

        // Act
        String result = aiService.getExplanation(testFlag, testAccount);

        // Assert
        assertTrue(result.contains("AI unavailable"),
                "Service should fall back to a safe message when the API returns an error code.");
    }

    @Test
    void testNetworkFailure_TriggersFallbackWithoutCrashing() {
        // Arrange
        forceNetworkFailure = true;

        // Act
        String result = aiService.getExplanation(testFlag, testAccount);

        // Assert
        assertNotNull(result, "Service should safely catch the exception and not return null.");
        assertTrue(result.contains("AI unavailable"),
                "Service should fall back to a safe message when the network fails.");
    }

    // --- startSession() — account context injection ---

    @Test
    void testStartSession_IncludesAccountIdInPrompt() {
        // Act: starting a session should immediately fire one API call with the seeded prompt
        aiService.startSession(testFlag, testAccount);

        // Assert: the account ID must appear in the request body sent to Open Router
        assertNotNull(capturedRequestBody, "A request should have been sent to the AI API.");
        assertTrue(capturedRequestBody.contains("ACC-123"),
                "The account ID should be included in the initial prompt for context.");
    }

    @Test
    void testStartSession_IncludesTransactionHistorySummaryInPrompt() {
        // Act
        aiService.startSession(testFlag, testAccount);

        // Assert: the prompt should mention transaction history so the AI can reason about it
        assertNotNull(capturedRequestBody);
        assertTrue(capturedRequestBody.contains("Total transactions on record"),
                "The initial prompt should include the account's transaction count.");
        assertTrue(capturedRequestBody.contains("Average transaction amount"),
                "The initial prompt should include the account's average transaction amount.");
    }

    @Test
    void testStartSession_SeedsConversationWithTwoTurns() {
        // Act: one user turn (the prompt) and one model turn (the AI reply) should be seeded
        AIExplanationService.ConversationSession session = aiService.startSession(testFlag, testAccount);

        // Assert
        List<java.util.Map<String, String>> history = session.getHistory();
        assertEquals(2, history.size(),
                "A fresh session should have exactly 2 turns: the seeded user prompt and the AI reply.");
        assertEquals("user",  history.get(0).get("role"), "First turn should be the user prompt.");
        assertEquals("model", history.get(1).get("role"), "Second turn should be the AI reply.");
    }

    @Test
    void testStartSession_NullAccount_DoesNotCrash() {
        // Edge case: account context is optional — passing null should not throw
        assertDoesNotThrow(() -> aiService.startSession(testFlag, null),
                "startSession() should handle a null account gracefully.");
    }

    // --- chat() — multi-turn conversation history growth ---

    @Test
    void testChat_AppendsUserAndModelTurnsToHistory() {
        // Arrange: start a session (2 turns seeded), then send one follow-up
        AIExplanationService.ConversationSession session = aiService.startSession(testFlag, testAccount);
        int turnsAfterStart = session.getHistory().size(); // should be 2

        // Act
        aiService.chat(session, "Is this merchant known for fraud?");

        // Assert: one user turn and one model turn should have been appended
        assertEquals(turnsAfterStart + 2, session.getHistory().size(),
                "Each chat() call should append exactly one user turn and one model turn.");
    }

    @Test
    void testChat_SendsFullHistoryInRequest() {
        // Arrange: build up a 2-turn session, then send a follow-up
        AIExplanationService.ConversationSession session = aiService.startSession(testFlag, testAccount);
        session.addTurn("model", "The transaction is suspicious because it is 10x the average.");

        // Act: the follow-up request body must contain the prior model turn so Open Router has context
        aiService.chat(session, "What should I do next?");

        // Assert
        assertNotNull(capturedRequestBody);
        assertTrue(capturedRequestBody.contains("10x the average"),
                "The full conversation history must be included in each follow-up request.");
    }

    @Test
    void testChat_MultipleRoundsGrowHistoryCorrectly() {
        // Arrange
        AIExplanationService.ConversationSession session = aiService.startSession(testFlag, testAccount);

        // Act: simulate an analyst asking three follow-up questions
        aiService.chat(session, "Question one");
        aiService.chat(session, "Question two");
        aiService.chat(session, "Question three");

        // Assert: 2 (seed) + 3 * 2 (each Q adds user + model) = 8
        assertEquals(8, session.getHistory().size(),
                "History should grow by 2 turns (user + model) for each chat() call.");
    }

    // --- Manual HttpResponse implementation (unchanged from original test file) ---

    private static class TestHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        public TestHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public String body() { return body; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return null; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return null; }
        @Override public HttpClient.Version version() { return null; }
    }
}