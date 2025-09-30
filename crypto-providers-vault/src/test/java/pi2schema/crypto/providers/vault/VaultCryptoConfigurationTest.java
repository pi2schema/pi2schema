package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class VaultCryptoConfigurationTest {

    @Test
    @DisplayName("Should create configuration with valid parameters")
    void shouldCreateConfigurationWithValidParameters() {
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .build();

        assertThat(config.getVaultUrl()).isEqualTo("https://vault.example.com");
        assertThat(config.getVaultToken()).isEqualTo("test-token");
        assertThat(config.getTransitEnginePath()).isEqualTo("transit");
        assertThat(config.getKeyPrefix()).isEqualTo("pi2schema");
        assertThat(config.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getRetryBackoffMs()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("Should create configuration with custom parameters")
    void shouldCreateConfigurationWithCustomParameters() {
        var config = VaultCryptoConfiguration
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

        assertThat(config.getVaultUrl()).isEqualTo("https://custom-vault.example.com");
        assertThat(config.getVaultToken()).isEqualTo("custom-token");
        assertThat(config.getTransitEnginePath()).isEqualTo("custom-transit");
        assertThat(config.getKeyPrefix()).isEqualTo("custom-prefix");
        assertThat(config.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getMaxRetries()).isEqualTo(5);
        assertThat(config.getRetryBackoffMs()).isEqualTo(Duration.ofMillis(200));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid vault URL")
    void shouldThrowExceptionForInvalidVaultUrl(String invalidUrl) {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration.builder().vaultUrl(invalidUrl).vaultToken("test-token").build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Vault URL cannot be null or empty");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid vault token")
    void shouldThrowExceptionForInvalidVaultToken(String invalidToken) {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken(invalidToken)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Vault token cannot be null or empty");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid transit engine path")
    void shouldThrowExceptionForInvalidTransitEnginePath(String invalidPath) {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .transitEnginePath(invalidPath)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transit engine path cannot be null or empty");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @DisplayName("Should throw exception for invalid key prefix")
    void shouldThrowExceptionForInvalidKeyPrefix(String invalidPrefix) {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .keyPrefix(invalidPrefix)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Key prefix cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for null connection timeout")
    void shouldThrowExceptionForNullConnectionTimeout() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(null)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Connection timeout must be positive");
    }

    @Test
    @DisplayName("Should throw exception for zero connection timeout")
    void shouldThrowExceptionForZeroConnectionTimeout() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(Duration.ZERO)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Connection timeout must be positive");
    }

    @Test
    @DisplayName("Should throw exception for negative connection timeout")
    void shouldThrowExceptionForNegativeConnectionTimeout() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(Duration.ofSeconds(-1))
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Connection timeout must be positive");
    }

    @Test
    @DisplayName("Should throw exception for null request timeout")
    void shouldThrowExceptionForNullRequestTimeout() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(null)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Request timeout must be positive");
    }

    @Test
    @DisplayName("Should throw exception for zero request timeout")
    void shouldThrowExceptionForZeroRequestTimeout() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(Duration.ZERO)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Request timeout must be positive");
    }

    @Test
    @DisplayName("Should throw exception for negative request timeout")
    void shouldThrowExceptionForNegativeRequestTimeout() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(Duration.ofSeconds(-1))
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Request timeout must be positive");
    }

    @Test
    @DisplayName("Should throw exception for negative max retries")
    void shouldThrowExceptionForNegativeMaxRetries() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .maxRetries(-1)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Max retries cannot be negative");
    }

    @Test
    @DisplayName("Should allow zero max retries")
    void shouldAllowZeroMaxRetries() {
        assertThatCode(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .maxRetries(0)
                    .build()
            )
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception for null retry backoff")
    void shouldThrowExceptionForNullRetryBackoff() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .retryBackoffMs(null)
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Retry backoff must be non-negative");
    }

    @Test
    @DisplayName("Should throw exception for negative retry backoff")
    void shouldThrowExceptionForNegativeRetryBackoff() {
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .retryBackoffMs(Duration.ofMillis(-1))
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Retry backoff must be non-negative");
    }

    @Test
    @DisplayName("Should allow zero retry backoff")
    void shouldAllowZeroRetryBackoff() {
        assertThatCode(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .retryBackoffMs(Duration.ZERO)
                    .build()
            )
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        var config1 = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .build();

        var config2 = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .build();

        var config3 = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://different-vault.example.com")
            .vaultToken("test-token")
            .build();

        assertThat(config1).isEqualTo(config2).hasSameHashCodeAs(config2);
        assertThat(config1).isNotEqualTo(config3);
        assertThat(config1.hashCode()).isNotEqualTo(config3.hashCode());
    }

    @Test
    @DisplayName("Should redact token in toString")
    void shouldRedactTokenInToString() {
        var config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("secret-token")
            .build();

        var toString = config.toString();
        assertThat(toString)
            .doesNotContain("secret-token")
            .contains("[REDACTED]")
            .contains("https://vault.example.com");
    }
}
