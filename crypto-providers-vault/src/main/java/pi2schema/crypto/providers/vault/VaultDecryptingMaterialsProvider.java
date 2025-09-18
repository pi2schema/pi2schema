package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;

import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Implementation of DecryptingMaterialsProvider that uses HashiCorp Vault's transit encryption
 * to decrypt Key Encryption Keys (KEKs) for subject-specific data decryption.
 *
 * This provider decrypts encrypted Data Encryption Keys (DEKs) using Vault's transit engine
 * with subject-specific keys and validates encryption context for GDPR compliance.
 */
public class VaultDecryptingMaterialsProvider implements DecryptingMaterialsProvider {

    private static final Logger logger = LoggerFactory.getLogger(VaultDecryptingMaterialsProvider.class);
    private static final String PROVIDER_VERSION = "1.0";
    
    // Pattern for validating encryption context format: subjectId=value;timestamp=value;version=value
    private static final Pattern ENCRYPTION_CONTEXT_PATTERN = Pattern.compile(
        "^subjectId=([^;]+);timestamp=([^;]+);version=([^;]*)$"
    );

    private final VaultTransitClient vaultClient;
    private final VaultCryptoConfiguration config;

    static {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to register Tink AEAD configuration", e);
        }
    }

    /**
     * Creates a new VaultDecryptingMaterialsProvider with the specified configuration.
     *
     * @param config the Vault configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public VaultDecryptingMaterialsProvider(VaultCryptoConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        // Validate configuration during initialization
        validateConfiguration(config);

        this.config = config;
        this.vaultClient = new VaultTransitClient(config);

        logger.info("VaultDecryptingMaterialsProvider initialized [vaultUrl={}, transitEngine={}, keyPrefix={}]", 
                   sanitizeUrl(config.getVaultUrl()), config.getTransitEnginePath(), config.getKeyPrefix());
    }

    /**
     * Decrypts the provided encrypted DEK using the subject's KEK and returns
     * the ready-to-use DEK as an Aead primitive.
     *
     * This method:
     * 1. Validates the encryption context format and subject ID
     * 2. Decrypts the encrypted DEK using Vault's transit engine with the subject-specific key
     * 3. Reconstructs the Tink AEAD primitive from the decrypted DEK bytes
     *
     * @param subjectId the subject identifier to locate the appropriate KEK
     * @param encryptedDataKey the encrypted DEK to decrypt
     * @param encryptionContext context or metadata from encryption
     * @return CompletableFuture containing the Aead primitive with the decrypted DEK
     * @throws IllegalArgumentException if subjectId is null or empty
     * @throws InvalidEncryptionContextException if encryption context is invalid
     * @throws SubjectKeyNotFoundException if the subject's key is not found in Vault
     */
    @Override
    public CompletableFuture<Aead> decryptionKeysFor(String subjectId, byte[] encryptedDataKey, String encryptionContext) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            String errorMsg = "Subject ID cannot be null or empty";
            logger.error("Decryption materials request failed: {}", errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        if (encryptedDataKey == null || encryptedDataKey.length == 0) {
            String errorMsg = String.format("Encrypted data key cannot be null or empty [subjectId=%s]", subjectId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        if (encryptionContext == null || encryptionContext.trim().isEmpty()) {
            String errorMsg = String.format("Encryption context cannot be null or empty [subjectId=%s]", subjectId);
            logger.error(errorMsg);
            return CompletableFuture.failedFuture(
                new InvalidEncryptionContextException(encryptionContext, errorMsg)
            );
        }

        logger.debug("Decrypting materials [subjectId={}, encryptedKeySize={}, contextLength={}]", 
                    subjectId, encryptedDataKey.length, encryptionContext.length());

        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    return validateEncryptionContext(subjectId, encryptionContext);
                } catch (Exception e) {
                    logger.error("Encryption context validation failed [subjectId={}, error={}]", 
                               subjectId, e.getMessage(), e);
                    throw e;
                }
            })
            .thenCompose(validatedContext -> decryptDataEncryptionKey(subjectId, encryptedDataKey, validatedContext))
            .thenApply(dekBytes -> {
                try {
                    return reconstructAeadPrimitive(dekBytes);
                } catch (Exception e) {
                    logger.error("AEAD primitive reconstruction failed [subjectId={}, error={}]", 
                               subjectId, e.getMessage(), e);
                    throw e;
                }
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to decrypt materials [subjectId={}, error={}]", 
                               subjectId, throwable.getMessage(), throwable);
                } else {
                    logger.debug("Successfully decrypted materials [subjectId={}]", subjectId);
                }
            });
    }

    /**
     * Validates the encryption context format and ensures the subject ID matches.
     *
     * @param expectedSubjectId the expected subject ID
     * @param encryptionContext the encryption context to validate
     * @return the validated encryption context
     * @throws InvalidEncryptionContextException if validation fails
     */
    private String validateEncryptionContext(String expectedSubjectId, String encryptionContext) {
        logger.debug("Validating encryption context [subjectId={}, contextLength={}]", 
                    expectedSubjectId, encryptionContext.length());

        var matcher = ENCRYPTION_CONTEXT_PATTERN.matcher(encryptionContext);
        if (!matcher.matches()) {
            String errorMsg = String.format(
                "Encryption context format is invalid [subjectId=%s, expectedFormat=subjectId=value;timestamp=value;version=value]", 
                expectedSubjectId
            );
            logger.error(errorMsg);
            throw new InvalidEncryptionContextException(encryptionContext, errorMsg);
        }

        String contextSubjectId = matcher.group(1);
        String timestampStr = matcher.group(2);
        String version = matcher.group(3);

        // Validate subject ID matches
        if (!expectedSubjectId.equals(contextSubjectId)) {
            String errorMsg = String.format(
                "Subject ID mismatch [expected=%s, foundInContext=%s]", 
                expectedSubjectId, contextSubjectId
            );
            logger.error(errorMsg);
            throw new InvalidEncryptionContextException(encryptionContext, errorMsg);
        }

        // Validate timestamp is a valid number
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            String errorMsg = String.format(
                "Timestamp cannot be empty in encryption context [subjectId=%s]", 
                expectedSubjectId
            );
            logger.error(errorMsg);
            throw new InvalidEncryptionContextException(encryptionContext, errorMsg);
        }
        
        try {
            long timestamp = Long.parseLong(timestampStr);
            logger.debug("Encryption context timestamp validated [subjectId={}, timestamp={}]", 
                        expectedSubjectId, timestamp);
        } catch (NumberFormatException e) {
            String errorMsg = String.format(
                "Invalid timestamp format in encryption context [subjectId=%s, timestamp=%s]", 
                expectedSubjectId, timestampStr
            );
            logger.error(errorMsg, e);
            throw new InvalidEncryptionContextException(encryptionContext, errorMsg);
        }

        // Validate version (currently just check it's not empty)
        if (version == null || version.trim().isEmpty()) {
            String errorMsg = String.format(
                "Version cannot be empty in encryption context [subjectId=%s]", 
                expectedSubjectId
            );
            logger.error(errorMsg);
            throw new InvalidEncryptionContextException(encryptionContext, errorMsg);
        }

        logger.debug("Encryption context validation successful [subjectId={}, version={}]", 
                    expectedSubjectId, version);
        return encryptionContext;
    }

    /**
     * Decrypts the encrypted Data Encryption Key using Vault's transit engine.
     *
     * @param subjectId the subject identifier
     * @param encryptedDataKey the encrypted DEK to decrypt
     * @param encryptionContext the validated encryption context
     * @return a CompletableFuture containing the decrypted DEK bytes
     */
    private CompletableFuture<byte[]> decryptDataEncryptionKey(
        String subjectId,
        byte[] encryptedDataKey,
        String encryptionContext
    ) {
        String keyName = vaultClient.generateKeyName(subjectId);

        logger.debug("Decrypting DEK with Vault [subjectId={}, keyName={}, encryptedKeySize={}]", 
                    subjectId, keyName, encryptedDataKey.length);

        return vaultClient
            .decrypt(keyName, encryptedDataKey, encryptionContext)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    // Unwrap CompletionException if present for better error reporting
                    Throwable actualThrowable = throwable;
                    if (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null) {
                        actualThrowable = throwable.getCause();
                    }
                    
                    logger.error("Failed to decrypt DEK [subjectId={}, keyName={}, errorType={}, error={}]", 
                               subjectId, keyName, actualThrowable.getClass().getSimpleName(), 
                               sanitizeLogMessage(actualThrowable.getMessage()), actualThrowable);

                    
                    // Convert specific Vault exceptions to more meaningful ones for decryption context
                    if (actualThrowable instanceof SubjectKeyNotFoundException) {
                        // Re-throw with proper subject ID
                        throw new SubjectKeyNotFoundException(
                            subjectId,
                            String.format("Subject key not found for subject: %s", subjectId),
                            actualThrowable
                        );
                    }
                } else {
                    logger.debug("Successfully decrypted DEK [subjectId={}, dekSize={}]", 
                               subjectId, result != null ? result.length : 0);
                }
            });
    }

    /**
     * Reconstructs a Tink AEAD primitive from the decrypted DEK bytes.
     *
     * @param dekBytes the decrypted DEK bytes
     * @return the Aead primitive ready for use
     * @throws VaultCryptoException if the AEAD primitive cannot be created
     */
    private Aead reconstructAeadPrimitive(byte[] dekBytes) {
        try {
            logger.debug("Reconstructing AEAD primitive from DEK (key size: {} bytes)", dekBytes.length);

            // Validate key size (should be 32 bytes for AES-256)
            if (dekBytes.length != 32) {
                throw new VaultCryptoException(
                    String.format("Invalid DEK size. Expected 32 bytes, got %d bytes", dekBytes.length)
                );
            }

            // Create AEAD primitive from the decrypted key bytes
            Aead aead = new AesGcmJce(dekBytes);

            logger.debug("Successfully reconstructed AEAD primitive");
            return aead;
        } catch (GeneralSecurityException e) {
            throw new VaultCryptoException("Failed to reconstruct AEAD primitive from decrypted DEK", e);
        }
    }

    /**
     * Validates the configuration to ensure all required parameters are properly set.
     * Note: Most validation is done in VaultCryptoConfiguration.Builder, this is for
     * additional runtime validation if needed.
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validateConfiguration(VaultCryptoConfiguration config) {
        // Configuration validation is primarily handled in the builder
        // This method is kept for any additional runtime validation needs
        logger.debug("Configuration validation successful");
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

    /**
     * Sanitizes log messages to ensure no sensitive data is exposed.
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
        logger.info("Closing VaultDecryptingMaterialsProvider");
        try {
            if (vaultClient != null) {
                vaultClient.close();
                logger.debug("VaultTransitClient closed successfully");
            }
        } catch (Exception e) {
            logger.warn("Error closing VaultTransitClient [error={}]", e.getMessage(), e);
        }
    }
}