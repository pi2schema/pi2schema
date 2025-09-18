package pi2schema.crypto.providers.vault;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration class for Vault crypto provider containing all necessary
 * connection and behavior settings.
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
            throw new IllegalArgumentException("Key prefix can only contain alphanumeric characters, underscores, and hyphens");
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
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for VaultCryptoConfiguration with sensible defaults.
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

        public Builder vaultUrl(String vaultUrl) {
            this.vaultUrl = vaultUrl;
            return this;
        }

        public Builder vaultToken(String vaultToken) {
            this.vaultToken = vaultToken;
            return this;
        }

        public Builder transitEnginePath(String transitEnginePath) {
            this.transitEnginePath = transitEnginePath;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryBackoffMs(Duration retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
            return this;
        }

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
