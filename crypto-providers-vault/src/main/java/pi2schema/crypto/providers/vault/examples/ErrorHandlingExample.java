package pi2schema.crypto.providers.vault.examples;

import pi2schema.crypto.providers.EncryptionMaterial;
import pi2schema.crypto.providers.vault.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating comprehensive error handling with Vault crypto providers.
 *
 * <p>This example shows how to handle various error conditions that can occur
 * when using the Vault crypto providers, including network issues, authentication
 * failures, and invalid configurations.</p>
 */
public class ErrorHandlingExample {

    /**
     * Demonstrates handling of configuration validation errors.
     */
    public static void demonstrateConfigurationErrors() {
        System.out.println("=== Configuration Error Handling ===");

        // Invalid URL format
        try {
            VaultCryptoConfiguration.builder().vaultUrl("invalid-url").vaultToken("token").build();
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Caught invalid URL error: " + e.getMessage());
        }

        // Empty token
        try {
            VaultCryptoConfiguration.builder().vaultUrl("https://vault.example.com:8200").vaultToken("").build();
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Caught empty token error: " + e.getMessage());
        }

        // Invalid timeout
        try {
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("https://vault.example.com:8200")
                .vaultToken("token")
                .connectionTimeout(Duration.ofSeconds(-1))
                .build();
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Caught invalid timeout error: " + e.getMessage());
        }

        // Invalid key prefix
        try {
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("https://vault.example.com:8200")
                .vaultToken("token")
                .keyPrefix("invalid/prefix")
                .build();
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Caught invalid key prefix error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates handling of authentication errors.
     */
    public static void demonstrateAuthenticationErrors() {
        System.out.println("\n=== Authentication Error Handling ===");

        // Configuration with invalid token
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("invalid-token")
            .maxRetries(1) // Reduce retries for faster demo
            .build();

        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-user");

            try {
                future.get();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof VaultAuthenticationException) {
                    System.out.println("✓ Caught authentication error: " + cause.getMessage());
                } else if (cause instanceof VaultConnectivityException) {
                    System.out.println("✓ Caught connectivity error (may indicate auth issue): " + cause.getMessage());
                } else {
                    System.out.println("✓ Caught error (likely auth-related): " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("✓ Provider initialization or cleanup error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates handling of connectivity errors.
     */
    public static void demonstrateConnectivityErrors() {
        System.out.println("\n=== Connectivity Error Handling ===");

        // Configuration with unreachable Vault server
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://unreachable-vault.example.com:8200")
            .vaultToken("token")
            .connectionTimeout(Duration.ofSeconds(2)) // Short timeout for demo
            .maxRetries(1)
            .build();

        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-user");

            try {
                future.get();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof VaultConnectivityException) {
                    System.out.println("✓ Caught connectivity error: " + cause.getMessage());
                } else {
                    System.out.println("✓ Caught network-related error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("✓ Provider error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates handling of subject key not found errors during decryption.
     */
    public static void demonstrateSubjectKeyNotFoundErrors() {
        System.out.println("\n=== Subject Key Not Found Error Handling ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("valid-token")
            .build();

        try (VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config)) {
            // Attempt to decrypt with non-existent subject key
            byte[] fakeEncryptedKey = "fake-encrypted-key".getBytes();
            String fakeContext = "subjectId=nonexistent-user;timestamp=1234567890;version=1.0";

            CompletableFuture<com.google.crypto.tink.Aead> future = provider.decryptionKeysFor(
                "nonexistent-user",
                fakeEncryptedKey,
                fakeContext
            );

            try {
                future.get();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof SubjectKeyNotFoundException) {
                    SubjectKeyNotFoundException skne = (SubjectKeyNotFoundException) cause;
                    System.out.println("✓ Caught subject key not found error:");
                    System.out.println("  Subject ID: " + skne.getSubjectId());
                    System.out.println("  Message: " + skne.getMessage());
                } else {
                    System.out.println("✓ Caught related error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("✓ Provider error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates handling of invalid encryption context errors.
     */
    public static void demonstrateInvalidEncryptionContextErrors() {
        System.out.println("\n=== Invalid Encryption Context Error Handling ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("valid-token")
            .build();

        try (VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config)) {
            byte[] fakeEncryptedKey = "fake-encrypted-key".getBytes();

            // Test various invalid contexts
            String[] invalidContexts = {
                "invalid-format",
                "subjectId=user1", // Missing timestamp and version
                "subjectId=user1;timestamp=abc;version=1.0", // Invalid timestamp
                "subjectId=user1;timestamp=1234567890;version=", // Empty version
                "subjectId=user2;timestamp=1234567890;version=1.0", // Subject ID mismatch
            };

            for (String invalidContext : invalidContexts) {
                try {
                    CompletableFuture<com.google.crypto.tink.Aead> future = provider.decryptionKeysFor(
                        "user1",
                        fakeEncryptedKey,
                        invalidContext
                    );
                    future.get();
                } catch (Exception e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof InvalidEncryptionContextException) {
                        InvalidEncryptionContextException iece = (InvalidEncryptionContextException) cause;
                        System.out.println("✓ Caught invalid context error:");
                        System.out.println("  Context: " + iece.getEncryptionContext());
                        System.out.println("  Message: " + iece.getMessage());
                    } else {
                        System.out.println("✓ Caught validation error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("✓ Provider error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates proper error handling patterns with retry logic.
     */
    public static void demonstrateRetryPatterns() {
        System.out.println("\n=== Retry Pattern Demonstration ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("token")
            .maxRetries(3)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();

        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-user");

            // Handle with custom retry logic
            future.handle((result, throwable) -> {
                if (throwable != null) {
                    System.out.println("Operation failed after retries: " + throwable.getMessage());

                    // Determine if retry is appropriate
                    Throwable cause = throwable.getCause();
                    if (cause instanceof VaultConnectivityException) {
                        System.out.println("→ Connectivity issue - consider circuit breaker pattern");
                    } else if (cause instanceof VaultAuthenticationException) {
                        System.out.println("→ Authentication issue - refresh token and retry");
                    } else {
                        System.out.println("→ Other error - check configuration and Vault status");
                    }
                } else {
                    System.out.println("✓ Operation succeeded");
                }
                return result;
            });
        } catch (Exception e) {
            System.out.println("Provider error: " + e.getMessage());
        }
    }

    /**
     * Demonstrates graceful degradation patterns.
     */
    public static void demonstrateGracefulDegradation() {
        System.out.println("\n=== Graceful Degradation Patterns ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken("token")
            .build();

        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("test-user");

            // Implement fallback strategy
            future.handle((result, throwable) -> {
                if (throwable != null) {
                    System.out.println("Primary encryption failed: " + throwable.getMessage());

                    // Example fallback strategies:
                    System.out.println("Fallback options:");
                    System.out.println("1. Use local encryption (reduced security)");
                    System.out.println("2. Queue operation for later retry");
                    System.out.println("3. Return error to user with retry suggestion");
                    System.out.println("4. Use cached encryption materials (if available)");

                    // In practice, implement appropriate fallback
                    return null; // or fallback result
                } else {
                    System.out.println("✓ Primary encryption succeeded");
                    return result;
                }
            });
        } catch (Exception e) {
            System.out.println("Provider initialization failed - using fallback encryption");
        }
    }

    public static void main(String[] args) {
        demonstrateConfigurationErrors();
        demonstrateAuthenticationErrors();
        demonstrateConnectivityErrors();
        demonstrateSubjectKeyNotFoundErrors();
        demonstrateInvalidEncryptionContextErrors();
        demonstrateRetryPatterns();
        demonstrateGracefulDegradation();

        System.out.println("\n=== Error Handling Best Practices ===");
        System.out.println("✓ Validate configuration early");
        System.out.println("✓ Handle specific exception types appropriately");
        System.out.println("✓ Implement retry logic for transient failures");
        System.out.println("✓ Use circuit breaker pattern for repeated failures");
        System.out.println("✓ Provide meaningful error messages to users");
        System.out.println("✓ Log errors with sufficient context for debugging");
        System.out.println("✓ Implement graceful degradation where possible");
    }
}
