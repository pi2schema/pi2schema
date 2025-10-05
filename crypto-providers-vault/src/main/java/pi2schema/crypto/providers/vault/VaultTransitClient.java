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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for interacting with HashiCorp Vault's transit encryption engine.
 *
 * <p>
 * This client handles authentication, encryption, decryption, and key
 * management operations
 * with proper retry logic and connection pooling. It provides a high-level
 * interface for
 * Vault's transit encryption API while handling error conditions and
 * performance optimization.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 * <li>Asynchronous operations using CompletableFuture</li>
 * <li>Automatic retry with exponential backoff</li>
 * <li>HTTP connection pooling for performance</li>
 * <li>Comprehensive error handling and logging</li>
 * <li>Request correlation for debugging</li>
 * </ul>
 *
 * <h3>Key Management:</h3>
 * <p>
 * Keys are automatically created if they don't exist when first accessed.
 * The client uses subject-specific key naming following the pattern:
 * {@code {keyPrefix}/subject/{subjectId}}
 * </p>
 *
 * <h3>Error Handling:</h3>
 * <p>
 * The client handles various error conditions:
 * <ul>
 * <li>Network timeouts and connectivity issues</li>
 * <li>Authentication failures</li>
 * <li>Invalid requests and responses</li>
 * <li>Vault server errors</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is thread-safe and designed for concurrent use across multiple
 * threads.
 * </p>
 *
 * @since 1.0
 * @see VaultCryptoConfiguration
 * @see VaultEncryptingMaterialsProvider
 * @see VaultDecryptingMaterialsProvider
 */
public class VaultTransitClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(VaultTransitClient.class);
    private static final String VAULT_TOKEN_HEADER = "X-Vault-Token";
    private static final String CONTENT_TYPE_JSON = "application/json";

    // Request ID generator for correlation across logs
    private static final AtomicLong REQUEST_ID_GENERATOR = new AtomicLong(0);

    private final VaultCryptoConfiguration config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * Creates a new VaultTransitClient with the specified configuration.
     *
     * <p>
     * This constructor initializes the HTTP client with connection pooling
     * and sets up the base URL for Vault API calls.
     * </p>
     *
     * @param config the Vault configuration containing connection details and
     *               settings
     * @throws IllegalArgumentException if configuration is null or invalid
     */
    public VaultTransitClient(VaultCryptoConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = config.getVaultUrl().replaceAll("/$", "") + "/v1/" + config.getTransitEnginePath();

        this.httpClient = HttpClient.newBuilder().connectTimeout(config.getConnectionTimeout()).build();

        logger.info(
            "VaultTransitClient initialized [baseUrl={}, transitEngine={}, keyPrefix={}, connectionTimeout={}ms, requestTimeout={}ms, maxRetries={}]",
            sanitizeUrl(config.getVaultUrl()),
            config.getTransitEnginePath(),
            config.getKeyPrefix(),
            config.getConnectionTimeout().toMillis(),
            config.getRequestTimeout().toMillis(),
            config.getMaxRetries()
        );
    }

    /**
     * Encrypts the given plaintext using the specified key name and encryption
     * context.
     *
     * <p>
     * This method ensures the key exists in Vault (creating it if necessary) before
     * performing the encryption operation. The encryption context provides
     * additional
     * authenticated data for the encryption operation.
     * </p>
     *
     * @param keyName   the name of the key in Vault (must not be null or empty)
     * @param plaintext the data to encrypt (must not be null or empty)
     * @param context   the encryption context for additional security (may be null)
     * @return a CompletableFuture containing the encrypted data as bytes
     * @throws IllegalArgumentException     if keyName or plaintext is null or empty
     * @throws VaultAuthenticationException if Vault authentication fails
     * @throws VaultConnectivityException   if Vault is unreachable
     * @throws VaultCryptoException         if encryption operation fails
     */
    public CompletableFuture<byte[]> encrypt(String keyName, byte[] plaintext, String context) {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

        if (keyName == null || keyName.trim().isEmpty()) {
            String errorMsg = String.format("Key name cannot be null or empty [requestId=%d]", requestId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        if (plaintext == null || plaintext.length == 0) {
            String errorMsg = String.format(
                "Plaintext cannot be null or empty [requestId=%d, keyName=%s]",
                requestId,
                keyName
            );
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        logger.debug(
            "Starting encryption operation [requestId={}, keyName={}, plaintextSize={}, contextLength={}]",
            requestId,
            keyName,
            plaintext.length,
            context != null ? context.length() : 0
        );

        return ensureKeyExists(keyName)
            .thenCompose(ignored -> performEncrypt(keyName, plaintext, context, requestId))
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Encryption operation failed [requestId={}, keyName={}, error={}]",
                        requestId,
                        keyName,
                        throwable.getMessage(),
                        throwable
                    );
                } else {
                    logger.debug(
                        "Encryption operation completed successfully [requestId={}, keyName={}, ciphertextSize={}]",
                        requestId,
                        keyName,
                        result != null ? result.length : 0
                    );
                }
            })
            .exceptionally(throwable -> {
                // Unwrap CompletionException if present
                Throwable actualThrowable = throwable;
                if (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null) {
                    actualThrowable = throwable.getCause();
                }

                logger.error(
                    "Network error during encryption [requestId={}, keyName={}, errorType={}, error={}]",
                    requestId,
                    keyName,
                    actualThrowable.getClass().getSimpleName(),
                    sanitizeLogMessage(actualThrowable.getMessage()),
                    actualThrowable
                );

                // Preserve the original exception type and chain properly
                if (actualThrowable instanceof RuntimeException) {
                    throw (RuntimeException) actualThrowable;
                } else if (actualThrowable instanceof IOException) {
                    throw new VaultConnectivityException(
                        String.format("Network error during encryption using key '%s'", keyName),
                        actualThrowable
                    );
                } else {
                    throw new VaultCryptoException(
                        String.format("Failed to encrypt data using key '%s'", keyName),
                        actualThrowable
                    );
                }
            });
    }

    /**
     * Decrypts the given ciphertext using the specified key name and encryption
     * context.
     *
     * <p>
     * The encryption context must match the context used during encryption.
     * If the key doesn't exist in Vault, the operation will fail with
     * {@link SubjectKeyNotFoundException}.
     * </p>
     *
     * @param keyName    the name of the key in Vault (must not be null or empty)
     * @param ciphertext the data to decrypt (must not be null or empty)
     * @param context    the encryption context used during encryption (may be null)
     * @return a CompletableFuture containing the decrypted data as bytes
     * @throws IllegalArgumentException     if keyName or ciphertext is null or
     *                                      empty
     * @throws SubjectKeyNotFoundException  if the key doesn't exist in Vault
     * @throws VaultAuthenticationException if Vault authentication fails
     * @throws VaultConnectivityException   if Vault is unreachable
     * @throws VaultCryptoException         if decryption operation fails
     */
    public CompletableFuture<byte[]> decrypt(String keyName, byte[] ciphertext, String context) {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

        if (keyName == null || keyName.trim().isEmpty()) {
            String errorMsg = String.format("Key name cannot be null or empty [requestId=%d]", requestId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        if (ciphertext == null || ciphertext.length == 0) {
            String errorMsg = String.format(
                "Ciphertext cannot be null or empty [requestId=%d, keyName=%s]",
                requestId,
                keyName
            );
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        logger.debug(
            "Starting decryption operation [requestId={}, keyName={}, ciphertextSize={}, contextLength={}]",
            requestId,
            keyName,
            ciphertext.length,
            context != null ? context.length() : 0
        );

        return performDecrypt(keyName, ciphertext, context, requestId)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Decryption operation failed [requestId={}, keyName={}, error={}]",
                        requestId,
                        keyName,
                        throwable.getMessage(),
                        throwable
                    );
                } else {
                    logger.debug(
                        "Decryption operation completed successfully [requestId={}, keyName={}, plaintextSize={}]",
                        requestId,
                        keyName,
                        result != null ? result.length : 0
                    );
                }
            });
    }

    /**
     * Ensures that the specified key exists in Vault, creating it if necessary.
     *
     * <p>
     * This method first checks if the key exists and creates it only if needed.
     * Key creation is idempotent - if multiple threads attempt to create the same
     * key simultaneously, only one will succeed and others will continue normally.
     * </p>
     *
     * @param keyName the name of the key to ensure exists (must not be null or
     *                empty)
     * @return a CompletableFuture that completes when the key is confirmed to exist
     * @throws IllegalArgumentException     if keyName is null or empty
     * @throws VaultAuthenticationException if Vault authentication fails
     * @throws VaultConnectivityException   if Vault is unreachable
     * @throws VaultCryptoException         if key creation fails
     */
    public CompletableFuture<Void> ensureKeyExists(String keyName) {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

        if (keyName == null || keyName.trim().isEmpty()) {
            String errorMsg = String.format("Key name cannot be null or empty [requestId=%d]", requestId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        logger.debug("Ensuring key exists [requestId={}, keyName={}]", requestId, keyName);

        return checkKeyExists(keyName, requestId)
            .thenCompose(exists -> {
                if (exists) {
                    logger.debug("Key already exists [requestId={}, keyName={}]", requestId, keyName);
                    return CompletableFuture.completedFuture(null);
                } else {
                    logger.debug("Creating new key [requestId={}, keyName={}]", requestId, keyName);
                    return createKey(keyName, requestId);
                }
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Key existence check/creation failed [requestId={}, keyName={}, error={}]",
                        requestId,
                        keyName,
                        throwable.getMessage(),
                        throwable
                    );
                }
            });
    }

    /**
     * Generates the full key name including the configured prefix and subject
     * pattern.
     *
     * <p>
     * The generated key name follows the pattern:
     * {@code {keyPrefix}/subject/{subjectId}}.
     * The subject ID is sanitized to prevent path traversal attacks by replacing
     * non-alphanumeric characters (except underscore and hyphen) with underscores.
     * </p>
     *
     * @param subjectId the subject identifier (must not be null or empty)
     * @return the full key name for Vault
     * @throws IllegalArgumentException if subjectId is null or empty
     */
    public String generateKeyName(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            String errorMsg = "Subject ID cannot be null or empty";
            logger.error("Key name generation failed: {}", errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Sanitize subject ID to prevent path traversal
        String sanitizedSubjectId = subjectId.replaceAll("[^a-zA-Z0-9_-]", "_");
        String keyName = config.getKeyPrefix() + "_subject_" + sanitizedSubjectId;

        if (!sanitizedSubjectId.equals(subjectId)) {
            logger.debug(
                "Subject ID was sanitized [original={}, sanitized={}, keyName={}]",
                subjectId,
                sanitizedSubjectId,
                keyName
            );
        }

        return keyName;
    }

    /**
     * Deletes the subject-specific key from Vault to support GDPR
     * right-to-be-forgotten.
     * Once a key is deleted, all data encrypted with that key becomes
     * unrecoverable.
     *
     * @param subjectId the subject identifier whose key should be deleted
     * @return a CompletableFuture that completes when the key is successfully
     *         deleted
     * @throws IllegalArgumentException    if subjectId is null or empty
     * @throws SubjectKeyNotFoundException if the subject's key is not found in
     *                                     Vault
     */
    public CompletableFuture<Void> deleteSubjectKey(String subjectId) {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

        if (subjectId == null || subjectId.trim().isEmpty()) {
            String errorMsg = String.format("Subject ID cannot be null or empty [requestId=%d]", requestId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        String keyName = generateKeyName(subjectId);
        logger.info(
            "Deleting subject key for GDPR compliance [requestId={}, subjectId={}, keyName={}]",
            requestId,
            subjectId,
            keyName
        );

        return checkKeyExists(keyName, requestId)
            .thenCompose(exists -> {
                if (!exists) {
                    String errorMsg = String.format(
                        "Subject key not found for deletion [requestId=%d, subjectId=%s, keyName=%s]",
                        requestId,
                        subjectId,
                        keyName
                    );
                    logger.warn(errorMsg);
                    return CompletableFuture.failedFuture(new SubjectKeyNotFoundException(subjectId, errorMsg));
                }
                return performKeyDeletion(keyName, requestId);
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Failed to delete subject key [requestId={}, subjectId={}, keyName={}, error={}]",
                        requestId,
                        subjectId,
                        keyName,
                        throwable.getMessage(),
                        throwable
                    );
                } else {
                    logger.info(
                        "Successfully deleted subject key [requestId={}, subjectId={}, keyName={}]",
                        requestId,
                        subjectId,
                        keyName
                    );
                }
            });
    }

    /**
     * Checks if a subject-specific key exists in Vault.
     * This method is useful for GDPR compliance to verify key existence before
     * operations.
     *
     * @param subjectId the subject identifier to check
     * @return a CompletableFuture containing true if the key exists, false
     *         otherwise
     * @throws IllegalArgumentException if subjectId is null or empty
     */
    public CompletableFuture<Boolean> subjectKeyExists(String subjectId) {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

        if (subjectId == null || subjectId.trim().isEmpty()) {
            String errorMsg = String.format("Subject ID cannot be null or empty [requestId=%d]", requestId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        String keyName = generateKeyName(subjectId);
        logger.debug(
            "Checking subject key existence [requestId={}, subjectId={}, keyName={}]",
            requestId,
            subjectId,
            keyName
        );

        return checkKeyExists(keyName, requestId)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Failed to check subject key existence [requestId={}, subjectId={}, keyName={}, error={}]",
                        requestId,
                        subjectId,
                        keyName,
                        throwable.getMessage(),
                        throwable
                    );
                } else {
                    logger.debug(
                        "Subject key existence check completed [requestId={}, subjectId={}, exists={}]",
                        requestId,
                        subjectId,
                        result
                    );
                }
            });
    }

    /**
     * Lists all subject keys managed by this provider.
     * This method is useful for GDPR compliance auditing and bulk operations.
     * Note: This is a potentially expensive operation and should be used carefully.
     *
     * @return a CompletableFuture containing a list of subject IDs that have keys
     *         in Vault
     */
    public CompletableFuture<java.util.List<String>> listSubjectKeys() {
        long requestId = REQUEST_ID_GENERATOR.incrementAndGet();

        logger.debug("Listing all subject keys [requestId={}]", requestId);

        String listUrl = baseUrl + "/keys";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("list", true);

        return executeWithRetry(
                () -> {
                    try {
                        logger.debug(
                            "Sending list keys request to Vault [requestId={}, url={}]",
                            requestId,
                            sanitizeUrl(listUrl)
                        );

                        HttpRequest request = HttpRequest
                            .newBuilder()
                            .uri(URI.create(listUrl))
                            .header(VAULT_TOKEN_HEADER, config.getVaultToken())
                            .header("Content-Type", CONTENT_TYPE_JSON)
                            .timeout(config.getRequestTimeout())
                            .method("LIST", HttpRequest.BodyPublishers.noBody())
                            .build();

                        return httpClient
                            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(response -> {
                                logger.debug(
                                    "Received list keys response from Vault [requestId={}, statusCode={}, responseSize={}]",
                                    requestId,
                                    response.statusCode(),
                                    response.body().length()
                                );

                                if (response.statusCode() == 404) {
                                    // No keys exist yet
                                    logger.debug("No keys found in Vault [requestId={}]", requestId);
                                    return java.util.Collections.<String>emptyList();
                                }

                                handleHttpResponse(response, "list keys", requestId);
                                return parseSubjectKeysFromListResponse(response.body(), requestId);
                            });
                    } catch (Exception e) {
                        String errorMsg = String.format("Failed to list keys [requestId=%d]", requestId);
                        logger.error(errorMsg, e);
                        return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                    }
                },
                requestId
            )
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Failed to list subject keys [requestId={}, error={}]",
                        requestId,
                        throwable.getMessage(),
                        throwable
                    );
                } else {
                    logger.debug(
                        "Successfully listed subject keys [requestId={}, count={}]",
                        requestId,
                        result != null ? result.size() : 0
                    );
                }
            });
    }

    private CompletableFuture<byte[]> performEncrypt(String keyName, byte[] plaintext, String context, long requestId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("plaintext", Base64.getEncoder().encodeToString(plaintext));

        if (context != null && !context.isEmpty()) {
            requestBody.put("context", Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8)));
        }

        String url = baseUrl + "/encrypt/" + keyName;

        return executeWithRetry(
            () -> {
                try {
                    String jsonBody = objectMapper.writeValueAsString(requestBody);
                    logger.debug(
                        "Sending encrypt request to Vault [requestId={}, url={}, bodySize={}]",
                        requestId,
                        sanitizeUrl(url),
                        jsonBody.length()
                    );

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
                            logger.debug(
                                "Received encrypt response from Vault [requestId={}, statusCode={}, responseSize={}]",
                                requestId,
                                response.statusCode(),
                                response.body().length()
                            );
                            handleHttpResponse(response, "encrypt", requestId);
                            return parseCiphertext(response.body(), requestId);
                        });
                } catch (Exception e) {
                    String errorMsg = String.format(
                        "Failed to encrypt data [requestId=%d, keyName=%s]",
                        requestId,
                        keyName
                    );
                    logger.error(errorMsg, e);
                    return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                }
            },
            requestId
        );
    }

    private CompletableFuture<byte[]> performDecrypt(
        String keyName,
        byte[] ciphertext,
        String context,
        long requestId
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ciphertext", new String(ciphertext, StandardCharsets.UTF_8));

        if (context != null && !context.isEmpty()) {
            requestBody.put("context", Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8)));
        }

        String url = baseUrl + "/decrypt/" + keyName;

        return executeWithRetry(
            () -> {
                try {
                    String jsonBody = objectMapper.writeValueAsString(requestBody);
                    logger.debug(
                        "Sending decrypt request to Vault [requestId={}, url={}, bodySize={}]",
                        requestId,
                        sanitizeUrl(url),
                        jsonBody.length()
                    );

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
                            logger.debug(
                                "Received decrypt response from Vault [requestId={}, statusCode={}, responseSize={}]",
                                requestId,
                                response.statusCode(),
                                response.body().length()
                            );
                            handleHttpResponse(response, "decrypt", requestId);
                            return parsePlaintext(response.body(), requestId);
                        });
                } catch (Exception e) {
                    String errorMsg = String.format(
                        "Failed to decrypt data [requestId=%d, keyName=%s]",
                        requestId,
                        keyName
                    );
                    logger.error(errorMsg, e);
                    return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                }
            },
            requestId
        );
    }

    CompletableFuture<Boolean> checkKeyExists(String keyName, long requestId) {
        String url = baseUrl + "/keys/" + keyName;

        return executeWithRetry(
            () -> {
                try {
                    logger.debug("Checking key existence [requestId={}, url={}]", requestId, sanitizeUrl(url));

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
                            logger.debug(
                                "Key existence check response [requestId={}, statusCode={}]",
                                requestId,
                                response.statusCode()
                            );

                            if (response.statusCode() == 200) {
                                return true;
                            } else if (response.statusCode() == 404) {
                                return false;
                            } else {
                                handleHttpResponse(response, "check key existence", requestId);
                                return false; // This line should not be reached due to exception in
                                // handleHttpResponse
                            }
                        });
                } catch (Exception e) {
                    String errorMsg = String.format(
                        "Failed to check key existence [requestId=%d, keyName=%s]",
                        requestId,
                        keyName
                    );
                    logger.error(errorMsg, e);
                    return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                }
            },
            requestId
        );
    }

    private CompletableFuture<Void> createKey(String keyName, long requestId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("type", "aes256-gcm96");

        String url = baseUrl + "/keys/" + keyName;

        return executeWithRetry(
                () -> {
                    try {
                        String jsonBody = objectMapper.writeValueAsString(requestBody);
                        logger.debug(
                            "Creating key in Vault [requestId={}, url={}, keyType={}]",
                            requestId,
                            sanitizeUrl(url),
                            requestBody.get("type")
                        );

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
                                logger.debug(
                                    "Key creation response [requestId={}, statusCode={}]",
                                    requestId,
                                    response.statusCode()
                                );

                                handleHttpResponse(response, "create key", requestId);
                                return null;
                            });
                    } catch (Exception e) {
                        String errorMsg = String.format(
                            "Failed to create key [requestId=%d, keyName=%s]",
                            requestId,
                            keyName
                        );
                        logger.error(errorMsg, e);
                        return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                    }
                },
                requestId
            )
            .thenCompose(result -> enableKeyDeletion(keyName, requestId));
    }

    private CompletableFuture<Void> enableKeyDeletion(String keyName, long requestId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("deletion_allowed", true);

        String url = baseUrl + "/keys/" + keyName + "/config";

        return executeWithRetry(
            () -> {
                try {
                    String jsonBody = objectMapper.writeValueAsString(requestBody);
                    logger.debug(
                        "Enabling key deletion in Vault [requestId={}, url={}, deletionAllowed={}]",
                        requestId,
                        sanitizeUrl(url),
                        requestBody.get("deletion_allowed")
                    );

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
                            logger.debug(
                                "Key deletion enablement response [requestId={}, statusCode={}]",
                                requestId,
                                response.statusCode()
                            );

                            handleHttpResponse(response, "enable key deletion", requestId);
                            return null;
                        });
                } catch (Exception e) {
                    String errorMsg = String.format(
                        "Failed to enable key deletion [requestId=%d, keyName=%s]",
                        requestId,
                        keyName
                    );
                    logger.error(errorMsg, e);
                    return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                }
            },
            requestId
        );
    }

    /**
     * Performs the actual key deletion operation in Vault.
     *
     * @param keyName   the full key name to delete
     * @param requestId the request ID for logging correlation
     * @return a CompletableFuture that completes when the key is deleted
     */
    private CompletableFuture<Void> performKeyDeletion(String keyName, long requestId) {
        String url = baseUrl + "/keys/" + keyName;

        return executeWithRetry(
            () -> {
                try {
                    logger.debug("Deleting key in Vault [requestId={}, url={}]", requestId, sanitizeUrl(url));

                    HttpRequest request = HttpRequest
                        .newBuilder()
                        .uri(URI.create(url))
                        .header(VAULT_TOKEN_HEADER, config.getVaultToken())
                        .timeout(config.getRequestTimeout())
                        .DELETE()
                        .build();

                    return httpClient
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            logger.debug(
                                "Key deletion response [requestId={}, statusCode={}]",
                                requestId,
                                response.statusCode()
                            );

                            handleHttpResponse(response, "delete key", requestId);
                            return null;
                        });
                } catch (Exception e) {
                    String errorMsg = String.format(
                        "Failed to delete key [requestId=%d, keyName=%s]",
                        requestId,
                        keyName
                    );
                    logger.error(errorMsg, e);
                    return CompletableFuture.failedFuture(new VaultConnectivityException(errorMsg, e));
                }
            },
            requestId
        );
    }

    /**
     * Parses the Vault list keys response and extracts subject IDs.
     *
     * @param responseBody the JSON response from Vault's list keys API
     * @param requestId    the request ID for logging correlation
     * @return a list of subject IDs extracted from the key names
     */
    private java.util.List<String> parseSubjectKeysFromListResponse(String responseBody, long requestId) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = responseNode.get("data");

            if (dataNode == null || !dataNode.has("keys")) {
                logger.debug("No keys found in list response [requestId={}]", requestId);
                return java.util.Collections.emptyList();
            }

            JsonNode keysNode = dataNode.get("keys");
            if (!keysNode.isArray()) {
                logger.warn("Keys node is not an array in list response [requestId={}]", requestId);
                return java.util.Collections.emptyList();
            }

            java.util.List<String> subjectIds = new java.util.ArrayList<>();
            String subjectPrefix = config.getKeyPrefix() + "_subject_";

            for (JsonNode keyNode : keysNode) {
                String keyName = keyNode.asText();
                if (keyName.startsWith(subjectPrefix)) {
                    String subjectId = keyName.substring(subjectPrefix.length());
                    // Reverse the sanitization if needed (though this is best-effort)
                    subjectIds.add(subjectId);
                    logger.debug(
                        "Found subject key [requestId={}, keyName={}, subjectId={}]",
                        requestId,
                        keyName,
                        subjectId
                    );
                }
            }

            logger.debug(
                "Parsed subject keys from list response [requestId={}, totalKeys={}, subjectKeys={}]",
                requestId,
                keysNode.size(),
                subjectIds.size()
            );
            return subjectIds;
        } catch (IOException e) {
            String errorMsg = String.format("Failed to parse list keys response [requestId=%d]", requestId);
            logger.error(errorMsg, e);
            throw new VaultCryptoException(errorMsg, e);
        }
    }

    private <T> CompletableFuture<T> executeWithRetry(
        java.util.function.Supplier<CompletableFuture<T>> operation,
        long requestId
    ) {
        return executeWithRetry(operation, 0, requestId);
    }

    private <T> CompletableFuture<T> executeWithRetry(
        java.util.function.Supplier<CompletableFuture<T>> operation,
        int attempt,
        long requestId
    ) {
        return operation
            .get()
            .handle((result, throwable) -> {
                if (throwable == null) {
                    return CompletableFuture.completedFuture(result);
                }

                if (attempt >= config.getMaxRetries()) {
                    logger.error(
                        "Max retries exceeded, failing operation [requestId={}, maxRetries={}, errorType={}, finalError={}]",
                        requestId,
                        config.getMaxRetries(),
                        throwable.getClass().getSimpleName(),
                        sanitizeLogMessage(throwable.getMessage()),
                        throwable
                    );
                    return CompletableFuture.<T>failedFuture(throwable);
                }

                if (isRetryableException(throwable)) {
                    long delay = calculateBackoffDelay(attempt);
                    logger.warn(
                        "Operation failed, retrying [requestId={}, attempt={}/{}, retryDelay={}ms, errorType={}, error={}]",
                        requestId,
                        attempt + 1,
                        config.getMaxRetries() + 1,
                        delay,
                        throwable.getClass().getSimpleName(),
                        sanitizeLogMessage(throwable.getMessage()),
                        throwable
                    );

                    // For testing, we'll use a simpler approach without actual delay
                    if (delay == 0) {
                        return executeWithRetry(operation, attempt + 1, requestId);
                    }

                    CompletableFuture<T> delayedRetry = new CompletableFuture<>();
                    java.util.concurrent.ScheduledExecutorService scheduler =
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

                    scheduler.schedule(
                        () -> {
                            try {
                                executeWithRetry(operation, attempt + 1, requestId)
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
                    logger.error(
                        "Non-retryable exception occurred [requestId={}, errorType={}, error={}]",
                        requestId,
                        throwable.getClass().getSimpleName(),
                        sanitizeLogMessage(throwable.getMessage()),
                        throwable
                    );
                    return CompletableFuture.<T>failedFuture(throwable);
                }
            })
            .thenCompose(future -> future);
    }

    private boolean isRetryableException(Throwable throwable) {
        // Retry on connectivity issues but not on authentication or validation errors
        boolean isRetryable =
            (throwable instanceof VaultConnectivityException ||
                (throwable instanceof IOException) ||
                (throwable.getCause() instanceof IOException));

        // Don't retry on authentication or validation errors
        if (throwable instanceof VaultAuthenticationException || throwable instanceof IllegalArgumentException) {
            isRetryable = false;
        }

        logger.debug(
            "Exception retry evaluation [exception={}, retryable={}]",
            throwable.getClass().getSimpleName(),
            isRetryable
        );

        return isRetryable;
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

    private void handleHttpResponse(HttpResponse<String> response, String operation, long requestId) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            return; // Success
        }

        String baseErrorMessage = String.format(
            "Vault %s operation failed [requestId=%d, statusCode=%d]",
            operation,
            requestId,
            statusCode
        );

        // Parse Vault error response for additional details
        String vaultErrorDetails = null;
        String detailedErrorMessage = baseErrorMessage;

        try {
            if (response.body() != null && !response.body().trim().isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(response.body());
                if (errorNode.has("errors") && errorNode.get("errors").isArray()) {
                    vaultErrorDetails = errorNode.get("errors").toString();
                    detailedErrorMessage = baseErrorMessage + " - Vault errors: " + vaultErrorDetails;
                }
            }
        } catch (Exception e) {
            logger.debug(
                "Could not parse error response body [requestId={}, responseBodyLength={}]",
                requestId,
                response.body() != null ? response.body().length() : 0,
                e
            );
        }

        // Log the error with appropriate level based on status code and throw specific
        // exceptions
        if (statusCode == 401 || statusCode == 403) {
            logger.error(
                "Vault authentication failed [requestId={}, statusCode={}, operation={}, vaultErrors={}]",
                requestId,
                statusCode,
                operation,
                vaultErrorDetails != null ? vaultErrorDetails : "none"
            );
            throw new VaultAuthenticationException(detailedErrorMessage);
        } else if (statusCode == 404) {
            logger.warn(
                "Vault resource not found [requestId={}, statusCode={}, operation={}, vaultErrors={}]",
                requestId,
                statusCode,
                operation,
                vaultErrorDetails != null ? vaultErrorDetails : "none"
            );
            throw new SubjectKeyNotFoundException("", detailedErrorMessage);
        } else if (statusCode >= 500) {
            logger.error(
                "Vault server error [requestId={}, statusCode={}, operation={}, vaultErrors={}]",
                requestId,
                statusCode,
                operation,
                vaultErrorDetails != null ? vaultErrorDetails : "none"
            );
            throw new VaultConnectivityException(detailedErrorMessage);
        } else {
            logger.error(
                "Vault client error [requestId={}, statusCode={}, operation={}, vaultErrors={}]",
                requestId,
                statusCode,
                operation,
                vaultErrorDetails != null ? vaultErrorDetails : "none"
            );
            throw new VaultCryptoException(detailedErrorMessage);
        }
    }

    private byte[] parseCiphertext(String responseBody, long requestId) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = responseNode.get("data");

            if (dataNode == null || !dataNode.has("ciphertext")) {
                String errorMsg = String.format(
                    "Invalid encryption response format: missing ciphertext [requestId=%d]",
                    requestId
                );
                logger.error(errorMsg);
                throw new VaultCryptoException(errorMsg);
            }

            String ciphertext = dataNode.get("ciphertext").asText();
            logger.debug(
                "Successfully parsed ciphertext from response [requestId={}, ciphertextLength={}]",
                requestId,
                ciphertext.length()
            );
            return ciphertext.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            String errorMsg = String.format("Failed to parse encryption response [requestId=%d]", requestId);
            logger.error(errorMsg, e);
            throw new VaultCryptoException(errorMsg, e);
        }
    }

    private byte[] parsePlaintext(String responseBody, long requestId) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = responseNode.get("data");

            if (dataNode == null || !dataNode.has("plaintext")) {
                String errorMsg = String.format(
                    "Invalid decryption response format: missing plaintext [requestId=%d]",
                    requestId
                );
                logger.error(errorMsg);
                throw new VaultCryptoException(errorMsg);
            }

            String plaintext = dataNode.get("plaintext").asText();
            byte[] decodedPlaintext = Base64.getDecoder().decode(plaintext);
            logger.debug(
                "Successfully parsed plaintext from response [requestId={}, plaintextSize={}]",
                requestId,
                decodedPlaintext.length
            );
            return decodedPlaintext;
        } catch (IOException e) {
            String errorMsg = String.format("Failed to parse decryption response [requestId=%d]", requestId);
            logger.error(errorMsg, e);
            throw new VaultCryptoException(errorMsg, e);
        } catch (IllegalArgumentException e) {
            String errorMsg = String.format("Failed to decode base64 plaintext [requestId=%d]", requestId);
            logger.error(errorMsg, e);
            throw new VaultCryptoException(errorMsg, e);
        }
    }

    /**
     * Sanitizes URLs for logging by removing sensitive information.
     * This ensures that tokens or other sensitive data in URLs are not logged.
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

        // Replace any potential tokens in the path (though this shouldn't happen with
        // Vault URLs)
        return url.replaceAll("token=[^&]*", "token=[REDACTED]");
    }

    /**
     * Sanitizes log messages to ensure no sensitive data is exposed.
     * This method removes or redacts potentially sensitive information from log
     * messages.
     *
     * @param message the message to sanitize
     * @return the sanitized message safe for logging
     */
    private String sanitizeLogMessage(String message) {
        if (message == null) {
            return "null";
        }

        // Remove base64-encoded data that might be keys or sensitive content
        String sanitized = message.replaceAll("[A-Za-z0-9+/]{32,}={0,2}", "[REDACTED_BASE64]");

        // Remove potential token patterns
        sanitized = sanitized.replaceAll("(?i)token[=:]\\s*[A-Za-z0-9._-]+", "token=[REDACTED]");

        // Remove potential key material patterns
        sanitized = sanitized.replaceAll("(?i)(key|secret|password)[=:]\\s*[A-Za-z0-9._-]+", "$1=[REDACTED]");

        return sanitized;
    }

    @Override
    public void close() {
        logger.info("Closing VaultTransitClient");
        try {
            // Cancel any pending operations and clean up resources
            // HttpClient in Java 11+ manages its own resources, but we should ensure
            // any scheduled retry operations are properly cancelled

            // Note: In a production implementation, we might want to track active
            // operations
            // and provide a graceful shutdown mechanism, but for now we rely on the
            // HttpClient's internal resource management

            logger.debug("VaultTransitClient closed successfully");
        } catch (Exception e) {
            logger.warn("Error during VaultTransitClient cleanup [error={}]", e.getMessage(), e);
            // Don't rethrow exceptions from close() method
        }
    }
}
