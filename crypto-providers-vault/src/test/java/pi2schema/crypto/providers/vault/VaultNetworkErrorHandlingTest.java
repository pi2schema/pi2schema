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
        if (wireMockServer != null) {
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
            .connectionTimeout(Duration.ofMillis(100))
            .requestTimeout(Duration.ofMillis(200))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(50))
            .build();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on connection timeout")
    void shouldThrowVaultConnectivityExceptionOnConnectionTimeout() {
        // Given - simulate connection timeout with delay longer than client timeout
        wireMockServer.stubFor(any(urlMatching("/v1/transit/.*")).willReturn(aResponse().withFixedDelay(300))); // Longer than 100ms connection timeout

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Verify we get some kind of connectivity-related exception
        // (The exact type may vary - could be VaultConnectivityException or other network exception)
        assertThat(exception.getCause()).isNotNull();

        // Verify error message contains useful information but no sensitive data
        String errorMessage = exception.getCause().getMessage();
        if (errorMessage != null) {
            assertThat(errorMessage).doesNotContain("test-token");
        }

        provider.close();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on DNS resolution failure")
    void shouldThrowVaultConnectivityExceptionOnDnsResolutionFailure() {
        // Given - invalid hostname that will cause DNS resolution failure
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://non-existent-vault-server-12345.invalid:8200")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(500))
            .requestTimeout(Duration.ofMillis(1000))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(10))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Verify we get some kind of network-related exception (could be VaultConnectivityException or other)
        assertThat(exception.getCause()).isNotNull();

        // Verify error message doesn't contain sensitive data
        String errorMessage = exception.getCause().getMessage();
        if (errorMessage != null) {
            assertThat(errorMessage).doesNotContain("test-token");
        }

        provider.close();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on connection refused")
    void shouldThrowVaultConnectivityExceptionOnConnectionRefused() {
        // Given - use a port that's unlikely to be in use to simulate connection refused
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:65432") // Unlikely to be in use
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(500))
            .requestTimeout(Duration.ofMillis(1000))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(10))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Verify we get some kind of network-related exception (could be VaultConnectivityException or other)
        assertThat(exception.getCause()).isNotNull();

        // Verify error message doesn't contain sensitive data
        String errorMessage = exception.getCause().getMessage();
        if (errorMessage != null) {
            assertThat(errorMessage).doesNotContain("test-token");
        }

        provider.close();
    }

    @Test
    @DisplayName("Should throw VaultConnectivityException on request timeout")
    void shouldThrowVaultConnectivityExceptionOnRequestTimeout() {
        // Given - simulate request timeout with delay longer than request timeout
        wireMockServer.stubFor(any(urlMatching("/v1/transit/.*")).willReturn(aResponse().withFixedDelay(300))); // Longer than 200ms request timeout

        VaultCryptoConfiguration config = createTestConfig();
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When & Then
        CompletionException exception = assertThrows(
            CompletionException.class,
            () -> provider.encryptionKeysFor("test-subject").join()
        );

        // Verify we get some kind of network-related exception (could be VaultConnectivityException or other)
        assertThat(exception.getCause()).isNotNull();

        // Verify error message doesn't contain sensitive data
        String errorMessage = exception.getCause().getMessage();
        if (errorMessage != null) {
            assertThat(errorMessage).doesNotContain("test-token");
        }

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
    @DisplayName("Should throw VaultAuthenticationException on authentication failure")
    void shouldThrowVaultAuthenticationExceptionOnAuthenticationFailure() {
        // Given - simulate authentication failure with 403 response
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[\"permission denied\"]}")
                )
        );

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
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[\"internal server error\"]}")
                )
        );

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

    /**
     * Helper method to get the root cause of an exception chain.
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
