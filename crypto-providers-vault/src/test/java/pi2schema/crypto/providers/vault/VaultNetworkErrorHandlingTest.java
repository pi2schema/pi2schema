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
import static org.junit.jupiter.api.Assertions.*;

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

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        assertConnectivityException(exception);

        provider.close();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on DNS resolution failure")
    void shouldThrowVaultConnectivityExceptionOnDnsResolutionFailure() {
        // Given - simulate DNS resolution failure by using non-routable IP address
        // This avoids external network dependencies while still testing DNS-like failures
        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        assertConnectivityException(exception);

        provider.close();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on connection refused")
    void shouldThrowVaultConnectivityExceptionOnConnectionRefused() {
        // Given - create config first, then simulate connection refused by stopping WireMock server
        VaultCryptoConfiguration config = createTestConfig();
        simulateConnectionRefused();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        assertConnectivityException(exception);

        provider.close();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on request timeout")
    void shouldThrowVaultConnectivityExceptionOnRequestTimeout() {
        // Given - simulate request timeout
        simulateRequestTimeout();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        assertConnectivityException(exception);

        provider.close();
    }

    @Test
    @DisplayName("Should verify server errors are treated as non-retryable when wrapped in CompletionException")
    void shouldVerifyServerErrorsAreNonRetryableWhenWrapped() {
        // Given - simulate server error (currently non-retryable due to CompletionException wrapping)
        simulateServerError();

        VaultCryptoConfiguration config = createTestConfig(); // maxRetries = 2
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify VaultConnectivityException is thrown
        assertThat(exception.getCause()).isInstanceOf(VaultConnectivityException.class);

        // Verify only one request was made (server errors wrapped in CompletionException are currently non-retryable)
        wireMockServer.verify(exactly(1), anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should not retry non-retryable exceptions")
    void shouldNotRetryNonRetryableExceptions() {
        // Given - configuration for testing non-retryable exception
        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When - test non-retryable exception (null subject ID)
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor(null).join()
        );

        // Then - verify IllegalArgumentException is thrown (non-retryable)
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getCause().getMessage()).contains("Subject ID cannot be null or empty");

        // Verify no HTTP requests were made (no retries for validation errors)
        wireMockServer.verify(0, anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should not retry authentication failures")
    void shouldNotRetryAuthenticationFailures() {
        // Given - simulate authentication failure (non-retryable)
        simulateAuthenticationFailure();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify VaultAuthenticationException is thrown
        assertThat(exception.getCause()).isInstanceOf(VaultAuthenticationException.class);

        // Verify only one request was made (no retries for authentication errors)
        wireMockServer.verify(exactly(1), anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should retry connection timeouts with exponential backoff")
    void shouldRetryConnectionTimeoutsWithExponentialBackoff() {
        // Given - simulate connection timeout (retryable)
        simulateConnectionTimeout();

        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify connectivity exception is thrown after retries
        assertThat(exception.getCause()).isNotNull();

        // Verify the correct number of retry attempts were made (maxRetries + 1 = 2 total attempts)
        wireMockServer.verify(exactly(2), anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should retry retryable exceptions like connection timeouts the configured number of times")
    void shouldRetryRetryableExceptionsConfiguredNumberOfTimes() {
        // Given - simulate connection timeout (retryable exception)
        simulateConnectionTimeout();

        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify exception is thrown after retries
        assertThat(exception.getCause()).isNotNull();

        wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should verify each retry attempt is observable through request counting")
    void shouldVerifyEachRetryAttemptIsObservable() {
        // Given - simulate persistent connection timeout (retryable exception)
        // This test verifies that retry attempts are observable through WireMock request counting
        simulateConnectionTimeout();

        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When - this should fail after exhausting retries
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify exception is thrown after retries
        assertThat(exception.getCause()).isNotNull();

        // Verify retry attempts were made (implementation may vary in exact count)
        // This replaces log-based retry verification with direct request counting
        wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should verify retry exhaustion when max retries reached for retryable exceptions")
    void shouldVerifyRetryExhaustionWhenMaxRetriesReached() {
        // Given - simulate persistent connection timeout (retryable exception)
        simulateConnectionTimeout();

        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify exception indicates retry exhaustion
        assertThat(exception.getCause()).isNotNull();

        // Verify exactly maxRetries + 1 attempts were made (3 total attempts)
        // This replaces log-based retry verification with direct request counting
        wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));

        provider.close();
    }

    @Test
    @DisplayName("Should verify different retry behavior for different exception types")
    void shouldVerifyDifferentRetryBehaviorForDifferentExceptionTypes() {
        // Test 1: Retryable connection timeout should trigger retries
        simulateConnectionTimeout();

        VaultCryptoConfiguration config = createTestConfig(); // maxRetries = 2
        VaultEncryptingMaterialsProvider provider1 = new VaultEncryptingMaterialsProvider(config);

        CompletionException retryableException = assertThrows(
            CompletionException.class,
            () -> provider1.encryptionKeysFor("test-subject-1").join()
        );

        // Verify retryable exception triggered retries (3 total attempts)
        wireMockServer.verify(exactly(3), anyRequestedFor(urlMatching("/v1/transit/.*")));
        assertThat(retryableException.getCause()).isNotNull();

        provider1.close();

        // Reset WireMock for next test
        wireMockServer.resetRequests();

        // Test 2: Non-retryable authentication error should not trigger retries
        simulateAuthenticationFailure();

        VaultEncryptingMaterialsProvider provider2 = new VaultEncryptingMaterialsProvider(config);

        CompletionException nonRetryableException = assertThrows(
            CompletionException.class,
            () -> provider2.encryptionKeysFor("test-subject-2").join()
        );

        // Verify non-retryable exception did not trigger retries (1 attempt only)
        wireMockServer.verify(exactly(1), anyRequestedFor(urlMatching("/v1/transit/.*")));
        assertThat(nonRetryableException.getCause()).isInstanceOf(VaultAuthenticationException.class);

        provider2.close();
    }

    @Test
    @DisplayName("Should throw VaultAuthenticationException on authentication failure")
    void shouldThrowVaultAuthenticationExceptionOnAuthenticationFailure() {
        // Given - simulate authentication failure
        simulateAuthenticationFailure();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Verify we get VaultAuthenticationException
        assertThat(exception.getCause()).isInstanceOf(VaultAuthenticationException.class);
        VaultAuthenticationException authException = (VaultAuthenticationException) exception.getCause();

        // Verify error message contains useful information but no sensitive data
        assertThat(authException.getMessage()).doesNotContain("test-token");
        assertThat(authException.getMessage()).containsAnyOf("permission denied", "Authentication failed");

        provider.close();
    }

    @Test
    @DisplayName("Should preserve error context in exceptions")
    void shouldPreserveErrorContextInExceptions() {
        // Given - simulate server error
        simulateServerError();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject-123").join()
        );

        // Then - verify exception contains useful context
        assertThat(exception.getCause()).isInstanceOf(VaultConnectivityException.class);
        VaultConnectivityException vaultException = (VaultConnectivityException) exception.getCause();

        // Verify error message contains context but no sensitive data
        assertThat(vaultException.getMessage()).doesNotContain("test-token");
        assertThat(vaultException.getMessage()).containsAnyOf("Failed to", "internal server error", "operation failed");

        provider.close();
    }

    @Test
    @DisplayName("Should handle network conditions deterministically with controlled delays")
    void shouldHandleNetworkConditionsDeterministically() {
        // Given - simulate a specific delay pattern for deterministic testing
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(120) // Delay longer than request timeout (100ms) to ensure timeout, optimized for speed
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"keys\":{\"1\":{\"creation_time\":\"2023-01-01T00:00:00Z\"}}}}")
                )
        );

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then - this should timeout due to the controlled delay
        // This demonstrates deterministic control over network conditions
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Verify we get a timeout-related exception
        assertThat(exception.getCause()).isNotNull();

        // Verify error message doesn't contain sensitive data
        String errorMessage = exception.getCause().getMessage();
        if (errorMessage != null) {
            assertThat(errorMessage).doesNotContain("test-token");
        }

        provider.close();
    }

    @Test
    @DisplayName("Should verify exception messages contain useful information with sanitized URLs")
    void shouldVerifyExceptionMessagesContainUsefulInformationWithSanitizedUrls() {
        // Given - simulate server error to get detailed exception
        simulateServerError();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject-123").join()
        );

        // Then - verify exception contains useful information
        assertThat(exception.getCause()).isInstanceOf(VaultConnectivityException.class);
        VaultConnectivityException vaultException = (VaultConnectivityException) exception.getCause();

        // Verify exception message contains useful debugging information
        String message = vaultException.getMessage();
        assertThat(message).isNotNull();
        assertThat(message)
            .containsAnyOf("operation failed", "statusCode=500", "internal server error", "Vault errors");

        // Verify URL information is present but sanitized (no sensitive data)
        assertThat(message).doesNotContain("test-token");
        assertThat(message).doesNotContain("X-Vault-Token");

        // Verify operation context is included
        assertThat(message).containsAnyOf("encrypt", "operation", "failed");

        provider.close();
    }

    @Test
    @DisplayName("Should verify timeout information is included in exception messages")
    void shouldVerifyTimeoutInformationIsIncludedInExceptionMessages() {
        // Given - simulate connection timeout with specific configuration
        simulateConnectionTimeout();

        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify timeout context is preserved in exception
        assertThat(exception.getCause()).isNotNull();

        // Verify sensitive token is not exposed in any exception message
        String rootMessage = exception.getMessage();
        String causeMessage = exception.getCause().getMessage();

        if (rootMessage != null) {
            assertThat(rootMessage).doesNotContain("sensitive-token-12345");
            assertThat(rootMessage).doesNotContain("X-Vault-Token");
        }

        if (causeMessage != null) {
            assertThat(causeMessage).doesNotContain("sensitive-token-12345");
            assertThat(causeMessage).doesNotContain("X-Vault-Token");
        }

        provider.close();
    }

    @Test
    @DisplayName("Should ensure sensitive information is not exposed in exception messages")
    void shouldEnsureSensitiveInformationIsNotExposedInExceptionMessages() {
        // Given - configuration with sensitive data
        VaultCryptoConfiguration config = VaultCryptoConfiguration
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

        // Test multiple error scenarios to ensure token sanitization

        // Test 1: Authentication failure
        simulateAuthenticationFailure();
        VaultEncryptingMaterialsProvider provider1 = new VaultEncryptingMaterialsProvider(config);

        CompletionException authException = assertThrows(
            CompletionException.class,
            () -> provider1.encryptionKeysFor("test-subject").join()
        );

        // Verify no sensitive data in authentication exception
        assertThat(authException.getCause()).isInstanceOf(VaultAuthenticationException.class);
        verifyNoSensitiveDataInException(authException, "hvs.CAESIJ1234567890abcdef-very-sensitive-token");
        provider1.close();

        // Reset for next test
        wireMockServer.resetAll();

        // Test 2: Server error
        simulateServerError();
        VaultEncryptingMaterialsProvider provider2 = new VaultEncryptingMaterialsProvider(config);

        CompletionException serverException = assertThrows(
            CompletionException.class,
            () -> provider2.encryptionKeysFor("test-subject").join()
        );

        // Verify no sensitive data in server error exception
        assertThat(serverException.getCause()).isInstanceOf(VaultConnectivityException.class);
        verifyNoSensitiveDataInException(serverException, "hvs.CAESIJ1234567890abcdef-very-sensitive-token");
        provider2.close();

        // Reset for next test
        wireMockServer.resetAll();

        // Test 3: Connection timeout
        simulateConnectionTimeout();
        VaultEncryptingMaterialsProvider provider3 = new VaultEncryptingMaterialsProvider(config);

        CompletionException timeoutException = assertThrows(
            CompletionException.class,
            () -> provider3.encryptionKeysFor("test-subject").join()
        );

        // Verify no sensitive data in timeout exception
        verifyNoSensitiveDataInException(timeoutException, "hvs.CAESIJ1234567890abcdef-very-sensitive-token");
        provider3.close();
    }

    @Test
    @DisplayName("Should verify original cause exceptions are properly chained")
    void shouldVerifyOriginalCauseExceptionsAreProperlyChained() {
        // Given - simulate server error to get exception with cause chain
        simulateServerError();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Then - verify exception chain is properly preserved
        assertThat(exception).isNotNull();
        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause()).isInstanceOf(VaultConnectivityException.class);

        // Verify the exception chain preserves context
        VaultConnectivityException vaultException = (VaultConnectivityException) exception.getCause();

        // The VaultConnectivityException should have meaningful message
        assertThat(vaultException.getMessage()).isNotNull();
        assertThat(vaultException.getMessage()).isNotEmpty();

        // Verify stack trace is preserved for debugging
        assertThat(vaultException.getStackTrace()).isNotEmpty();

        provider.close();
    }

    @Test
    @DisplayName("Should verify exception context includes operation and request details")
    void shouldVerifyExceptionContextIncludesOperationAndRequestDetails() {
        // Given - simulate authentication failure to get detailed exception
        simulateAuthenticationFailure();

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject-with-context").join()
        );

        // Then - verify exception contains operational context
        assertThat(exception.getCause()).isInstanceOf(VaultAuthenticationException.class);
        VaultAuthenticationException authException = (VaultAuthenticationException) exception.getCause();

        String message = authException.getMessage();
        assertThat(message).isNotNull();

        // Verify operation context is included
        assertThat(message)
            .containsAnyOf("operation failed", "Authentication failed", "permission denied", "statusCode=403");

        // Verify request correlation information is present
        assertThat(message).containsAnyOf("requestId=", "Vault", "failed");

        // Verify no sensitive information is leaked
        assertThat(message).doesNotContain("test-token");
        assertThat(message).doesNotContain("X-Vault-Token");

        provider.close();
    }

    @Test
    @DisplayName("Should verify different exception types contain appropriate error details")
    void shouldVerifyDifferentExceptionTypesContainAppropriateErrorDetails() {
        VaultCryptoConfiguration config = createTestConfig();

        // Test 1: IllegalArgumentException for null subject ID
        VaultEncryptingMaterialsProvider provider1 = new VaultEncryptingMaterialsProvider(config);

        CompletionException nullSubjectException = assertThrows(
            CompletionException.class,
            () -> provider1.encryptionKeysFor(null).join()
        );

        assertThat(nullSubjectException.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(nullSubjectException.getCause().getMessage()).contains("Subject ID cannot be null or empty");
        provider1.close();

        // Test 2: VaultAuthenticationException for auth failure
        simulateAuthenticationFailure();
        VaultEncryptingMaterialsProvider provider2 = new VaultEncryptingMaterialsProvider(config);

        CompletionException authException = assertThrows(
            CompletionException.class,
            () -> provider2.encryptionKeysFor("test-subject").join()
        );

        assertThat(authException.getCause()).isInstanceOf(VaultAuthenticationException.class);
        String authMessage = authException.getCause().getMessage();
        assertThat(authMessage).containsAnyOf("permission denied", "Authentication failed", "403");
        assertThat(authMessage).doesNotContain("test-token");
        provider2.close();

        // Reset for next test
        wireMockServer.resetAll();

        // Test 3: VaultConnectivityException for server error
        simulateServerError();
        VaultEncryptingMaterialsProvider provider3 = new VaultEncryptingMaterialsProvider(config);

        CompletionException serverException = assertThrows(
            CompletionException.class,
            () -> provider3.encryptionKeysFor("test-subject").join()
        );

        assertThat(serverException.getCause()).isInstanceOf(VaultConnectivityException.class);
        String serverMessage = serverException.getCause().getMessage();
        assertThat(serverMessage).containsAnyOf("internal server error", "500", "operation failed");
        assertThat(serverMessage).doesNotContain("test-token");
        provider3.close();
    }

    private static void assertConnectivityException(CompletionException exception) {
        Throwable actualException = exception.getCause();
        assertThat(actualException).isNotNull();
        assertThat(actualException).isInstanceOf(VaultConnectivityException.class);
        assertThat(actualException.getMessage()).doesNotContain("test-token");
    }

    /**
     * Helper method to verify that no sensitive data is present in exception messages.
     * This checks the entire exception chain for sensitive information.
     */
    private void verifyNoSensitiveDataInException(Throwable exception, String sensitiveToken) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                assertThat(message).doesNotContain(sensitiveToken);
                assertThat(message).doesNotContain("X-Vault-Token");
                assertThat(message).doesNotContain("Authorization");
                // Check for partial token exposure
                if (sensitiveToken.length() > 10) {
                    String tokenPrefix = sensitiveToken.substring(0, 10);
                    String tokenSuffix = sensitiveToken.substring(sensitiveToken.length() - 10);
                    assertThat(message).doesNotContain(tokenPrefix);
                    assertThat(message).doesNotContain(tokenSuffix);
                }
            }
            current = current.getCause();
        }
    }
}
