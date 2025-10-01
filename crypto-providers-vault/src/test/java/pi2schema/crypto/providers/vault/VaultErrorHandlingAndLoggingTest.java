package pi2schema.crypto.providers.vault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for error handling and logging in Vault crypto providers.
 * This test class verifies that:
 * 1. All error scenarios are properly logged
 * 2. No sensitive information appears in logs
 * 3. Exception chaining is correct
 * 4. Error messages are meaningful and actionable
 */
class VaultErrorHandlingAndLoggingTest {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger transitClientLogger;
    private Logger encryptingProviderLogger;
    private Logger decryptingProviderLogger;
    private WireMockServer wireMockServer;

    private VaultCryptoConfiguration validConfig;
    private VaultCryptoConfiguration invalidConfig;

    @BeforeEach
    void setUp() {
        // Set up WireMock server
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        // Set up log capture
        logAppender = new ListAppender<>();
        logAppender.start();

        // Attach to all relevant loggers
        transitClientLogger = (Logger) LoggerFactory.getLogger(VaultTransitClient.class);
        encryptingProviderLogger = (Logger) LoggerFactory.getLogger(VaultEncryptingMaterialsProvider.class);
        decryptingProviderLogger = (Logger) LoggerFactory.getLogger(VaultDecryptingMaterialsProvider.class);

        transitClientLogger.addAppender(logAppender);
        encryptingProviderLogger.addAppender(logAppender);
        decryptingProviderLogger.addAppender(logAppender);

        transitClientLogger.setLevel(Level.DEBUG);
        encryptingProviderLogger.setLevel(Level.DEBUG);
        decryptingProviderLogger.setLevel(Level.DEBUG);

        // Create valid configuration with WireMock server and optimized timeouts
        validConfig =
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("http://localhost:" + wireMockServer.port())
                .vaultToken("test-token-12345")
                .transitEnginePath("transit")
                .keyPrefix("test-prefix")
                .connectionTimeout(Duration.ofMillis(80)) // Short timeouts for fast deterministic failure
                .requestTimeout(Duration.ofMillis(120))
                .maxRetries(2)
                .retryBackoffMs(Duration.ofMillis(10)) // Optimized for fast test execution
                .build();

        // Create invalid configuration for testing with WireMock server and optimized timeouts
        invalidConfig =
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("http://localhost:" + wireMockServer.port())
                .vaultToken("invalid-token")
                .transitEnginePath("transit")
                .keyPrefix("test-prefix")
                .connectionTimeout(Duration.ofMillis(80)) // Short timeouts for fast deterministic failure
                .requestTimeout(Duration.ofMillis(120))
                .maxRetries(1)
                .retryBackoffMs(Duration.ofMillis(10)) // Optimized for fast test execution
                .build();
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            transitClientLogger.detachAppender(logAppender);
            encryptingProviderLogger.detachAppender(logAppender);
            decryptingProviderLogger.detachAppender(logAppender);
            logAppender.stop();
        }

        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should log initialization without exposing sensitive data")
    void shouldLogInitializationWithoutSensitiveData() {
        // When
        try (var provider = new VaultEncryptingMaterialsProvider(validConfig)) {
            // Then
            var logEvents = logAppender.list;

            // Verify initialization is logged
            assertThat(logEvents)
                .anyMatch(event ->
                    event.getLevel() == Level.INFO &&
                    event.getMessage().contains("VaultEncryptingMaterialsProvider initialized")
                );
            assertThat(logEvents)
                .anyMatch(event ->
                    event.getLevel() == Level.INFO && event.getMessage().contains("VaultTransitClient initialized")
                );

            // Verify no sensitive data in logs
            logEvents.forEach(event -> {
                var message = event.getFormattedMessage();
                assertThat(message).doesNotContain("test-token-12345", "password");
            });
        }
    }

    @Test
    @DisplayName("Should handle null subject ID with proper error logging")
    void shouldHandleNullSubjectIdWithProperErrorLogging() {
        try (var provider = new VaultEncryptingMaterialsProvider(validConfig)) {
            // When
            var future = provider.encryptionKeysFor(null);

            // Then
            assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subject ID cannot be null or empty");

            // Verify error logging
            var errorLogs = logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();

            assertThat(errorLogs)
                .anyMatch(event -> event.getMessage().contains("Encryption materials generation failed"));
        }
    }

    @Test
    @DisplayName("Should handle empty subject ID with proper error logging")
    void shouldHandleEmptySubjectIdWithProperErrorLogging() {
        try (var provider = new VaultEncryptingMaterialsProvider(validConfig)) {
            // When
            var future = provider.encryptionKeysFor("   ");

            // Then
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Should handle connectivity failures with proper error handling")
    void shouldHandleConnectivityFailuresWithProperErrorHandling() {
        try (var provider = new VaultEncryptingMaterialsProvider(invalidConfig)) {
            // When
            var future = provider.encryptionKeysFor("test-subject");

            // Then
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            // Note: Retry behavior verification is now handled in
            // VaultNetworkErrorHandlingTest
            // using WireMock request counting instead of log-based verification.
            // This test focuses on error handling and logging of other aspects.
        }
    }

    @Test
    @DisplayName("Should handle invalid encryption context with detailed error logging")
    void shouldHandleInvalidEncryptionContextWithDetailedErrorLogging() {
        try (var provider = new VaultDecryptingMaterialsProvider(validConfig)) {
            // When
            var future = provider.decryptionKeysFor(
                "test-subject",
                "invalid-encrypted-key".getBytes(),
                "invalid-context-format"
            );

            // Then
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InvalidEncryptionContextException.class);

            // Verify detailed error logging
            var errorLogs = logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();

            assertThat(errorLogs)
                .anyMatch(event -> event.getMessage().contains("Encryption context format is invalid"));
        }
    }

    @Test
    @DisplayName("Should handle subject ID mismatch with clear error logging")
    void shouldHandleSubjectIdMismatchWithClearErrorLogging() {
        try (var provider = new VaultDecryptingMaterialsProvider(validConfig)) {
            // When - context has different subject ID
            var contextWithWrongSubject = "subjectId=wrong-subject;timestamp=1234567890;version=1.0";
            var future = provider.decryptionKeysFor(
                "test-subject",
                "encrypted-key".getBytes(),
                contextWithWrongSubject
            );

            // Then
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InvalidEncryptionContextException.class);

            // Verify subject mismatch error logging
            var errorLogs = logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();

            assertThat(errorLogs).anyMatch(event -> event.getMessage().contains("Subject ID mismatch"));
        }
    }

    @Test
    @DisplayName("Should sanitize URLs in log messages")
    void shouldSanitizeUrlsInLogMessages() {
        // Create config with query parameters that should be sanitized
        var configWithQuery = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200?token=secret123&other=value")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .build();

        // When
        try (var provider = new VaultEncryptingMaterialsProvider(configWithQuery)) {
            // Then
            var logEvents = logAppender.list;

            // Verify URLs are sanitized
            logEvents.forEach(event -> {
                var message = event.getFormattedMessage();
                if (message.contains("vault.example.com")) {
                    assertThat(message).doesNotContain("token=secret123");
                    if (message.contains("?")) {
                        assertThat(message).contains("[REDACTED]");
                    }
                }
            });
        }
    }

    @Test
    @DisplayName("Should log request IDs for correlation")
    void shouldLogRequestIdsForCorrelation() {
        // Stub a server error so that the operation fails quickly but still triggers request/operation logging
        wireMockServer.stubFor(
            any(urlMatching("/v1/transit/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[\"internal server error\"]}")
                )
        );

        try (var provider = new VaultEncryptingMaterialsProvider(validConfig)) {
            var future = provider.encryptionKeysFor("test-subject");

            // Expect failure due to server error; use join to avoid additional timing complexity
            assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(VaultConnectivityException.class);

            // Collect DEBUG logs and verify at least one contains requestId= pattern
            var debugLogs = logAppender.list.stream().filter(e -> e.getLevel() == Level.DEBUG).toList();

            assertThat(debugLogs)
                .as("Expected at least one debug log containing requestId for correlation")
                .anyMatch(event -> event.getMessage().contains("requestId="));
        }
    }

    @Test
    @DisplayName("Should handle configuration validation errors with clear messages")
    void shouldHandleConfigurationValidationErrorsWithClearMessages() {
        // When - invalid URL format
        assertThatThrownBy(() ->
                VaultCryptoConfiguration.builder().vaultUrl("invalid-url-format").vaultToken("token").build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vault URL must start with http:// or https://");

        // When - invalid key prefix
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("token")
                    .keyPrefix("invalid@prefix")
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key prefix can only contain alphanumeric characters");
    }

    @Test
    @DisplayName("Should not log sensitive data in exception messages")
    void shouldNotLogSensitiveDataInExceptionMessages() {
        try (var provider = new VaultEncryptingMaterialsProvider(invalidConfig)) {
            // When
            var future = provider.encryptionKeysFor("test-subject");

            assertThatThrownBy(future::join).isInstanceOf(CompletionException.class);

            // Then - verify no sensitive data in any log messages
            var allLogs = logAppender.list;

            allLogs.forEach(event -> {
                var message = event.getFormattedMessage();

                // Should not contain tokens
                assertThat(message).doesNotContain("invalid-token");

                // Should not contain raw key material
                assertThat(message).doesNotMatch(".*\\b[A-Za-z0-9+/]{40,}={0,2}\\b.*");

                // Should not contain actual plaintext data
                assertThat(message).doesNotMatch(".*plaintext[\\s]*[:=][\\s]*[A-Za-z0-9+/=]{8,}.*");
            });
        }
    }

    @Test
    @DisplayName("Should provide meaningful error messages for different failure scenarios")
    void shouldProvideMeaningfulErrorMessagesForDifferentFailureScenarios() {
        try (var provider = new VaultDecryptingMaterialsProvider(validConfig)) {
            // Test 1: Null encrypted data key
            assertThatThrownBy(() ->
                    provider.decryptionKeysFor("test-subject", null, "valid-context").get(1, TimeUnit.SECONDS)
                )
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("Encrypted data key cannot be null")
                .hasMessageContaining("subjectId=test-subject");

            // Test 2: Empty encrypted data key
            assertThatThrownBy(() ->
                    provider.decryptionKeysFor("test-subject", new byte[0], "valid-context").get(1, TimeUnit.SECONDS)
                )
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("Encrypted data key cannot be null or empty");

            // Test 3: Invalid timestamp in context
            var invalidTimestampContext = "subjectId=test-subject;timestamp=invalid;version=1.0";
            assertThatThrownBy(() ->
                    provider
                        .decryptionKeysFor("test-subject", "key".getBytes(), invalidTimestampContext)
                        .get(1, TimeUnit.SECONDS)
                )
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("Invalid timestamp format");
        }
    }
}
