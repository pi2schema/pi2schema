package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class VaultCryptoConfigurationTest {

    @Test
    @DisplayName("Should create configuration with valid parameters")
    void shouldCreateConfigurationWithValidParameters() {
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .build();

        assertEquals("https://vault.example.com", config.getVaultUrl());
        assertEquals("test-token", config.getVaultToken());
        assertEquals("transit", config.getTransitEnginePath());
        assertEquals("pi2schema", config.getKeyPrefix());
        assertEquals(Duration.ofSeconds(10), config.getConnectionTimeout());
        assertEquals(Duration.ofSeconds(30), config.getRequestTimeout());
        assertEquals(3, config.getMaxRetries());
        assertEquals(Duration.ofMillis(100), config.getRetryBackoffMs());
    }

    @Test
    @DisplayName("Should create configuration with custom parameters")
    void shouldCreateConfigurationWithCustomParameters() {
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://custom-vault.example.com")
            .vaultToken("custom-token")
            .transitEnginePath("custom-transit")
            .keyPrefix("custom-prefix")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(60))
            .maxRetries(5)
            .retryBackoffMs(Duration.ofMillis(200))
            .build();

        assertEquals("https://custom-vault.example.com", config.getVaultUrl());
        assertEquals("custom-token", config.getVaultToken());
        assertEquals("custom-transit", config.getTransitEnginePath());
        assertEquals("custom-prefix", config.getKeyPrefix());
        assertEquals(Duration.ofSeconds(5), config.getConnectionTimeout());
        assertEquals(Duration.ofSeconds(60), config.getRequestTimeout());
        assertEquals(5, config.getMaxRetries());
        assertEquals(Duration.ofMillis(200), config.getRetryBackoffMs());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid vault URL")
    void shouldThrowExceptionForInvalidVaultUrl(String invalidUrl) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> VaultCryptoConfiguration.builder().vaultUrl(invalidUrl).vaultToken("test-token").build()
        );
        assertEquals("Vault URL cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid vault token")
    void shouldThrowExceptionForInvalidVaultToken(String invalidToken) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken(invalidToken)
                    .build()
        );
        assertEquals("Vault token cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid transit engine path")
    void shouldThrowExceptionForInvalidTransitEnginePath(String invalidPath) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .transitEnginePath(invalidPath)
                    .build()
        );
        assertEquals("Transit engine path cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid key prefix")
    void shouldThrowExceptionForInvalidKeyPrefix(String invalidPrefix) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .keyPrefix(invalidPrefix)
                    .build()
        );
        assertEquals("Key prefix cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null connection timeout")
    void shouldThrowExceptionForNullConnectionTimeout() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(null)
                    .build()
        );
        assertEquals("Connection timeout must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for zero connection timeout")
    void shouldThrowExceptionForZeroConnectionTimeout() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(Duration.ZERO)
                    .build()
        );
        assertEquals("Connection timeout must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for negative connection timeout")
    void shouldThrowExceptionForNegativeConnectionTimeout() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(Duration.ofSeconds(-1))
                    .build()
        );
        assertEquals("Connection timeout must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null request timeout")
    void shouldThrowExceptionForNullRequestTimeout() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(null)
                    .build()
        );
        assertEquals("Request timeout must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for zero request timeout")
    void shouldThrowExceptionForZeroRequestTimeout() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(Duration.ZERO)
                    .build()
        );
        assertEquals("Request timeout must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for negative request timeout")
    void shouldThrowExceptionForNegativeRequestTimeout() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(Duration.ofSeconds(-1))
                    .build()
        );
        assertEquals("Request timeout must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for negative max retries")
    void shouldThrowExceptionForNegativeMaxRetries() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .maxRetries(-1)
                    .build()
        );
        assertEquals("Max retries cannot be negative", exception.getMessage());
    }

    @Test
    @DisplayName("Should allow zero max retries")
    void shouldAllowZeroMaxRetries() {
        assertDoesNotThrow(() ->
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("https://vault.example.com")
                .vaultToken("test-token")
                .maxRetries(0)
                .build()
        );
    }

    @Test
    @DisplayName("Should throw exception for null retry backoff")
    void shouldThrowExceptionForNullRetryBackoff() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .retryBackoffMs(null)
                    .build()
        );
        assertEquals("Retry backoff must be non-negative", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for negative retry backoff")
    void shouldThrowExceptionForNegativeRetryBackoff() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .retryBackoffMs(Duration.ofMillis(-1))
                    .build()
        );
        assertEquals("Retry backoff must be non-negative", exception.getMessage());
    }

    @Test
    @DisplayName("Should allow zero retry backoff")
    void shouldAllowZeroRetryBackoff() {
        assertDoesNotThrow(() ->
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("https://vault.example.com")
                .vaultToken("test-token")
                .retryBackoffMs(Duration.ZERO)
                .build()
        );
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        VaultCryptoConfiguration config1 = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .build();

        VaultCryptoConfiguration config2 = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .build();

        VaultCryptoConfiguration config3 = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://different-vault.example.com")
            .vaultToken("test-token")
            .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
        assertNotEquals(config1.hashCode(), config3.hashCode());
    }

    @Test
    @DisplayName("Should redact token in toString")
    void shouldRedactTokenInToString() {
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("secret-token")
            .build();

        String toString = config.toString();
        assertFalse(toString.contains("secret-token"));
        assertTrue(toString.contains("[REDACTED]"));
        assertTrue(toString.contains("https://vault.example.com"));
    }
}
