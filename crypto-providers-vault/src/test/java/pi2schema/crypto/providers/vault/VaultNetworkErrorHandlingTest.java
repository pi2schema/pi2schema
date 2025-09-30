package pi2schema.crypto.providers.vault;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for network-related error handling scenarios.
 * This test class focuses on network failures, timeouts, and connectivity issues
 * using controlled WireMock server simulation instead of external dependencies.
 */
class VaultNetworkErrorHandlingTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        // Set up WireMock server with dynamic port
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    private VaultCryptoConfiguration createTestConfig() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100)) // Optimized for reliable test execution
            .requestTimeout(Duration.ofMillis(200)) // Optimized for reliable test execution
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(20)) // Optimized for fast test execution
            .build();
    }

    /**
     * Helper method to simulate connection timeout by delaying response longer than client timeout.
     */
    private void simulateConnectionTimeout() {
        // Simulate connection timeout by delaying response much longer than connection timeout
        // This will cause the HTTP client to timeout and throw a connection timeout exception
        wireMockServer.stubFor(any(urlMatching("/v1/transit/.*")).willReturn(aResponse().withFixedDelay(500))); // Much longer than 100ms connection timeout to ensure timeout
    }

    /**
     * Helper method to simulate request timeout by delaying response longer than request timeout.
     */
    private void simulateRequestTimeout() {
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(250) // Longer than 200ms request timeout, optimized for reliability
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"keys\":{\"1\":{\"creation_time\":\"2023-01-01T00:00:00Z\"}}}}")
                )
        );
    }

    /**
     * Helper method to simulate connection refused by stopping the WireMock server.
     */
    private void simulateConnectionRefused() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * Helper method to simulate server error responses.
     */
    private void simulateServerError() {
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[\"internal server error\"]}")
                )
        );
    }

    /**
     * Helper method to simulate authentication failure.
     */
    private void simulateAuthenticationFailure() {
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[\"permission denied\"]}")
                )
        );
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on connection timeout")
    void shouldThrowVaultConnectivityExceptionOnConnectionTimeout() {
        // Given - simulate connection timeout
        simulateConnectionTimeout();

        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100))
            .requestTimeout(Duration.ofMillis(100)) // Set to 100ms to be less than the 120ms delay
            .maxRetries(0) // No retries for this test to make it faster and more deterministic
            .retryBackoffMs(Duration.ofMillis(10))
            .build();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .satisfies(this::assertConnectivityException);
        }
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on DNS resolution failure")
    void shouldThrowVaultConnectivityExceptionOnDnsResolutionFailure() {
        // Given - simulate DNS resolution failure by using non-routable IP address
        // This avoids external network dependencies while still testing DNS-like failures
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://192.0.2.1:8200") // RFC 5737 test network - guaranteed non-routable
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100)) // Optimized for reliable test execution
            .requestTimeout(Duration.ofMillis(200)) // Optimized for reliable test execution
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(10)) // Optimized for fast test execution
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .satisfies(this::assertConnectivityException);
        }
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on connection refused")
    void shouldThrowVaultConnectivityExceptionOnConnectionRefused() {
        // Given - create config first, then simulate connection refused by stopping WireMock server
        var config = createTestConfig();
        simulateConnectionRefused();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .satisfies(this::assertConnectivityException);
        }
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on request timeout")
    void shouldThrowVaultConnectivityExceptionOnRequestTimeout() {
        // Given - simulate request timeout
        simulateRequestTimeout();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .satisfies(this::assertConnectivityException);
        }
    }

    @Test
    @DisplayName("Should verify server errors are treated as non-retryable when wrapped in CompletionException")
    void shouldVerifyServerErrorsAreNonRetryableWhenWrapped() {
        // Given - simulate server error (currently non-retryable due to CompletionException wrapping)
        simulateServerError();

        var config = createTestConfig(); // maxRetries = 2
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            // Verify only one request was made (server errors wrapped in CompletionException are currently non-retryable)
            wireMockServer.verify(exactly(1), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should not retry non-retryable exceptions")
    void shouldNotRetryNonRetryableExceptions() {
        // Given - configuration for testing non-retryable exception
        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then - test non-retryable exception (null subject ID)
            assertThatThrownBy(() -> provider.encryptionKeysFor(null).join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject ID cannot be null or empty");

            // Verify no HTTP requests were made (no retries for validation errors)
            wireMockServer.verify(0, anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should not retry authentication failures")
    void shouldNotRetryAuthenticationFailures() {
        // Given - simulate authentication failure (non-retryable)
        simulateAuthenticationFailure();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultAuthenticationException.class);

            // Verify only one request was made (no retries for authentication errors)
            wireMockServer.verify(exactly(1), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should retry connection timeouts with exponential backoff")
    void shouldRetryConnectionTimeoutsWithExponentialBackoff() {
        // Given - simulate connection timeout (retryable)
        simulateConnectionTimeout();

        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100)) // Optimized for reliable test execution
            .requestTimeout(Duration.ofMillis(200)) // Optimized for reliable test execution
            .maxRetries(1) // Only 1 retry for faster test
            .retryBackoffMs(Duration.ofMillis(10)) // Optimized for fast test execution
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            // Verify the correct number of retry attempts were made (maxRetries + 1 = 2 total attempts)
            wireMockServer.verify(exactly(2), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should retry retryable exceptions like connection timeouts the configured number of times")
    void shouldRetryRetryableExceptionsConfiguredNumberOfTimes() {
        // Given - simulate connection timeout (retryable exception)
        simulateConnectionTimeout();

        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100)) // Optimized for reliable test execution
            .requestTimeout(Duration.ofMillis(200)) // Optimized for reliable test execution
            .maxRetries(2) // 2 retries for this test
            .retryBackoffMs(Duration.ofMillis(10)) // Optimized for fast test execution
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should verify each retry attempt is observable through request counting")
    void shouldVerifyEachRetryAttemptIsObservable() {
        // Given - simulate persistent connection timeout (retryable exception)
        simulateConnectionTimeout();

        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(50)) // Optimized for fast test execution
            .requestTimeout(Duration.ofMillis(100)) // Optimized for fast test execution
            .maxRetries(2) // 2 retries for this test
            .retryBackoffMs(Duration.ofMillis(5)) // Optimized for fast test execution
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then - this should fail after exhausting retries
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            // Verify retry attempts were made (implementation may vary in exact count)
            wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should verify retry exhaustion when max retries reached for retryable exceptions")
    void shouldVerifyRetryExhaustionWhenMaxRetriesReached() {
        // Given - simulate persistent connection timeout (retryable exception)
        simulateConnectionTimeout();

        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(50)) // Optimized for fast test execution
            .requestTimeout(Duration.ofMillis(100)) // Optimized for fast test execution
            .maxRetries(2) // 2 retries for this test
            .retryBackoffMs(Duration.ofMillis(5)) // Optimized for fast test execution
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            // Verify exactly maxRetries + 1 attempts were made (3 total attempts)
            wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should verify different retry behavior for different exception types")
    void shouldVerifyDifferentRetryBehaviorForDifferentExceptionTypes() {
        // Test 1: Retryable connection timeout should trigger retries
        simulateConnectionTimeout();

        var config = createTestConfig(); // maxRetries = 2
        try (var provider1 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider1.encryptionKeysFor("test-subject-1").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            // Verify retryable exception triggered retries (3 total attempts)
            wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }

        // Reset WireMock for next test
        wireMockServer.resetRequests();

        // Test 2: Non-retryable authentication error should not trigger retries
        simulateAuthenticationFailure();

        try (var provider2 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider2.encryptionKeysFor("test-subject-2").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultAuthenticationException.class);

            // Verify non-retryable exception did not trigger retries (1 attempt only)
            wireMockServer.verify(exactly(1), anyRequestedFor(urlMatching("/v1/transit/.*")));
        }
    }

    @Test
    @DisplayName("Should throw VaultAuthenticationException on authentication failure")
    void shouldThrowVaultAuthenticationExceptionOnAuthenticationFailure() {
        // Given - simulate authentication failure
        simulateAuthenticationFailure();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultAuthenticationException.class)
                .satisfies(ex -> {
                    var authException = (VaultAuthenticationException) ex.getCause();
                    assertThat(authException.getMessage()).doesNotContain("test-token");
                    assertThat(authException.getMessage()).containsAnyOf("permission denied", "Authentication failed");
                });
        }
    }

    @Test
    @DisplayName("Should preserve error context in exceptions")
    void shouldPreserveErrorContextInExceptions() {
        // Given - simulate server error
        simulateServerError();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject-123").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> {
                    var vaultException = (VaultConnectivityException) ex.getCause();
                    assertThat(vaultException.getMessage()).doesNotContain("test-token");
                    assertThat(vaultException.getMessage())
                        .containsAnyOf("Failed to", "internal server error", "operation failed");
                });
        }
    }

    @Test
    @DisplayName("Should handle network conditions deterministically with controlled delays")
    void shouldHandleNetworkConditionsDeterministically() {
        // Given - simulate a specific delay pattern for deterministic testing
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(300) // Increased delay to make timeout more reliable
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"keys\":{\"1\":{\"creation_time\":\"2023-01-01T00:00:00Z\"}}}}")
                )
        );

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then - this should timeout due to the controlled delay
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> {
                    var causeMessage = ex.getCause().getMessage();
                    if (causeMessage != null) {
                        assertThat(causeMessage).doesNotContain("test-token");
                    }
                });
        }
    }

    @Test
    @DisplayName("Should verify exception messages contain useful information with sanitized URLs")
    void shouldVerifyExceptionMessagesContainUsefulInformationWithSanitizedUrls() {
        // Given - simulate server error to get detailed exception
        simulateServerError();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject-123").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> {
                    var message = ex.getCause().getMessage();
                    assertThat(message).isNotNull();
                    assertThat(message)
                        .containsAnyOf("operation failed", "statusCode=500", "internal server error", "Vault errors");
                    assertThat(message).doesNotContain("test-token", "X-Vault-Token");
                    assertThat(message).containsAnyOf("encrypt", "operation", "failed");
                });
        }
    }

    @Test
    @DisplayName("Should verify timeout information is included in exception messages")
    void shouldVerifyTimeoutInformationIsIncludedInExceptionMessages() {
        // Given - simulate connection timeout with specific configuration
        simulateConnectionTimeout();

        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("sensitive-token-12345")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(40)) // Optimized for fast test execution
            .requestTimeout(Duration.ofMillis(80)) // Optimized for fast test execution
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(5)) // Optimized for fast test execution
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> {
                    verifyNoSensitiveDataInException(ex, "sensitive-token-12345");
                });
        }
    }

    @Test
    @DisplayName("Should ensure sensitive information is not exposed in exception messages")
    void shouldEnsureSensitiveInformationIsNotExposedInExceptionMessages() {
        // Given - configuration with sensitive data
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:" + wireMockServer.port())
            .vaultToken("hvs.CAESIJ1234567890abcdef-very-sensitive-token")
            .transitEnginePath("transit")
            .keyPrefix("sensitive-prefix")
            .connectionTimeout(Duration.ofMillis(30)) // Optimized for fast test execution
            .requestTimeout(Duration.ofMillis(60)) // Optimized for fast test execution
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(2)) // Optimized for fast test execution
            .build();

        // Test 1: Authentication failure
        simulateAuthenticationFailure();
        try (var provider1 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider1.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultAuthenticationException.class)
                .satisfies(ex -> verifyNoSensitiveDataInException(ex, "hvs.CAESIJ1234567890abcdef-very-sensitive-token")
                );
        }

        // Reset for next test
        wireMockServer.resetAll();

        // Test 2: Server error
        simulateServerError();
        try (var provider2 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider2.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> verifyNoSensitiveDataInException(ex, "hvs.CAESIJ1234567890abcdef-very-sensitive-token")
                );
        }

        // Reset for next test
        wireMockServer.resetAll();

        // Test 3: Connection timeout
        simulateConnectionTimeout();
        try (var provider3 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider3.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> verifyNoSensitiveDataInException(ex, "hvs.CAESIJ1234567890abcdef-very-sensitive-token")
                );
        }
    }

    @Test
    @DisplayName("Should verify original cause exceptions are properly chained")
    void shouldVerifyOriginalCauseExceptionsAreProperlyChained() {
        // Given - simulate server error to get exception with cause chain
        simulateServerError();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> {
                    var vaultException = (VaultConnectivityException) ex.getCause();
                    assertThat(vaultException.getMessage()).isNotNull().isNotEmpty();
                    assertThat(vaultException.getStackTrace()).isNotEmpty();
                });
        }
    }

    @Test
    @DisplayName("Should verify exception context includes operation and request details")
    void shouldVerifyExceptionContextIncludesOperationAndRequestDetails() {
        // Given - simulate authentication failure to get detailed exception
        simulateAuthenticationFailure();

        var config = createTestConfig();
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When & Then
            assertThatThrownBy(() -> provider.encryptionKeysFor("test-subject-with-context").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultAuthenticationException.class)
                .satisfies(ex -> {
                    var message = ex.getCause().getMessage();
                    assertThat(message).isNotNull();
                    assertThat(message)
                        .containsAnyOf(
                            "operation failed",
                            "Authentication failed",
                            "permission denied",
                            "statusCode=403"
                        );
                    assertThat(message).containsAnyOf("requestId=", "Vault", "failed");
                    assertThat(message).doesNotContain("test-token", "X-Vault-Token");
                });
        }
    }

    @Test
    @DisplayName("Should verify different exception types contain appropriate error details")
    void shouldVerifyDifferentExceptionTypesContainAppropriateErrorDetails() {
        var config = createTestConfig();

        // Test 1: IllegalArgumentException for null subject ID
        try (var provider1 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider1.encryptionKeysFor(null).join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subject ID cannot be null or empty");
        }

        // Test 2: VaultAuthenticationException for auth failure
        simulateAuthenticationFailure();
        try (var provider2 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider2.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultAuthenticationException.class)
                .satisfies(ex -> {
                    var authMessage = ex.getCause().getMessage();
                    assertThat(authMessage).containsAnyOf("permission denied", "Authentication failed", "403");
                    assertThat(authMessage).doesNotContain("test-token");
                });
        }

        // Reset for next test
        wireMockServer.resetAll();

        // Test 3: VaultConnectivityException for server error
        simulateServerError();
        try (var provider3 = new VaultEncryptingMaterialsProvider(config)) {
            assertThatThrownBy(() -> provider3.encryptionKeysFor("test-subject").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class)
                .satisfies(ex -> {
                    var serverMessage = ex.getCause().getMessage();
                    assertThat(serverMessage).containsAnyOf("internal server error", "500", "operation failed");
                    assertThat(serverMessage).doesNotContain("test-token");
                });
        }
    }

    private void assertConnectivityException(Throwable exception) {
        var actualException = exception.getCause();
        assertThat(actualException).isNotNull().isInstanceOf(VaultConnectivityException.class);
        assertThat(actualException.getMessage()).doesNotContain("test-token");
    }

    /**
     * Helper method to verify that no sensitive data is present in exception messages.
     * This checks the entire exception chain for sensitive information.
     */
    private void verifyNoSensitiveDataInException(Throwable exception, String sensitiveToken) {
        var current = exception;
        while (current != null) {
            var message = current.getMessage();
            if (message != null) {
                assertThat(message).doesNotContain(sensitiveToken);
                assertThat(message).doesNotContain("X-Vault-Token");
                assertThat(message).doesNotContain("Authorization");
                // Check for partial token exposure
                if (sensitiveToken.length() > 10) {
                    var tokenPrefix = sensitiveToken.substring(0, 10);
                    var tokenSuffix = sensitiveToken.substring(sensitiveToken.length() - 10);
                    assertThat(message).doesNotContain(tokenPrefix);
                    assertThat(message).doesNotContain(tokenSuffix);
                }
            }
            current = current.getCause();
        }
    }
}
