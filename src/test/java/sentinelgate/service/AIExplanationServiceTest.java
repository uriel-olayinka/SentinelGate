package sentinelgate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.FraudFlag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

class AIExplanationServiceTest {

    private AIExplanationService aiService;
    private FraudFlag testFlag;

    // Variables to control what our fake HTTP client does
    private int mockStatusCode;
    private String mockBody;
    private boolean forceNetworkFailure;

    @BeforeEach
    void setUp() {
        testFlag = new FraudFlag("Test Rule", "HIGH", "Raw test context");
        mockStatusCode = 200;
        mockBody = "";
        forceNetworkFailure = false;

        // We create an anonymous subclass to intercept the HTTP call. No Mockito needed!
        aiService = new AIExplanationService() {
            @Override
            protected HttpResponse<String> sendHttpRequest(HttpRequest request) throws IOException {
                if (forceNetworkFailure) {
                    throw new IOException("Connection timed out");
                }
                return new TestHttpResponse(mockStatusCode, mockBody);
            }
        };
    }

    @Test
    void testSuccessfulApiResponse_ReturnsCleanExplanation() {
        // Arrange
        mockStatusCode = 200;
        mockBody = "{\"contents\":[{\"parts\":[{\"text\":\"The transaction was unusually large.\"}]}]}";

        // Act
        String result = aiService.getExplanation(testFlag);

        // Assert
        assertEquals("The transaction was unusually large.", result,
                "Service should extract and return the exact text from the JSON.");
    }

    @Test
    void testApiErrorResponse_TriggersFallback() {
        // Arrange
        mockStatusCode = 500;

        // Act
        String result = aiService.getExplanation(testFlag);

        // Assert
        assertTrue(result.contains("Transaction flagged by system rule"),
                "Service should fall back to template when API returns an error code.");
        assertTrue(result.contains("Test Rule"), "Fallback should include the rule name.");
    }

    @Test
    void testNetworkFailure_TriggersFallbackWithoutCrashing() {
        // Arrange
        forceNetworkFailure = true;

        // Act
        String result = aiService.getExplanation(testFlag);

        // Assert
        assertNotNull(result, "Service should safely catch the exception and not return null.");
        assertTrue(result.contains("Transaction flagged by system rule"),
                "Service should fall back to template when network fails.");
    }

    // --- Our Bulletproof Manual Mock for HttpResponse ---
    // This safely implements the locked-down JDK interface without any reflection hacks.
    private static class TestHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        public TestHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public String body() { return body; }

        // We do not use these methods in our AI service, so returning null/empty is perfectly safe
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return null; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return null; }
        @Override public HttpClient.Version version() { return null; }
    }
}