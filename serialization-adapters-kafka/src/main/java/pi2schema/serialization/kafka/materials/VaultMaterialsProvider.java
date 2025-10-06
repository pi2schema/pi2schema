package pi2schema.serialization.kafka.materials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;
import pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider;
import pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider;

import java.util.Map;

/**
 * Kafka materials provider factory for HashiCorp Vault crypto provider.
 *
 * <p>
 * This factory creates Vault-based materials providers from Kafka configuration
 * properties
 * using the standard Apache Kafka ConfigDef mechanism. It supports both
 * encrypting and
 * decrypting materials providers using Vault's transit encryption engine for
 * GDPR-compliant
 * key management.
 * </p>
 *
 * <p>
 * Configuration is handled by {@link VaultKafkaConfig} which follows Apache
 * Kafka standards
 * for configuration definition, validation, and type conversion.
 * </p>
 *
 * <h3>Example Kafka Configuration:</h3>
 * 
 * <pre>{@code
 * pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.VaultMaterialsProvider
 * pi2schema.vault.url=https://vault.example.com:8200
 * pi2schema.vault.token=hvs.CAESIJ...
 * pi2schema.vault.provider.type=encrypting
 * }</pre>
 *
 * @since 1.0
 * @see VaultKafkaConfig
 * @see VaultEncryptingMaterialsProvider
 * @see VaultDecryptingMaterialsProvider
 * @see VaultCryptoConfiguration
 */
public class VaultMaterialsProvider implements MaterialsProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(VaultMaterialsProvider.class);

    /**
     * Creates a Vault materials provider from Kafka configuration properties.
     *
     * <p>
     * This method uses {@link VaultKafkaConfig} to parse and validate the Kafka
     * configuration
     * properties according to Apache Kafka standards, then creates the appropriate
     * materials
     * provider based on the provider type.
     * </p>
     *
     * @param configs Kafka configuration properties
     * @return VaultEncryptingMaterialsProvider or VaultDecryptingMaterialsProvider
     *         based on configuration
     * @throws org.apache.kafka.common.config.ConfigException if configuration is
     *                                                        invalid
     * @throws IllegalArgumentException                       if provider type is
     *                                                        invalid
     */
    @Override
    public Object create(Map<String, ?> configs) {
        logger.debug("Creating Vault materials provider from Kafka configuration");

        try {
            // Use VaultKafkaConfig for standard Kafka configuration handling
            VaultKafkaConfig vaultKafkaConfig = new VaultKafkaConfig(configs);

            // Convert to VaultCryptoConfiguration
            VaultCryptoConfiguration vaultConfig = vaultKafkaConfig.toVaultCryptoConfiguration();
            String providerType = vaultKafkaConfig.getProviderType();

            Object provider = createProvider(vaultConfig, providerType);

            logger.info(
                    "Successfully created Vault materials provider [type={}, vaultUrl={}]",
                    providerType,
                    sanitizeUrl(vaultConfig.getVaultUrl()));

            return provider;
        } catch (Exception e) {
            logger.error("Failed to create Vault materials provider: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates the appropriate materials provider based on the provider type.
     *
     * @param vaultConfig  the Vault configuration
     * @param providerType the provider type ("encrypting" or "decrypting")
     * @return the materials provider instance
     * @throws IllegalArgumentException if provider type is invalid
     */
    private Object createProvider(VaultCryptoConfiguration vaultConfig, String providerType) {
        return switch (providerType.toLowerCase()) {
            case VaultKafkaConfig.PROVIDER_TYPE_ENCRYPTING -> {
                logger.debug("Creating VaultEncryptingMaterialsProvider");
                yield new VaultEncryptingMaterialsProvider(vaultConfig);
            }
            case VaultKafkaConfig.PROVIDER_TYPE_DECRYPTING -> {
                logger.debug("Creating VaultDecryptingMaterialsProvider");
                yield new VaultDecryptingMaterialsProvider(vaultConfig);
            }
            default -> throw new IllegalArgumentException(
                    "Invalid provider type: " +
                            providerType +
                            ". Must be '" +
                            VaultKafkaConfig.PROVIDER_TYPE_ENCRYPTING +
                            "' or '" +
                            VaultKafkaConfig.PROVIDER_TYPE_DECRYPTING +
                            "'");
        };
    }

    /**
     * Sanitizes URLs for logging by removing sensitive information.
     *
     * @param url the URL to sanitize
     * @return the sanitized URL safe for logging
     */
    private String sanitizeUrl(String url) {
        if (url == null) {
            return "null";
        }

        // Remove any query parameters that might contain sensitive data
        int queryIndex = url.indexOf('?');
        if (queryIndex != -1) {
            url = url.substring(0, queryIndex) + "?[REDACTED]";
        }

        return url.replaceAll("token=[^&]*", "token=[REDACTED]");
    }
}
