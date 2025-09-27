package pi2schema.crypto.providers.vault;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration class for Vault crypto provider containing all necessary
 * connection and behavior settings.
 *
 * <p>This class provides a builder pattern for configuring the Vault crypto provider
 * with sensible defaults. All configuration parameters are validated during construction
 * to ensure proper operation.</p>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
 *     .vaultUrl("https://vault.example.com:8200")
 *     .vaultToken("hvs.CAESIJ...")
 *     .transitEnginePath("transit")
 *     .keyPrefix("myapp")
 *     .connectionTimeout(Duration.ofSeconds(10))
 *     .requestTimeout(Duration.ofSeconds(30))
 *     .maxRetries(3)
 *     .build();
 * }</pre>
 *
 * <h3>Configuration Parameters:</h3>
 * <ul>
 *   <li><strong>vaultUrl</strong>: The URL of the Vault server (required)</li>
 *   <li><strong>vaultToken</strong>: Authentication token for Vault (required)</li>
 *   <li><strong>transitEnginePath</strong>: Path to the transit engine (default: "transit")</li>
 *   <li><strong>keyPrefix</strong>: Prefix for subject-specific keys (default: "pi2schema")</li>
 *   <li><strong>connectionTimeout</strong>: HTTP connection timeout (default: 10 seconds)</li>
 *   <li><strong>requestTimeout</strong>: Request timeout (default: 30 seconds)</li>
 *   <li><strong>maxRetries</strong>: Maximum retry attempts (default: 3)</li>
 *   <li><strong>retryBackoffMs</strong>: Base retry backoff duration (default: 100ms)</li>
 * </ul>
 *
 * @since 1.0
 * @see VaultEncryptingMaterialsProvider
 * @see VaultDecryptingMaterialsProvider
 */
public final class VaultCryptoConfiguration {

    private final String vaultUrl;
    private final String vaultToken;
    private final String transitEnginePath;
    private final String keyPrefix;
    private final Duration connectionTimeout;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final Duration retryBackoffMs;

    private VaultCryptoConfiguration(
        String vaultUrl,
        String vaultToken,
        String transitEnginePath,
        String keyPrefix,
        Duration connectionTimeout,
        Duration requestTimeout,
        int maxRetries,
        Duration retryBackoffMs
    ) {
        this.vaultUrl = vaultUrl;
        this.vaultToken = vaultToken;
        this.transitEnginePath = transitEnginePath;
        this.keyPrefix = keyPrefix;
        this.connectionTimeout = connectionTimeout;
        this.requestTimeout = requestTimeout;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
    }

    /**
     * Validates the builder parameters before construction.
     *
     * @param builder the builder to validate
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    static void validateBuilder(Builder builder) {
        if (builder.vaultUrl == null || builder.vaultUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Vault URL cannot be null or empty");
        }

        // Validate URL format
        String trimmedUrl = builder.vaultUrl.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Vault URL must start with http:// or https://");
        }

        if (builder.vaultToken == null || builder.vaultToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Vault token cannot be null or empty");
        }

        // Validate token doesn't have leading/trailing whitespace
        if (!builder.vaultToken.equals(builder.vaultToken.trim())) {
            throw new IllegalArgumentException("Vault token cannot contain leading or trailing whitespace");
        }

        if (builder.transitEnginePath == null || builder.transitEnginePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Transit engine path cannot be null or empty");
        }

        if (builder.keyPrefix == null || builder.keyPrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Key prefix cannot be null or empty");
        }

        // Validate key prefix format
        if (!builder.keyPrefix.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException(
                "Key prefix can only contain alphanumeric characters, underscores, and hyphens"
            );
        }

        if (
            builder.connectionTimeout == null ||
            builder.connectionTimeout.isNegative() ||
            builder.connectionTimeout.isZero()
        ) {
            throw new IllegalArgumentException("Connection timeout must be positive");
        }

        // Validate timeout limits
        if (builder.connectionTimeout.toMillis() > 300000) { // 5 minutes
            throw new IllegalArgumentException("Connection timeout cannot exceed 5 minutes");
        }

        if (builder.requestTimeout == null || builder.requestTimeout.isNegative() || builder.requestTimeout.isZero()) {
            throw new IllegalArgumentException("Request timeout must be positive");
        }

        if (builder.requestTimeout.toMillis() > 600000) { // 10 minutes
            throw new IllegalArgumentException("Request timeout cannot exceed 10 minutes");
        }

        if (builder.maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        if (builder.retryBackoffMs == null || builder.retryBackoffMs.isNegative()) {
            throw new IllegalArgumentException("Retry backoff must be non-negative");
        }
    }

    public String getVaultUrl() {
        return vaultUrl;
    }

    public String getVaultToken() {
        return vaultToken;
    }

    public String getTransitEnginePath() {
        return transitEnginePath;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getRetryBackoffMs() {
        return retryBackoffMs;
    }

    /**
     * Creates a new builder for VaultCryptoConfiguration.
     *
     * <p>The builder comes with sensible defaults for all optional parameters:
     * <ul>
     *   <li>transitEnginePath: "transit"</li>
     *   <li>keyPrefix: "pi2schema"</li>
     *   <li>connectionTimeout: 10 seconds</li>
     *   <li>requestTimeout: 30 seconds</li>
     *   <li>maxRetries: 3</li>
     *   <li>retryBackoffMs: 100ms</li>
     * </ul>
     *
     * @return a new builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for VaultCryptoConfiguration with sensible defaults.
     *
     * <p>This builder provides a fluent API for constructing VaultCryptoConfiguration
     * instances with validation. All parameters are validated when {@link #build()}
     * is called.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
     *     .vaultUrl("https://vault.example.com:8200")
     *     .vaultToken(System.getenv("VAULT_TOKEN"))
     *     .build();
     * }</pre>
     */
    public static class Builder {

        private String vaultUrl;
        private String vaultToken;
        private String transitEnginePath = "transit";
        private String keyPrefix = "pi2schema";
        private Duration connectionTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private int maxRetries = 3;
        private Duration retryBackoffMs = Duration.ofMillis(100);

        /**
         * Sets the Vault server URL.
         *
         * @param vaultUrl the Vault server URL (must start with http:// or https://)
         * @return this builder instance
         * @throws IllegalArgumentException if URL is null, empty, or invalid format
         */
        public Builder vaultUrl(String vaultUrl) {
            this.vaultUrl = vaultUrl;
            return this;
        }

        /**
         * Sets the Vault authentication token.
         *
         * @param vaultToken the Vault token for authentication
         * @return this builder instance
         * @throws IllegalArgumentException if token is null, empty, or contains whitespace
         */
        public Builder vaultToken(String vaultToken) {
            this.vaultToken = vaultToken;
            return this;
        }

        /**
         * Sets the path to the transit encryption engine.
         *
         * @param transitEnginePath the path to the transit engine (default: "transit")
         * @return this builder instance
         * @throws IllegalArgumentException if path is null or empty
         */
        public Builder transitEnginePath(String transitEnginePath) {
            this.transitEnginePath = transitEnginePath;
            return this;
        }

        /**
         * Sets the prefix for subject-specific keys in Vault.
         *
         * @param keyPrefix the key prefix (default: "pi2schema", must contain only alphanumeric, underscore, hyphen)
         * @return this builder instance
         * @throws IllegalArgumentException if prefix is null, empty, or contains invalid characters
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        /**
         * Sets the HTTP connection timeout.
         *
         * @param connectionTimeout the connection timeout (default: 10 seconds, max: 5 minutes)
         * @return this builder instance
         * @throws IllegalArgumentException if timeout is null, zero, negative, or exceeds 5 minutes
         */
        public Builder connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * Sets the HTTP request timeout.
         *
         * @param requestTimeout the request timeout (default: 30 seconds, max: 10 minutes)
         * @return this builder instance
         * @throws IllegalArgumentException if timeout is null, zero, negative, or exceeds 10 minutes
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts for failed requests.
         *
         * @param maxRetries the maximum retry attempts (default: 3, must be non-negative)
         * @return this builder instance
         * @throws IllegalArgumentException if maxRetries is negative
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the base backoff duration for retry attempts.
         *
         * @param retryBackoffMs the base retry backoff duration (default: 100ms, must be non-negative)
         * @return this builder instance
         * @throws IllegalArgumentException if backoff is null or negative
         */
        public Builder retryBackoffMs(Duration retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
            return this;
        }

        /**
         * Builds and validates the VaultCryptoConfiguration instance.
         *
         * <p>This method validates all configuration parameters and throws
         * IllegalArgumentException if any validation fails.</p>
         *
         * @return a new VaultCryptoConfiguration instance
         * @throws IllegalArgumentException if any configuration parameter is invalid
         */
        public VaultCryptoConfiguration build() {
            validateBuilder(this);
            return new VaultCryptoConfiguration(
                vaultUrl,
                vaultToken,
                transitEnginePath,
                keyPrefix,
                connectionTimeout,
                requestTimeout,
                maxRetries,
                retryBackoffMs
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VaultCryptoConfiguration that = (VaultCryptoConfiguration) o;
        return (
            maxRetries == that.maxRetries &&
            Objects.equals(vaultUrl, that.vaultUrl) &&
            Objects.equals(vaultToken, that.vaultToken) &&
            Objects.equals(transitEnginePath, that.transitEnginePath) &&
            Objects.equals(keyPrefix, that.keyPrefix) &&
            Objects.equals(connectionTimeout, that.connectionTimeout) &&
            Objects.equals(requestTimeout, that.requestTimeout) &&
            Objects.equals(retryBackoffMs, that.retryBackoffMs)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            vaultUrl,
            vaultToken,
            transitEnginePath,
            keyPrefix,
            connectionTimeout,
            requestTimeout,
            maxRetries,
            retryBackoffMs
        );
    }

    @Override
    public String toString() {
        return (
            "VaultCryptoConfiguration{" +
            "vaultUrl='" +
            vaultUrl +
            '\'' +
            ", vaultToken='[REDACTED]'" +
            ", transitEnginePath='" +
            transitEnginePath +
            '\'' +
            ", keyPrefix='" +
            keyPrefix +
            '\'' +
            ", connectionTimeout=" +
            connectionTimeout +
            ", requestTimeout=" +
            requestTimeout +
            ", maxRetries=" +
            maxRetries +
            ", retryBackoffMs=" +
            retryBackoffMs +
            '}'
        );
    }
}
