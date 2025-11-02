package pi2schema.serialization.kafka.materials;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.types.Password;
import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;

import java.time.Duration;
import java.util.Map;

/**
 * Kafka configuration for Vault materials provider following Apache Kafka
 * ConfigDef standards.
 *
 * <p>
 * This configuration class defines all Vault-related configuration properties
 * using Kafka's
 * standard ConfigDef mechanism, providing automatic type conversion,
 * validation, and
 * documentation generation.
 * </p>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 * <li><strong>pi2schema.vault.url</strong>: Vault server URL (required)</li>
 * <li><strong>pi2schema.vault.token</strong>: Vault authentication token
 * (required)</li>
 * <li><strong>pi2schema.vault.transit.engine.path</strong>: Transit engine path
 * (default: "transit")</li>
 * <li><strong>pi2schema.vault.key.prefix</strong>: Key prefix for subjects
 * (default: "pi2schema")</li>
 * <li><strong>pi2schema.vault.connection.timeout.ms</strong>: Connection
 * timeout in milliseconds (default: 10000)</li>
 * <li><strong>pi2schema.vault.request.timeout.ms</strong>: Request timeout in
 * milliseconds (default: 30000)</li>
 * <li><strong>pi2schema.vault.max.retries</strong>: Maximum retry attempts
 * (default: 3)</li>
 * <li><strong>pi2schema.vault.retry.backoff.ms</strong>: Base retry backoff in
 * milliseconds (default: 100)</li>
 * <li><strong>pi2schema.vault.provider.type</strong>: Provider type -
 * "encrypting" or "decrypting" (required)</li>
 * </ul>
 *
 * @since 1.0
 * @see VaultMaterialsProvider
 * @see VaultCryptoConfiguration
 */
public class VaultKafkaConfig extends AbstractConfig {

    // Configuration property names
    public static final String VAULT_URL_CONFIG = "pi2schema.vault.url";
    public static final String VAULT_TOKEN_CONFIG = "pi2schema.vault.token";
    public static final String VAULT_TRANSIT_ENGINE_PATH_CONFIG = "pi2schema.vault.transit.engine.path";
    public static final String VAULT_KEY_PREFIX_CONFIG = "pi2schema.vault.key.prefix";
    public static final String VAULT_CONNECTION_TIMEOUT_MS_CONFIG = "pi2schema.vault.connection.timeout.ms";
    public static final String VAULT_REQUEST_TIMEOUT_MS_CONFIG = "pi2schema.vault.request.timeout.ms";
    public static final String VAULT_MAX_RETRIES_CONFIG = "pi2schema.vault.max.retries";
    public static final String VAULT_RETRY_BACKOFF_MS_CONFIG = "pi2schema.vault.retry.backoff.ms";
    public static final String VAULT_PROVIDER_TYPE_CONFIG = "pi2schema.vault.provider.type";

    // Provider type constants
    public static final String PROVIDER_TYPE_ENCRYPTING = "encrypting";
    public static final String PROVIDER_TYPE_DECRYPTING = "decrypting";

    // Configuration documentation
    private static final String VAULT_URL_DOC = "The URL of the Vault server (must start with http:// or https://)";
    private static final String VAULT_TOKEN_DOC = "Authentication token for Vault access";
    private static final String VAULT_TRANSIT_ENGINE_PATH_DOC = "Path to the transit encryption engine in Vault";
    private static final String VAULT_KEY_PREFIX_DOC =
        "Prefix for subject-specific keys in Vault (alphanumeric, underscore, hyphen only)";
    private static final String VAULT_CONNECTION_TIMEOUT_MS_DOC = "HTTP connection timeout in milliseconds";
    private static final String VAULT_REQUEST_TIMEOUT_MS_DOC = "HTTP request timeout in milliseconds";
    private static final String VAULT_MAX_RETRIES_DOC = "Maximum number of retry attempts for failed requests";
    private static final String VAULT_RETRY_BACKOFF_MS_DOC = "Base backoff duration in milliseconds for retry attempts";
    private static final String VAULT_PROVIDER_TYPE_DOC =
        "Type of materials provider to create: 'encrypting' for producers, 'decrypting' for consumers";

    private static final ConfigDef CONFIG;

    static {
        CONFIG =
            new ConfigDef()
                .define(
                    VAULT_URL_CONFIG,
                    ConfigDef.Type.STRING,
                    ConfigDef.NO_DEFAULT_VALUE,
                    new UrlValidator(),
                    ConfigDef.Importance.HIGH,
                    VAULT_URL_DOC
                )
                .define(
                    VAULT_TOKEN_CONFIG,
                    ConfigDef.Type.PASSWORD,
                    ConfigDef.NO_DEFAULT_VALUE,
                    new NonEmptyStringValidator(),
                    ConfigDef.Importance.HIGH,
                    VAULT_TOKEN_DOC
                )
                .define(
                    VAULT_TRANSIT_ENGINE_PATH_CONFIG,
                    ConfigDef.Type.STRING,
                    "transit",
                    new NonEmptyStringValidator(),
                    ConfigDef.Importance.MEDIUM,
                    VAULT_TRANSIT_ENGINE_PATH_DOC
                )
                .define(
                    VAULT_KEY_PREFIX_CONFIG,
                    ConfigDef.Type.STRING,
                    "pi2schema",
                    new KeyPrefixValidator(),
                    ConfigDef.Importance.MEDIUM,
                    VAULT_KEY_PREFIX_DOC
                )
                .define(
                    VAULT_CONNECTION_TIMEOUT_MS_CONFIG,
                    ConfigDef.Type.INT,
                    10000,
                    ConfigDef.Range.between(1, 300000), // 1ms to 5 minutes
                    ConfigDef.Importance.LOW,
                    VAULT_CONNECTION_TIMEOUT_MS_DOC
                )
                .define(
                    VAULT_REQUEST_TIMEOUT_MS_CONFIG,
                    ConfigDef.Type.INT,
                    30000,
                    ConfigDef.Range.between(1, 600000), // 1ms to 10 minutes
                    ConfigDef.Importance.LOW,
                    VAULT_REQUEST_TIMEOUT_MS_DOC
                )
                .define(
                    VAULT_MAX_RETRIES_CONFIG,
                    ConfigDef.Type.INT,
                    3,
                    ConfigDef.Range.atLeast(0),
                    ConfigDef.Importance.LOW,
                    VAULT_MAX_RETRIES_DOC
                )
                .define(
                    VAULT_RETRY_BACKOFF_MS_CONFIG,
                    ConfigDef.Type.INT,
                    100,
                    ConfigDef.Range.atLeast(0),
                    ConfigDef.Importance.LOW,
                    VAULT_RETRY_BACKOFF_MS_DOC
                )
                .define(
                    VAULT_PROVIDER_TYPE_CONFIG,
                    ConfigDef.Type.STRING,
                    ConfigDef.NO_DEFAULT_VALUE,
                    new ProviderTypeValidator(),
                    ConfigDef.Importance.HIGH,
                    VAULT_PROVIDER_TYPE_DOC
                );
    }

    /**
     * Creates a new VaultKafkaConfig from the provided configuration properties.
     *
     * @param originals the configuration properties
     */
    public VaultKafkaConfig(Map<?, ?> originals) {
        super(CONFIG, originals);
    }

    /**
     * Gets the Vault server URL.
     *
     * @return the Vault server URL
     */
    public String getVaultUrl() {
        return getString(VAULT_URL_CONFIG);
    }

    /**
     * Gets the Vault authentication token.
     *
     * @return the Vault authentication token
     */
    public Password getVaultToken() {
        return getPassword(VAULT_TOKEN_CONFIG);
    }

    /**
     * Gets the transit engine path.
     *
     * @return the transit engine path
     */
    public String getTransitEnginePath() {
        return getString(VAULT_TRANSIT_ENGINE_PATH_CONFIG);
    }

    /**
     * Gets the key prefix for subject-specific keys.
     *
     * @return the key prefix
     */
    public String getKeyPrefix() {
        return getString(VAULT_KEY_PREFIX_CONFIG);
    }

    /**
     * Gets the connection timeout in milliseconds.
     *
     * @return the connection timeout in milliseconds
     */
    public int getConnectionTimeoutMs() {
        return getInt(VAULT_CONNECTION_TIMEOUT_MS_CONFIG);
    }

    /**
     * Gets the request timeout in milliseconds.
     *
     * @return the request timeout in milliseconds
     */
    public int getRequestTimeoutMs() {
        return getInt(VAULT_REQUEST_TIMEOUT_MS_CONFIG);
    }

    /**
     * Gets the maximum number of retry attempts.
     *
     * @return the maximum retry attempts
     */
    public int getMaxRetries() {
        return getInt(VAULT_MAX_RETRIES_CONFIG);
    }

    /**
     * Gets the retry backoff duration in milliseconds.
     *
     * @return the retry backoff duration in milliseconds
     */
    public int getRetryBackoffMs() {
        return getInt(VAULT_RETRY_BACKOFF_MS_CONFIG);
    }

    /**
     * Gets the provider type.
     *
     * @return the provider type ("encrypting" or "decrypting")
     */
    public String getProviderType() {
        return getString(VAULT_PROVIDER_TYPE_CONFIG);
    }

    /**
     * Converts this Kafka configuration to a VaultCryptoConfiguration.
     *
     * @return the equivalent VaultCryptoConfiguration
     */
    public VaultCryptoConfiguration toVaultCryptoConfiguration() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl(getVaultUrl())
            .vaultToken(getVaultToken().value())
            .transitEnginePath(getTransitEnginePath())
            .keyPrefix(getKeyPrefix())
            .connectionTimeout(Duration.ofMillis(getConnectionTimeoutMs()))
            .requestTimeout(Duration.ofMillis(getRequestTimeoutMs()))
            .maxRetries(getMaxRetries())
            .retryBackoffMs(Duration.ofMillis(getRetryBackoffMs()))
            .build();
    }

    /**
     * Validator for URL format.
     */
    private static class UrlValidator implements ConfigDef.Validator {

        @Override
        public void ensureValid(String name, Object value) {
            if (value == null) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "URL cannot be null");
            }

            String url = value.toString().trim();
            if (url.isEmpty()) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "URL cannot be empty");
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new org.apache.kafka.common.config.ConfigException(
                    name,
                    value,
                    "URL must start with http:// or https://"
                );
            }
        }
    }

    /**
     * Validator for non-empty strings.
     */
    private static class NonEmptyStringValidator implements ConfigDef.Validator {

        @Override
        public void ensureValid(String name, Object value) {
            if (value == null) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "Value cannot be null");
            }

            String str = value.toString().trim();
            if (str.isEmpty()) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "Value cannot be empty");
            }
        }
    }

    /**
     * Validator for key prefix format.
     */
    private static class KeyPrefixValidator implements ConfigDef.Validator {

        @Override
        public void ensureValid(String name, Object value) {
            if (value == null) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "Key prefix cannot be null");
            }

            String prefix = value.toString().trim();
            if (prefix.isEmpty()) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "Key prefix cannot be empty");
            }

            if (!prefix.matches("^[a-zA-Z0-9_-]+$")) {
                throw new org.apache.kafka.common.config.ConfigException(
                    name,
                    value,
                    "Key prefix can only contain alphanumeric characters, underscores, and hyphens"
                );
            }
        }
    }

    /**
     * Validator for provider type.
     */
    private static class ProviderTypeValidator implements ConfigDef.Validator {

        @Override
        public void ensureValid(String name, Object value) {
            if (value == null) {
                throw new org.apache.kafka.common.config.ConfigException(name, value, "Provider type cannot be null");
            }

            String type = value.toString().toLowerCase().trim();
            if (!PROVIDER_TYPE_ENCRYPTING.equals(type) && !PROVIDER_TYPE_DECRYPTING.equals(type)) {
                throw new org.apache.kafka.common.config.ConfigException(
                    name,
                    value,
                    "Provider type must be '" + PROVIDER_TYPE_ENCRYPTING + "' or '" + PROVIDER_TYPE_DECRYPTING + "'"
                );
            }
        }
    }
}
