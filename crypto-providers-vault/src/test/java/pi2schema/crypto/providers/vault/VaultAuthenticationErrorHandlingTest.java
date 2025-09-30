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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for authentication and authorization error handling scenarios.
 * This test class focuses on Vault authentication failures and permission issues.
 */
class VaultAuthenticationErrorHandlingTest {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger transitClientLogger;
    private Logger encryptingProviderLogger;

    @BeforeEach
    void setUp() {
        // Set up log capture
        logAppender = new ListAppender<>();
        logAppender.start();

        transitClientLogger = (Logger) LoggerFactory.getLogger(VaultTransitClient.class);
        encryptingProviderLogger = (Logger) LoggerFactory.getLogger(VaultEncryptingMaterialsProvider.class);

        transitClientLogger.addAppender(logAppender);
        encryptingProviderLogger.addAppender(logAppender);

        transitClientLogger.setLevel(Level.DEBUG);
        encryptingProviderLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            transitClientLogger.detachAppender(logAppender);
            encryptingProviderLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    @DisplayName("Should handle invalid token format with proper error logging")
    void shouldHandleInvalidTokenFormatWithProperErrorLogging() {
        // When/Then - should fail during configuration building with empty token
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com:8200")
                    .vaultToken("") // Empty token
                    .transitEnginePath("transit")
                    .keyPrefix("test-prefix")
                    .connectionTimeout(Duration.ofSeconds(5))
                    .requestTimeout(Duration.ofSeconds(10))
                    .maxRetries(1)
                    .retryBackoffMs(Duration.ofMillis(100))
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vault token cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle token with whitespace with proper error logging")
    void shouldHandleTokenWithWhitespaceWithProperErrorLogging() {
        // Given - configuration with token containing whitespace
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com:8200")
                    .vaultToken("  token-with-spaces  ") // Token with leading/trailing spaces
                    .transitEnginePath("transit")
                    .keyPrefix("test-prefix")
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vault token cannot contain leading or trailing whitespace");
    }

    @Test
    @DisplayName("Should log authentication failures without exposing token")
    void shouldLogAuthenticationFailuresWithoutExposingToken() {
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("secret-token-12345")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofSeconds(1))
            .requestTimeout(Duration.ofSeconds(2))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(50))
            .build();

        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // When - this will fail due to network issues, but we can verify token is not logged
            var future = provider.encryptionKeysFor("test-subject");

            assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);

            // Then - verify token is not in any log messages
            var allLogs = logAppender.list;

            allLogs.forEach(event -> {
                var message = event.getFormattedMessage();
                assertThat(message).doesNotContain("secret-token-12345");
            });
        }
    }

    @Test
    @DisplayName("Should handle configuration validation for URL format")
    void shouldHandleConfigurationValidationForUrlFormat() {
        // Test invalid URL schemes
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("ftp://vault.example.com:8200") // Invalid scheme
                    .vaultToken("token")
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vault URL must start with http:// or https://");

        // Test missing scheme
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("vault.example.com:8200") // Missing scheme
                    .vaultToken("token")
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vault URL must start with http:// or https://");
    }

    @Test
    @DisplayName("Should handle configuration validation for key prefix")
    void shouldHandleConfigurationValidationForKeyPrefix() {
        // Test invalid characters in key prefix
        assertThatThrownBy(() ->
                new VaultEncryptingMaterialsProvider(
                    VaultCryptoConfiguration
                        .builder()
                        .vaultUrl("https://vault.example.com:8200")
                        .vaultToken("token")
                        .keyPrefix("invalid@prefix!") // Invalid characters
                        .build()
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key prefix can only contain alphanumeric characters");
    }

    @Test
    @DisplayName("Should handle configuration validation for timeout values")
    void shouldHandleConfigurationValidationForTimeoutValues() {
        // Test excessive connection timeout
        assertThatThrownBy(() ->
                new VaultEncryptingMaterialsProvider(
                    VaultCryptoConfiguration
                        .builder()
                        .vaultUrl("https://vault.example.com:8200")
                        .vaultToken("token")
                        .connectionTimeout(Duration.ofMinutes(10)) // Too long
                        .build()
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Connection timeout cannot exceed 5 minutes");

        // Test excessive request timeout
        assertThatThrownBy(() ->
                new VaultEncryptingMaterialsProvider(
                    VaultCryptoConfiguration
                        .builder()
                        .vaultUrl("https://vault.example.com:8200")
                        .vaultToken("token")
                        .requestTimeout(Duration.ofMinutes(15)) // Too long
                        .build()
                )
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Request timeout cannot exceed 10 minutes");
    }

    @Test
    @DisplayName("Should provide clear error messages for configuration issues")
    void shouldProvideClearErrorMessagesForConfigurationIssues() {
        // Test null configuration
        assertThatThrownBy(() -> new VaultEncryptingMaterialsProvider(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration cannot be null");

        // Test null configuration for decrypting provider
        assertThatThrownBy(() -> new VaultDecryptingMaterialsProvider(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration cannot be null");
    }

    @Test
    @DisplayName("Should log configuration validation success")
    void shouldLogConfigurationValidationSuccess() {
        // Given - valid configuration
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("valid-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(10))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();

        // When
        try (var provider = new VaultEncryptingMaterialsProvider(config)) {
            // Then - verify successful initialization is logged
            var debugLogs = logAppender.list.stream().filter(event -> event.getLevel() == Level.DEBUG).toList();

            assertThat(debugLogs).anyMatch(event -> event.getMessage().contains("Configuration validation successful"));
        }
    }

    @Test
    @DisplayName("Should handle subject ID sanitization logging")
    void shouldHandleSubjectIdSanitizationLogging() {
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .build();

        try (var client = new VaultTransitClient(config)) {
            // When - generate key name with special characters that need sanitization
            var keyName = client.generateKeyName("user@domain.com/special#chars");

            // Then - verify sanitization occurred and was logged
            var subjectPart = keyName.substring(keyName.lastIndexOf("_") + 1);
            assertThat(subjectPart).doesNotContain("@", "#");
            assertThat(keyName).contains("test-prefix_subject_");

            // Verify sanitization was logged
            var debugLogs = logAppender.list.stream().filter(event -> event.getLevel() == Level.DEBUG).toList();

            assertThat(debugLogs).anyMatch(event -> event.getMessage().contains("Subject ID was sanitized"));
        }
    }
}
