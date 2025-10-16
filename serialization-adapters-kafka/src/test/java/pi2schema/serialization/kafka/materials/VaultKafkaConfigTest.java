package pi2schema.serialization.kafka.materials;

import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pi2schema.serialization.kafka.materials.VaultKafkaConfig.*;

class VaultKafkaConfigTest {

    private Map<String, Object> baseConfig;

    @BeforeEach
    void setUp() {
        baseConfig = new HashMap<>();
        baseConfig.put(VAULT_URL_CONFIG, "https://vault.example.com:8200");
        baseConfig.put(VAULT_TOKEN_CONFIG, "hvs.CAESIJ...");
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
    }

    @Test
    void shouldCreateConfigWithRequiredProperties() {
        // When
        VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);

        // Then
        assertEquals("https://vault.example.com:8200", config.getVaultUrl());
        assertEquals("hvs.CAESIJ...", config.getVaultToken().value());
        assertEquals(PROVIDER_TYPE_ENCRYPTING, config.getProviderType());

        // Verify defaults
        assertEquals("transit", config.getTransitEnginePath());
        assertEquals("pi2schema", config.getKeyPrefix());
        assertEquals(10000, config.getConnectionTimeoutMs());
        assertEquals(30000, config.getRequestTimeoutMs());
        assertEquals(3, config.getMaxRetries());
        assertEquals(100, config.getRetryBackoffMs());
    }

    @Test
    void shouldCreateConfigWithAllProperties() {
        // Given
        baseConfig.put(VAULT_TRANSIT_ENGINE_PATH_CONFIG, "custom-transit");
        baseConfig.put(VAULT_KEY_PREFIX_CONFIG, "custom-prefix");
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, 5000);
        baseConfig.put(VAULT_REQUEST_TIMEOUT_MS_CONFIG, 15000);
        baseConfig.put(VAULT_MAX_RETRIES_CONFIG, 5);
        baseConfig.put(VAULT_RETRY_BACKOFF_MS_CONFIG, 200);

        // When
        VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);

        // Then
        assertEquals("https://vault.example.com:8200", config.getVaultUrl());
        assertEquals("hvs.CAESIJ...", config.getVaultToken().value());
        assertEquals("custom-transit", config.getTransitEnginePath());
        assertEquals("custom-prefix", config.getKeyPrefix());
        assertEquals(5000, config.getConnectionTimeoutMs());
        assertEquals(15000, config.getRequestTimeoutMs());
        assertEquals(5, config.getMaxRetries());
        assertEquals(200, config.getRetryBackoffMs());
        assertEquals(PROVIDER_TYPE_ENCRYPTING, config.getProviderType());
    }

    @Test
    void shouldConvertToVaultCryptoConfiguration() {
        // Given
        baseConfig.put(VAULT_TRANSIT_ENGINE_PATH_CONFIG, "custom-transit");
        baseConfig.put(VAULT_KEY_PREFIX_CONFIG, "custom-prefix");
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, 5000);
        baseConfig.put(VAULT_REQUEST_TIMEOUT_MS_CONFIG, 15000);
        baseConfig.put(VAULT_MAX_RETRIES_CONFIG, 5);
        baseConfig.put(VAULT_RETRY_BACKOFF_MS_CONFIG, 200);

        VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);

        // When
        VaultCryptoConfiguration vaultConfig = config.toVaultCryptoConfiguration();

        // Then
        assertEquals("https://vault.example.com:8200", vaultConfig.getVaultUrl());
        assertEquals("hvs.CAESIJ...", vaultConfig.getVaultToken());
        assertEquals("custom-transit", vaultConfig.getTransitEnginePath());
        assertEquals("custom-prefix", vaultConfig.getKeyPrefix());
        assertEquals(Duration.ofMillis(5000), vaultConfig.getConnectionTimeout());
        assertEquals(Duration.ofMillis(15000), vaultConfig.getRequestTimeout());
        assertEquals(5, vaultConfig.getMaxRetries());
        assertEquals(Duration.ofMillis(200), vaultConfig.getRetryBackoffMs());
    }

    @Test
    void shouldFailWhenVaultUrlIsMissing() {
        // Given
        baseConfig.remove(VAULT_URL_CONFIG);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_URL_CONFIG));
    }

    @Test
    void shouldFailWhenVaultTokenIsMissing() {
        // Given
        baseConfig.remove(VAULT_TOKEN_CONFIG);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_TOKEN_CONFIG));
    }

    @Test
    void shouldFailWhenProviderTypeIsMissing() {
        // Given
        baseConfig.remove(VAULT_PROVIDER_TYPE_CONFIG);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_PROVIDER_TYPE_CONFIG));
    }

    @Test
    void shouldFailWhenVaultUrlIsEmpty() {
        // Given
        baseConfig.put(VAULT_URL_CONFIG, "");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("URL cannot be empty"));
    }

    @Test
    void shouldFailWhenVaultUrlIsInvalid() {
        // Given
        baseConfig.put(VAULT_URL_CONFIG, "invalid-url");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("URL must start with http:// or https://"));
    }

    @Test
    void shouldFailWhenProviderTypeIsInvalid() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, "invalid-type");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Provider type must be"));
    }

    @Test
    void shouldAcceptCaseInsensitiveProviderType() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, "ENCRYPTING");

        // When
        VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);

        // Then
        assertEquals("ENCRYPTING", config.getProviderType());
    }

    @Test
    void shouldFailWhenKeyPrefixIsInvalid() {
        // Given
        baseConfig.put(VAULT_KEY_PREFIX_CONFIG, "invalid@prefix");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Key prefix can only contain alphanumeric characters"));
    }

    @Test
    void shouldFailWhenConnectionTimeoutIsNegative() {
        // Given
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, -1);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be at least 1"));
    }

    @Test
    void shouldFailWhenConnectionTimeoutIsTooLarge() {
        // Given
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, 400000); // > 5 minutes

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be no more than 300000"));
    }

    @Test
    void shouldFailWhenRequestTimeoutIsTooLarge() {
        // Given
        baseConfig.put(VAULT_REQUEST_TIMEOUT_MS_CONFIG, 700000); // > 10 minutes

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be no more than 600000"));
    }

    @Test
    void shouldFailWhenMaxRetriesIsNegative() {
        // Given
        baseConfig.put(VAULT_MAX_RETRIES_CONFIG, -1);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be at least 0"));
    }

    @Test
    void shouldFailWhenRetryBackoffIsNegative() {
        // Given
        baseConfig.put(VAULT_RETRY_BACKOFF_MS_CONFIG, -1);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> new VaultKafkaConfig(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be at least 0"));
    }

    @Test
    void shouldAcceptValidKeyPrefixFormats() {
        // Test various valid key prefix formats
        String[] validPrefixes = { "pi2schema", "my-app", "app_name", "test123", "a", "A-B_C-1" };

        for (String prefix : validPrefixes) {
            baseConfig.put(VAULT_KEY_PREFIX_CONFIG, prefix);

            // Should not throw exception
            VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);
            assertEquals(prefix, config.getKeyPrefix());
        }
    }

    @Test
    void shouldTrimStringValues() {
        // Given
        baseConfig.put(VAULT_URL_CONFIG, "  https://vault.example.com:8200  ");
        baseConfig.put(VAULT_TOKEN_CONFIG, "  hvs.CAESIJ...  ");
        baseConfig.put(VAULT_TRANSIT_ENGINE_PATH_CONFIG, "  transit  ");
        baseConfig.put(VAULT_KEY_PREFIX_CONFIG, "  pi2schema  ");
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, "  encrypting  ");

        // When
        VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);

        // Then - values should be trimmed
        assertEquals("https://vault.example.com:8200", config.getVaultUrl());
        assertEquals("transit", config.getTransitEnginePath());
        assertEquals("pi2schema", config.getKeyPrefix());
        assertEquals("encrypting", config.getProviderType());
    }

    @Test
    void shouldHandleDecryptingProviderType() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_DECRYPTING);

        // When
        VaultKafkaConfig config = new VaultKafkaConfig(baseConfig);

        // Then
        assertEquals(PROVIDER_TYPE_DECRYPTING, config.getProviderType());
    }
}
