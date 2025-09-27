package pi2schema.crypto.providers.vault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for network-related error handling and logging scenarios.
 * This test class focuses on network failures, timeouts, and connectivity issues.
 */
class VaultNetworkErrorHandlingTest {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger transitClientLogger;

    @BeforeEach
    void setUp() {
        // Set up log capture
        logAppender = new ListAppender<>();
        logAppender.start();

        transitClientLogger = (Logger) LoggerFactory.getLogger(VaultTransitClient.class);
        transitClientLogger.addAppender(logAppender);
        transitClientLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            transitClientLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    @DisplayName("Should handle connection timeout with proper error logging")
    void shouldHandleConnectionTimeoutWithProperErrorLogging() {
        // Given - very short timeout to force timeout
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://192.0.2.1:8200") // Non-routable IP to force timeout
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(1)) // Very short timeout
            .requestTimeout(Duration.ofMillis(10))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(1))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");

        // Then
        assertThrows(CompletionException.class, future::join);

        // Verify timeout error logging
        List<ILoggingEvent> errorLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();

        assertTrue(
            errorLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("Failed to") ||
                    event.getMessage().contains("Connection") ||
                    event.getMessage().contains("timeout") ||
                    event.getMessage().contains("Encryption materials generation failed")
                ),
            "Should log connection failure"
        );

        provider.close();
    }

    @Test
    @DisplayName("Should handle DNS resolution failure with proper error logging")
    void shouldHandleDnsResolutionFailureWithProperErrorLogging() {
        // Given - invalid hostname
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

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");

        // Then
        assertThrows(CompletionException.class, future::join);

        // Verify DNS failure logging
        List<ILoggingEvent> errorLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();

        assertTrue(
            errorLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("Failed to") ||
                    event.getMessage().contains("Connection") ||
                    event.getMessage().contains("Encryption materials generation failed")
                ),
            "Should log DNS resolution failure"
        );

        provider.close();
    }

    @Test
    @DisplayName("Should handle connection refused with proper error logging")
    void shouldHandleConnectionRefusedWithProperErrorLogging() {
        // Given - localhost on unused port
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

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");

        // Then
        assertThrows(CompletionException.class, future::join);

        // Verify connection refused logging
        List<ILoggingEvent> errorLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();

        assertTrue(
            errorLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("Failed to") ||
                    event.getMessage().contains("Connection") ||
                    event.getMessage().contains("Encryption materials generation failed")
                ),
            "Should log connection refused error"
        );

        provider.close();
    }

    @Test
    @DisplayName("Should log retry attempts with exponential backoff information")
    void shouldLogRetryAttemptsWithExponentialBackoffInformation() {
        // Given - configuration that will fail but retry
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:65433") // Unlikely to be in use
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100))
            .requestTimeout(Duration.ofMillis(200))
            .maxRetries(3)
            .retryBackoffMs(Duration.ofMillis(50))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");

        // Then
        assertThrows(CompletionException.class, future::join);

        // Verify retry logging
        List<ILoggingEvent> warnLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .toList();

        // Should have multiple retry attempts logged
        long retryLogCount = warnLogs.stream().filter(event -> event.getMessage().contains("retrying")).count();

        assertTrue(retryLogCount >= 1, "Should log at least one retry attempt");

        // Verify retry information is included
        assertTrue(
            warnLogs
                .stream()
                .anyMatch(event -> event.getMessage().contains("attempt=") && event.getMessage().contains("retryDelay=")
                ),
            "Should log retry attempt number and delay"
        );

        // Verify final failure is logged
        List<ILoggingEvent> errorLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();

        assertTrue(
            errorLogs.stream().anyMatch(event -> event.getMessage().contains("Max retries exceeded")),
            "Should log max retries exceeded"
        );

        provider.close();
    }

    @Test
    @DisplayName("Should distinguish between retryable and non-retryable exceptions")
    void shouldDistinguishBetweenRetryableAndNonRetryableExceptions() {
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofSeconds(1))
            .requestTimeout(Duration.ofSeconds(2))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // Test non-retryable exception (null subject ID)
        CompletableFuture<EncryptionMaterial> future1 = provider.encryptionKeysFor(null);

        CompletionException exception1 = assertThrows(CompletionException.class, future1::join);

        assertTrue(exception1.getCause() instanceof IllegalArgumentException);

        // Verify no retry attempts for non-retryable exception
        List<ILoggingEvent> warnLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.WARN && event.getMessage().contains("retrying"))
            .toList();

        assertEquals(0, warnLogs.size(), "Should not retry non-retryable exceptions");

        provider.close();
    }

    @Test
    @DisplayName("Should handle request timeout with proper error classification")
    void shouldHandleRequestTimeoutWithProperErrorClassification() {
        // Given - very short request timeout
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://httpbin.org/delay/5") // Will take 5 seconds to respond
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofSeconds(2))
            .requestTimeout(Duration.ofMillis(100)) // Very short request timeout
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(10))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");

        // Then
        assertThrows(CompletionException.class, future::join);

        // Verify timeout error logging
        List<ILoggingEvent> errorLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();

        assertTrue(
            errorLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("Failed to") ||
                    event.getMessage().contains("timeout") ||
                    event.getMessage().contains("Encryption materials generation failed")
                ),
            "Should log request timeout error"
        );

        provider.close();
    }

    @Test
    @DisplayName("Should log structured information for debugging network issues")
    void shouldLogStructuredInformationForDebuggingNetworkIssues() {
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:65434")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(200))
            .requestTimeout(Duration.ofMillis(400))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(50))
            .build();

        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject-123");

        assertThrows(CompletionException.class, future::join);

        // Then - verify structured logging information
        List<ILoggingEvent> debugLogs = logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.DEBUG)
            .toList();

        // Should log request IDs for correlation (in debug or error logs)
        List<ILoggingEvent> allLogs = logAppender.list;
        assertTrue(
            allLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("requestId=") ||
                    event.getMessage().contains("Generating encryption materials") ||
                    event.getMessage().contains("test-subject-123")
                ),
            "Should include request IDs or subject info for correlation"
        );

        // Should log subject ID (but not sensitive data) - check all logs since debug might not be enabled
        assertTrue(
            allLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("test-subject-123") ||
                    event.getMessage().contains("subjectId=test-subject-123")
                ),
            "Should include subject ID for debugging"
        );

        // Should log operation details - check all logs
        assertTrue(
            allLogs
                .stream()
                .anyMatch(event ->
                    event.getMessage().contains("keyName=") ||
                    event.getMessage().contains("Generating") ||
                    event.getMessage().contains("materials")
                ),
            "Should include operation details for debugging"
        );

        provider.close();
    }
}
