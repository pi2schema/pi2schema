package pi2schema.serialization.kafka.materials;

import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider;
import pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pi2schema.serialization.kafka.materials.VaultKafkaConfig.*;

class VaultMaterialsProviderTest {

    private VaultMaterialsProvider factory;
    private Map<String, Object> baseConfig;

    @BeforeEach
    void setUp() {
        factory = new VaultMaterialsProvider();
        baseConfig = new HashMap<>();
        baseConfig.put(VAULT_URL_CONFIG, "https://vault.example.com:8200");
        baseConfig.put(VAULT_TOKEN_CONFIG, "hvs.CAESIJ...");
    }

    @Test
    void shouldCreateEncryptingMaterialsProvider() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);

        // When
        Object provider = factory.create(baseConfig);

        // Then
        assertInstanceOf(VaultEncryptingMaterialsProvider.class, provider);
    }

    @Test
    void shouldCreateDecryptingMaterialsProvider() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_DECRYPTING);

        // When
        Object provider = factory.create(baseConfig);

        // Then
        assertInstanceOf(VaultDecryptingMaterialsProvider.class, provider);
    }

    @Test
    void shouldCreateProviderWithAllOptionalConfiguration() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
        baseConfig.put(VAULT_TRANSIT_ENGINE_PATH_CONFIG, "custom-transit");
        baseConfig.put(VAULT_KEY_PREFIX_CONFIG, "custom-prefix");
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, 5000);
        baseConfig.put(VAULT_REQUEST_TIMEOUT_MS_CONFIG, 15000);
        baseConfig.put(VAULT_MAX_RETRIES_CONFIG, 5);
        baseConfig.put(VAULT_RETRY_BACKOFF_MS_CONFIG, 200);

        // When
        Object provider = factory.create(baseConfig);

        // Then
        assertInstanceOf(VaultEncryptingMaterialsProvider.class, provider);
    }

    @Test
    void shouldHandleIntegerConfigurationAsNumber() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, 5000);
        baseConfig.put(VAULT_MAX_RETRIES_CONFIG, 5);

        // When
        Object provider = factory.create(baseConfig);

        // Then
        assertInstanceOf(VaultEncryptingMaterialsProvider.class, provider);
    }

    @Test
    void shouldCreateProviderWithMinimalConfiguration() {
        // Given
        Map<String, Object> minimalConfig = Map.of(
            VAULT_URL_CONFIG,
            "https://vault.example.com:8200",
            VAULT_TOKEN_CONFIG,
            "hvs.CAESIJ...",
            VAULT_PROVIDER_TYPE_CONFIG,
            PROVIDER_TYPE_DECRYPTING
        );

        // When
        Object provider = factory.create(minimalConfig);

        // Then
        assertInstanceOf(VaultDecryptingMaterialsProvider.class, provider);
    }

    @Test
    void shouldHandleCaseInsensitiveProviderType() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, "ENCRYPTING");

        // When
        Object provider = factory.create(baseConfig);

        // Then
        assertInstanceOf(VaultEncryptingMaterialsProvider.class, provider);
    }

    @Test
    void shouldFailWhenVaultUrlIsMissing() {
        // Given
        baseConfig.remove(VAULT_URL_CONFIG);
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_URL_CONFIG));
    }

    @Test
    void shouldFailWhenVaultTokenIsMissing() {
        // Given
        baseConfig.remove(VAULT_TOKEN_CONFIG);
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_TOKEN_CONFIG));
    }

    @Test
    void shouldFailWhenProviderTypeIsMissing() {
        // Given
        // baseConfig already has URL and token, but no provider type

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_PROVIDER_TYPE_CONFIG));
    }

    @Test
    void shouldFailWhenVaultUrlIsEmpty() {
        // Given
        baseConfig.put(VAULT_URL_CONFIG, "");
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("URL cannot be empty"));
    }

    @Test
    void shouldFailWhenVaultTokenIsEmpty() {
        // Given
        baseConfig.put(VAULT_TOKEN_CONFIG, "");
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("Value cannot be empty"));
    }

    @Test
    void shouldFailWhenProviderTypeIsInvalid() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, "invalid-type");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("Provider type must be"));
    }

    @Test
    void shouldFailWhenVaultUrlIsInvalid() {
        // Given - invalid URL format will be caught by VaultKafkaConfig validation
        baseConfig.put(VAULT_URL_CONFIG, "invalid-url");
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("URL must start with http:// or https://"));
    }

    @Test
    void shouldFailWhenIntegerConfigurationIsInvalid() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, "not-a-number");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains(VAULT_CONNECTION_TIMEOUT_MS_CONFIG));
    }

    @Test
    void shouldFailWhenConnectionTimeoutIsNegative() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, -1);

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be at least 1"));
    }

    @Test
    void shouldFailWhenConnectionTimeoutIsTooLarge() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
        baseConfig.put(VAULT_CONNECTION_TIMEOUT_MS_CONFIG, 400000); // > 5 minutes

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("Value must be no more than 300000"));
    }

    @Test
    void shouldFailWhenKeyPrefixIsInvalid() {
        // Given
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
        baseConfig.put(VAULT_KEY_PREFIX_CONFIG, "invalid@prefix");

        // When & Then
        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(baseConfig));
        assertTrue(exception.getMessage().contains("Key prefix can only contain alphanumeric characters"));
    }

    @Test
    void shouldAcceptValidKeyPrefixFormats() {
        // Test various valid key prefix formats
        String[] validPrefixes = { "pi2schema", "my-app", "app_name", "test123" };

        for (String prefix : validPrefixes) {
            baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, PROVIDER_TYPE_ENCRYPTING);
            baseConfig.put(VAULT_KEY_PREFIX_CONFIG, prefix);

            // Should not throw exception
            Object provider = factory.create(baseConfig);
            assertInstanceOf(VaultEncryptingMaterialsProvider.class, provider);
        }
    }

    @Test
    void shouldTrimStringConfigurations() {
        // Given
        baseConfig.put(VAULT_URL_CONFIG, "  https://vault.example.com:8200  ");
        baseConfig.put(VAULT_TOKEN_CONFIG, "  hvs.CAESIJ...  ");
        baseConfig.put(VAULT_PROVIDER_TYPE_CONFIG, "  encrypting  ");
        baseConfig.put(VAULT_TRANSIT_ENGINE_PATH_CONFIG, "  transit  ");

        // When
        Object provider = factory.create(baseConfig);

        // Then
        assertInstanceOf(VaultEncryptingMaterialsProvider.class, provider);
    }
}
