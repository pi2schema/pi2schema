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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

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
    
    private VaultCryptoConfiguration validConfig;
    private VaultCryptoConfiguration invalidConfig;

    @BeforeEach
    void setUp() {
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

        // Create valid configuration
        validConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("test-token-12345")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(10))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();

        // Create invalid configuration for testing
        invalidConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("http://invalid-vault-url:9999")
            .vaultToken("invalid-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofMillis(100))
            .requestTimeout(Duration.ofMillis(200))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(50))
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
    }

    @Test
    @DisplayName("Should log initialization without exposing sensitive data")
    void shouldLogInitializationWithoutSensitiveData() {
        // When
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(validConfig);
        
        // Then
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Verify initialization is logged
        assertTrue(logEvents.stream().anyMatch(event -> 
            event.getLevel() == Level.INFO && 
            event.getMessage().contains("VaultEncryptingMaterialsProvider initialized")
        ), "Should log provider initialization");
        
        assertTrue(logEvents.stream().anyMatch(event -> 
            event.getLevel() == Level.INFO && 
            event.getMessage().contains("VaultTransitClient initialized")
        ), "Should log client initialization");
        
        // Verify no sensitive data in logs
        logEvents.forEach(event -> {
            String message = event.getFormattedMessage();
            assertFalse(message.contains("test-token-12345"), 
                       "Should not log vault token: " + message);
            assertFalse(message.contains("password"), 
                       "Should not log passwords: " + message);
        });
        
        provider.close();
    }

    @Test
    @DisplayName("Should handle null subject ID with proper error logging")
    void shouldHandleNullSubjectIdWithProperErrorLogging() {
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(validConfig);
        
        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(null);
        
        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Subject ID cannot be null or empty", exception.getCause().getMessage());
        
        // Verify error logging
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
        
        assertTrue(errorLogs.stream().anyMatch(event -> 
            event.getMessage().contains("Subject ID cannot be null or empty")
        ), "Should log null subject ID error");
        
        provider.close();
    }

    @Test
    @DisplayName("Should handle empty subject ID with proper error logging")
    void shouldHandleEmptySubjectIdWithProperErrorLogging() {
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(validConfig);
        
        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("   ");
        
        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        
        provider.close();
    }

    @Test
    @DisplayName("Should handle connectivity failures with proper retry logging")
    void shouldHandleConnectivityFailuresWithProperRetryLogging() {
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(invalidConfig);
        
        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");
        
        // Then
        assertThrows(ExecutionException.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });
        
        // Verify retry logging
        List<ILoggingEvent> warnLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .toList();
        
        assertTrue(warnLogs.stream().anyMatch(event -> 
            event.getMessage().contains("retrying")
        ), "Should log retry attempts");
        
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
        
        assertTrue(errorLogs.stream().anyMatch(event -> 
            event.getMessage().contains("Max retries exceeded")
        ), "Should log max retries exceeded");
        
        provider.close();
    }

    @Test
    @DisplayName("Should handle invalid encryption context with detailed error logging")
    void shouldHandleInvalidEncryptionContextWithDetailedErrorLogging() {
        VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(validConfig);
        
        // When
        CompletableFuture<com.google.crypto.tink.Aead> future = provider.decryptionKeysFor(
            "test-subject",
            "invalid-encrypted-key".getBytes(),
            "invalid-context-format"
        );
        
        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        
        // Verify detailed error logging
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
        
        assertTrue(errorLogs.stream().anyMatch(event -> 
            event.getMessage().contains("Encryption context format is invalid")
        ), "Should log encryption context validation error");
        
        provider.close();
    }

    @Test
    @DisplayName("Should handle subject ID mismatch with clear error logging")
    void shouldHandleSubjectIdMismatchWithClearErrorLogging() {
        VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(validConfig);
        
        // When - context has different subject ID
        String contextWithWrongSubject = "subjectId=wrong-subject;timestamp=1234567890;version=1.0";
        CompletableFuture<com.google.crypto.tink.Aead> future = provider.decryptionKeysFor(
            "test-subject",
            "encrypted-key".getBytes(),
            contextWithWrongSubject
        );
        
        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        
        // Verify subject mismatch error logging
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
        
        assertTrue(errorLogs.stream().anyMatch(event -> 
            event.getMessage().contains("Subject ID mismatch")
        ), "Should log subject ID mismatch error");
        
        provider.close();
    }

    @Test
    @DisplayName("Should sanitize URLs in log messages")
    void shouldSanitizeUrlsInLogMessages() {
        // Create config with query parameters that should be sanitized
        VaultCryptoConfiguration configWithQuery = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200?token=secret123&other=value")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .build();
        
        // When
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(configWithQuery);
        
        // Then
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Verify URLs are sanitized
        logEvents.forEach(event -> {
            String message = event.getFormattedMessage();
            if (message.contains("vault.example.com")) {
                assertFalse(message.contains("token=secret123"), 
                           "Should sanitize sensitive query parameters: " + message);
                if (message.contains("?")) {
                    assertTrue(message.contains("[REDACTED]"), 
                              "Should replace query parameters with [REDACTED]: " + message);
                }
            }
        });
        
        provider.close();
    }

    @Test
    @DisplayName("Should log request IDs for correlation")
    void shouldLogRequestIdsForCorrelation() {
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(invalidConfig);
        
        // When - this will fail but should generate request IDs
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");
        
        assertThrows(ExecutionException.class, () -> {
            future.get(3, TimeUnit.SECONDS);
        });
        
        // Then
        List<ILoggingEvent> debugLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.DEBUG)
            .toList();
        
        // Verify request IDs are present in logs
        assertTrue(debugLogs.stream().anyMatch(event -> 
            event.getMessage().contains("requestId=")
        ), "Should include request IDs in debug logs");
        
        provider.close();
    }

    @Test
    @DisplayName("Should handle configuration validation errors with clear messages")
    void shouldHandleConfigurationValidationErrorsWithClearMessages() {
        // When - invalid URL format
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            VaultCryptoConfiguration.builder()
                .vaultUrl("invalid-url-format")
                .vaultToken("token")
                .build();
        });
        
        assertTrue(exception1.getMessage().contains("Vault URL must start with http:// or https://"));
        
        // When - invalid key prefix
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            new VaultEncryptingMaterialsProvider(
                VaultCryptoConfiguration.builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("token")
                    .keyPrefix("invalid@prefix")
                    .build()
            );
        });
        
        assertTrue(exception2.getMessage().contains("Key prefix can only contain alphanumeric characters"));
    }

    @Test
    @DisplayName("Should not log sensitive data in exception messages")
    void shouldNotLogSensitiveDataInExceptionMessages() {
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(invalidConfig);
        
        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-subject");
        
        assertThrows(ExecutionException.class, () -> {
            future.get(3, TimeUnit.SECONDS);
        });
        
        // Then - verify no sensitive data in any log messages
        List<ILoggingEvent> allLogs = logAppender.list;
        
        allLogs.forEach(event -> {
            String message = event.getFormattedMessage();
            
            // Should not contain tokens
            assertFalse(message.contains("invalid-token"), 
                       "Should not log vault token: " + message);
            
            // Should not contain raw key material (check for base64-like patterns)
            assertFalse(message.matches(".*[A-Za-z0-9+/]{32,}={0,2}.*"), 
                       "Should not log base64 key material: " + message);
            
            // Should not contain plaintext data
            assertFalse(message.toLowerCase().contains("plaintext"), 
                       "Should not reference plaintext in logs: " + message);
        });
        
        provider.close();
    }

    @Test
    @DisplayName("Should provide meaningful error messages for different failure scenarios")
    void shouldProvideMeaningfulErrorMessagesForDifferentFailureScenarios() {
        VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(validConfig);
        
        // Test 1: Null encrypted data key
        ExecutionException exception1 = assertThrows(ExecutionException.class, () -> {
            provider.decryptionKeysFor("test-subject", null, "valid-context").get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception1.getCause().getMessage().contains("Encrypted data key cannot be null"));
        assertTrue(exception1.getCause().getMessage().contains("subjectId=test-subject"));
        
        // Test 2: Empty encrypted data key
        ExecutionException exception2 = assertThrows(ExecutionException.class, () -> {
            provider.decryptionKeysFor("test-subject", new byte[0], "valid-context").get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception2.getCause().getMessage().contains("Encrypted data key cannot be null or empty"));
        
        // Test 3: Invalid timestamp in context
        String invalidTimestampContext = "subjectId=test-subject;timestamp=invalid;version=1.0";
        ExecutionException exception3 = assertThrows(ExecutionException.class, () -> {
            provider.decryptionKeysFor("test-subject", "key".getBytes(), invalidTimestampContext).get(1, TimeUnit.SECONDS);
        });
        
        assertTrue(exception3.getCause().getMessage().contains("Invalid timestamp format"));
        
        provider.close();
    }
}