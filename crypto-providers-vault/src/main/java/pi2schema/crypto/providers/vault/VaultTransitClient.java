package pi2schema.crypto.providers.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client for interacting with HashiCorp Vault's transit encryption engine.
 * Handles authentication, encryption, decryption, and key management operations
 * with proper retry logic and connection pooling.
 */
public class VaultTransitClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(VaultTransitClient.class);
    private static final String VAULT_TOKEN_HEADER = "X-Vault-Token";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final VaultCryptoConfiguration config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * Creates a new VaultTransitClient with the specified configuration.
     *
     * @param config the Vault configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public VaultTransitClient(VaultCryptoConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = config.getVaultUrl().replaceAll("/$", "") + "/v1/" + config.getTransitEnginePath();

        this.httpClient = HttpClient.newBuilder().connectTimeout(config.getConnectionTimeout()).build();

        logger.info("VaultTransitClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Encrypts the given plaintext using the specified key name and encryption context.
     *
     * @param keyName the name of the key in Vault
     * @param plaintext the data to encrypt
     * @param context the encryption context for additional security
     * @return a CompletableFuture containing the encrypted data
     */
    public CompletableFuture<byte[]> encrypt(String keyName, byte[] plaintext, String context) {
        logger.debug(
            "Encrypting data with key: {} (context length: {})",
            keyName,
            context != null ? context.length() : 0
        );

        return ensureKeyExists(keyName)
            .thenCompose(ignored -> performEncrypt(keyName, plaintext, context))
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Encryption failed for key: {}", keyName, throwable);
                } else {
                    logger.debug("Encryption successful for key: {}", keyName);
                }
            });
    }

    /**
     * Decrypts the given ciphertext using the specified key name and encryption context.
     *
     * @param keyName the name of the key in Vault
     * @param ciphertext the data to decrypt
     * @param context the encryption context used during encryption
     * @return a CompletableFuture containing the decrypted data
     */
    public CompletableFuture<byte[]> decrypt(String keyName, byte[] ciphertext, String context) {
        logger.debug(
            "Decrypting data with key: {} (context length: {})",
            keyName,
            context != null ? context.length() : 0
        );

        return performDecrypt(keyName, ciphertext, context)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Decryption failed for key: {}", keyName, throwable);
                } else {
                    logger.debug("Decryption successful for key: {}", keyName);
                }
            });
    }

    /**
     * Ensures that the specified key exists in Vault, creating it if necessary.
     *
     * @param keyName the name of the key to ensure exists
     * @return a CompletableFuture that completes when the key is confirmed to exist
     */
    public CompletableFuture<Void> ensureKeyExists(String keyName) {
        logger.debug("Ensuring key exists: {}", keyName);

        return checkKeyExists(keyName)
            .thenCompose(exists -> {
                if (exists) {
                    logger.debug("Key already exists: {}", keyName);
                    return CompletableFuture.completedFuture(null);
                } else {
                    logger.debug("Creating new key: {}", keyName);
                    return createKey(keyName);
                }
            });
    }

    /**
     * Generates the full key name including the configured prefix and subject pattern.
     *
     * @param subjectId the subject identifier
     * @return the full key name for Vault
     */
    public String generateKeyName(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject ID cannot be null or empty");
        }

        // Sanitize subject ID to prevent path traversal
        String sanitizedSubjectId = subjectId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return config.getKeyPrefix() + "/subject/" + sanitizedSubjectId;
    }

    private CompletableFuture<byte[]> performEncrypt(String keyName, byte[] plaintext, String context) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("plaintext", Base64.getEncoder().encodeToString(plaintext));

        if (context != null && !context.isEmpty()) {
            requestBody.put("context", Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8)));
        }

        String url = baseUrl + "/encrypt/" + keyName;

        return executeWithRetry(() -> {
            try {
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .header(VAULT_TOKEN_HEADER, config.getVaultToken())
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .timeout(config.getRequestTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        handleHttpResponse(response, "encrypt");
                        return parseCiphertext(response.body());
                    });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(new VaultConnectivityException("Failed to encrypt data", e));
            }
        });
    }

    private CompletableFuture<byte[]> performDecrypt(String keyName, byte[] ciphertext, String context) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ciphertext", new String(ciphertext, StandardCharsets.UTF_8));

        if (context != null && !context.isEmpty()) {
            requestBody.put("context", Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8)));
        }

        String url = baseUrl + "/decrypt/" + keyName;

        return executeWithRetry(() -> {
            try {
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .header(VAULT_TOKEN_HEADER, config.getVaultToken())
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .timeout(config.getRequestTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        handleHttpResponse(response, "decrypt");
                        return parsePlaintext(response.body());
                    });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(new VaultConnectivityException("Failed to decrypt data", e));
            }
        });
    }

    CompletableFuture<Boolean> checkKeyExists(String keyName) {
        String url = baseUrl + "/keys/" + keyName;

        return executeWithRetry(() -> {
            try {
                HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .header(VAULT_TOKEN_HEADER, config.getVaultToken())
                    .timeout(config.getRequestTimeout())
                    .GET()
                    .build();

                return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            return true;
                        } else if (response.statusCode() == 404) {
                            return false;
                        } else {
                            handleHttpResponse(response, "check key existence");
                            return false; // This line should not be reached due to exception in handleHttpResponse
                        }
                    });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(
                    new VaultConnectivityException("Failed to check key existence", e)
                );
            }
        });
    }

    private CompletableFuture<Void> createKey(String keyName) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("type", "aes256-gcm96");

        String url = baseUrl + "/keys/" + keyName;

        return executeWithRetry(() -> {
            try {
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .header(VAULT_TOKEN_HEADER, config.getVaultToken())
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .timeout(config.getRequestTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        handleHttpResponse(response, "create key");
                        return null;
                    });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(new VaultConnectivityException("Failed to create key", e));
            }
        });
    }

    private <T> CompletableFuture<T> executeWithRetry(java.util.function.Supplier<CompletableFuture<T>> operation) {
        return executeWithRetry(operation, 0);
    }

    private <T> CompletableFuture<T> executeWithRetry(
        java.util.function.Supplier<CompletableFuture<T>> operation,
        int attempt
    ) {
        return operation
            .get()
            .handle((result, throwable) -> {
                if (throwable == null) {
                    return CompletableFuture.completedFuture(result);
                }

                if (attempt >= config.getMaxRetries()) {
                    logger.error("Max retries ({}) exceeded, failing operation", config.getMaxRetries());
                    return CompletableFuture.<T>failedFuture(throwable);
                }

                if (isRetryableException(throwable)) {
                    long delay = calculateBackoffDelay(attempt);
                    logger.warn(
                        "Operation failed (attempt {}/{}), retrying in {}ms",
                        attempt + 1,
                        config.getMaxRetries() + 1,
                        delay,
                        throwable
                    );

                    // For testing, we'll use a simpler approach without actual delay
                    if (delay == 0) {
                        return executeWithRetry(operation, attempt + 1);
                    }

                    CompletableFuture<T> delayedRetry = new CompletableFuture<>();
                    java.util.concurrent.ScheduledExecutorService scheduler =
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

                    scheduler.schedule(
                        () -> {
                            try {
                                executeWithRetry(operation, attempt + 1)
                                    .whenComplete((retryResult, retryThrowable) -> {
                                        scheduler.shutdown();
                                        if (retryThrowable != null) {
                                            delayedRetry.completeExceptionally(retryThrowable);
                                        } else {
                                            delayedRetry.complete(retryResult);
                                        }
                                    });
                            } catch (Exception e) {
                                scheduler.shutdown();
                                delayedRetry.completeExceptionally(e);
                            }
                        },
                        delay,
                        java.util.concurrent.TimeUnit.MILLISECONDS
                    );

                    return delayedRetry;
                } else {
                    logger.error("Non-retryable exception occurred", throwable);
                    return CompletableFuture.<T>failedFuture(throwable);
                }
            })
            .thenCompose(future -> future);
    }

    private boolean isRetryableException(Throwable throwable) {
        // Retry on connectivity issues but not on authentication or validation errors
        return (
            throwable instanceof VaultConnectivityException ||
            (throwable instanceof IOException) ||
            (throwable.getCause() instanceof IOException)
        );
    }

    private long calculateBackoffDelay(int attempt) {
        long baseDelay = config.getRetryBackoffMs().toMillis();
        long exponentialDelay = baseDelay * (1L << attempt); // 2^attempt
        long maxDelay = Duration.ofSeconds(5).toMillis(); // Cap at 5 seconds

        long delay = Math.min(exponentialDelay, maxDelay);

        // Add jitter (±25%)
        double jitterFactor = 0.75 + (ThreadLocalRandom.current().nextDouble() * 0.5);
        return Math.round(delay * jitterFactor);
    }

    private void handleHttpResponse(HttpResponse<String> response, String operation) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            return; // Success
        }

        String errorMessage = String.format("Vault %s operation failed with status %d", operation, statusCode);

        try {
            JsonNode errorNode = objectMapper.readTree(response.body());
            if (errorNode.has("errors") && errorNode.get("errors").isArray()) {
                String errors = errorNode.get("errors").toString();
                errorMessage += ": " + errors;
            }
        } catch (Exception e) {
            logger.debug("Could not parse error response body", e);
        }

        if (statusCode == 401 || statusCode == 403) {
            throw new VaultAuthenticationException(errorMessage);
        } else if (statusCode == 404) {
            throw new SubjectKeyNotFoundException("", errorMessage);
        } else if (statusCode >= 500) {
            throw new VaultConnectivityException(errorMessage);
        } else {
            throw new VaultCryptoException(errorMessage);
        }
    }

    private byte[] parseCiphertext(String responseBody) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = responseNode.get("data");

            if (dataNode == null || !dataNode.has("ciphertext")) {
                throw new VaultCryptoException("Invalid response format: missing ciphertext");
            }

            String ciphertext = dataNode.get("ciphertext").asText();
            return ciphertext.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VaultCryptoException("Failed to parse encryption response", e);
        }
    }

    private byte[] parsePlaintext(String responseBody) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = responseNode.get("data");

            if (dataNode == null || !dataNode.has("plaintext")) {
                throw new VaultCryptoException("Invalid response format: missing plaintext");
            }

            String plaintext = dataNode.get("plaintext").asText();
            return Base64.getDecoder().decode(plaintext);
        } catch (IOException e) {
            throw new VaultCryptoException("Failed to parse decryption response", e);
        } catch (IllegalArgumentException e) {
            throw new VaultCryptoException("Failed to decode base64 plaintext", e);
        }
    }

    @Override
    public void close() {
        logger.info("Closing VaultTransitClient");
        try {
            // Cancel any pending operations and clean up resources
            // HttpClient in Java 11+ manages its own resources, but we should ensure
            // any scheduled retry operations are properly cancelled
            
            // Note: In a production implementation, we might want to track active operations
            // and provide a graceful shutdown mechanism, but for now we rely on the
            // HttpClient's internal resource management
            
            logger.debug("VaultTransitClient closed successfully");
        } catch (Exception e) {
            logger.warn("Error during VaultTransitClient cleanup", e);
            // Don't rethrow exceptions from close() method
        }
    }
}
