package pi2schema.crypto.providers.vault.examples;

import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;

import java.time.Duration;

/**
 * Examples of different Vault crypto provider configurations for various deployment scenarios.
 *
 * <p>This class demonstrates how to configure the Vault crypto provider for different
 * environments and use cases, from development to production deployments.</p>
 */
public class ConfigurationExamples {

    /**
     * Basic development configuration with minimal settings.
     * Suitable for local development with Vault dev server.
     */
    public static VaultCryptoConfiguration developmentConfig() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://localhost:8200")
            .vaultToken("dev-only-token")
            .build(); // Uses all default values
    }

    /**
     * Production configuration with custom timeouts and retry settings.
     * Suitable for high-availability production environments.
     */
    public static VaultCryptoConfiguration productionConfig() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.company.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .transitEnginePath("transit")
            .keyPrefix("production-app")
            .connectionTimeout(Duration.ofSeconds(15))
            .requestTimeout(Duration.ofSeconds(45))
            .maxRetries(5)
            .retryBackoffMs(Duration.ofMillis(200))
            .build();
    }

    /**
     * Configuration for microservices with fast timeouts.
     * Optimized for low-latency microservice environments.
     */
    public static VaultCryptoConfiguration microserviceConfig() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault-internal.k8s.local:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .transitEnginePath("microservices-transit")
            .keyPrefix("user-service")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(15))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(50))
            .build();
    }

    /**
     * Configuration for batch processing with longer timeouts.
     * Suitable for batch jobs that can tolerate higher latency.
     */
    public static VaultCryptoConfiguration batchProcessingConfig() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.company.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .transitEnginePath("batch-transit")
            .keyPrefix("batch-processor")
            .connectionTimeout(Duration.ofSeconds(30))
            .requestTimeout(Duration.ofMinutes(2))
            .maxRetries(10)
            .retryBackoffMs(Duration.ofSeconds(1))
            .build();
    }

    /**
     * Configuration for multi-tenant applications.
     * Uses tenant-specific key prefixes for isolation.
     */
    public static VaultCryptoConfiguration multiTenantConfig(String tenantId) {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.company.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .transitEnginePath("tenant-transit")
            .keyPrefix("tenant-" + tenantId)
            .connectionTimeout(Duration.ofSeconds(10))
            .requestTimeout(Duration.ofSeconds(30))
            .maxRetries(3)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();
    }

    /**
     * Configuration using environment variables for all settings.
     * Suitable for containerized deployments with external configuration.
     */
    public static VaultCryptoConfiguration environmentBasedConfig() {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl(System.getenv("VAULT_URL"))
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .transitEnginePath(getEnvOrDefault("VAULT_TRANSIT_PATH", "transit"))
            .keyPrefix(getEnvOrDefault("VAULT_KEY_PREFIX", "pi2schema"))
            .connectionTimeout(
                Duration.ofSeconds(Integer.parseInt(getEnvOrDefault("VAULT_CONNECTION_TIMEOUT_SECONDS", "10")))
            )
            .requestTimeout(
                Duration.ofSeconds(Integer.parseInt(getEnvOrDefault("VAULT_REQUEST_TIMEOUT_SECONDS", "30")))
            )
            .maxRetries(Integer.parseInt(getEnvOrDefault("VAULT_MAX_RETRIES", "3")))
            .retryBackoffMs(Duration.ofMillis(Integer.parseInt(getEnvOrDefault("VAULT_RETRY_BACKOFF_MS", "100"))))
            .build();
    }

    /**
     * Configuration for testing with mock Vault (using Testcontainers).
     * Suitable for integration tests.
     */
    public static VaultCryptoConfiguration testConfig(String vaultUrl, String vaultToken) {
        return VaultCryptoConfiguration
            .builder()
            .vaultUrl(vaultUrl)
            .vaultToken(vaultToken)
            .transitEnginePath("transit")
            .keyPrefix("test")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(10))
            .maxRetries(1)
            .retryBackoffMs(Duration.ofMillis(10))
            .build();
    }

    /**
     * Helper method to get environment variable with default value.
     */
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }

    /**
     * Example of configuration validation and error handling.
     */
    public static void demonstrateConfigurationValidation() {
        try {
            // This will fail validation
            VaultCryptoConfiguration.builder().vaultUrl("invalid-url").vaultToken("token").build();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration validation failed: " + e.getMessage());
        }

        try {
            // This will also fail validation
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("https://vault.example.com:8200")
                .vaultToken("") // Empty token
                .build();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration validation failed: " + e.getMessage());
        }

        // Valid configuration
        VaultCryptoConfiguration validConfig = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("hvs.CAESIJ...")
            .build();

        System.out.println("Valid configuration created: " + validConfig);
    }
}
