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

        logger.info("VaultDecryptingMaterialsProvider initialized with configuration: {}", config);
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
            return CompletableFuture.failedFuture(new IllegalArgumentException("Subject ID cannot be null or empty"));
        }

        if (encryptedDataKey == null || encryptedDataKey.length == 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Encrypted data key cannot be null or empty"));
        }

        if (encryptionContext == null || encryptionContext.trim().isEmpty()) {
            return CompletableFuture.failedFuture(
                new InvalidEncryptionContextException(encryptionContext, "Encryption context cannot be null or empty")
            );
        }

        logger.debug("Decrypting materials for subject: {}", subjectId);

        return CompletableFuture
            .supplyAsync(() -> validateEncryptionContext(subjectId, encryptionContext))
            .thenCompose(validatedContext -> decryptDataEncryptionKey(subjectId, encryptedDataKey, validatedContext))
            .thenApply(this::reconstructAeadPrimitive)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to decrypt materials for subject: {}", subjectId, throwable);
                } else {
                    logger.debug("Successfully decrypted materials for subject: {}", subjectId);
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
        logger.debug("Validating encryption context for subject: {}", expectedSubjectId);

        var matcher = ENCRYPTION_CONTEXT_PATTERN.matcher(encryptionContext);
        if (!matcher.matches()) {
            throw new InvalidEncryptionContextException(
                encryptionContext,
                "Encryption context format is invalid. Expected format: subjectId=value;timestamp=value;version=value"
            );
        }

        String contextSubjectId = matcher.group(1);
        String timestampStr = matcher.group(2);
        String version = matcher.group(3);

        // Validate subject ID matches
        if (!expectedSubjectId.equals(contextSubjectId)) {
            throw new InvalidEncryptionContextException(
                encryptionContext,
                String.format("Subject ID mismatch. Expected: %s, Found in context: %s", expectedSubjectId, contextSubjectId)
            );
        }

        // Validate timestamp is a valid number
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            throw new InvalidEncryptionContextException(
                encryptionContext,
                "Timestamp cannot be empty in encryption context"
            );
        }
        
        try {
            Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new InvalidEncryptionContextException(
                encryptionContext,
                "Invalid timestamp format in encryption context: " + timestampStr
            );
        }

        // Validate version (currently just check it's not empty)
        if (version == null || version.trim().isEmpty()) {
            throw new InvalidEncryptionContextException(
                encryptionContext,
                "Version cannot be empty in encryption context"
            );
        }

        logger.debug("Encryption context validation successful for subject: {}", expectedSubjectId);
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

        logger.debug("Decrypting DEK with Vault key: {} (context: {})", keyName, encryptionContext);

        return vaultClient
            .decrypt(keyName, encryptedDataKey, encryptionContext)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to decrypt DEK for subject: {}", subjectId, throwable);
                    
                    // Convert specific Vault exceptions to more meaningful ones for decryption context
                    if (throwable instanceof SubjectKeyNotFoundException) {
                        // Re-throw with proper subject ID
                        throw new SubjectKeyNotFoundException(
                            subjectId,
                            String.format("Subject key not found for subject: %s", subjectId),
                            throwable
                        );
                    }
                } else {
                    logger.debug("Successfully decrypted DEK for subject: {}", subjectId);
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
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validateConfiguration(VaultCryptoConfiguration config) {
        // Additional validation beyond what's done in the configuration builder
        
        // Validate URL format
        String vaultUrl = config.getVaultUrl();
        if (!vaultUrl.startsWith("http://") && !vaultUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Vault URL must start with http:// or https://");
        }

        // Validate token is not just whitespace
        String token = config.getVaultToken();
        if (token.trim().length() != token.length()) {
            throw new IllegalArgumentException("Vault token cannot contain leading or trailing whitespace");
        }

        // Validate key prefix doesn't contain invalid characters
        String keyPrefix = config.getKeyPrefix();
        if (!keyPrefix.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Key prefix can only contain alphanumeric characters, underscores, and hyphens");
        }

        // Validate timeout values are reasonable
        if (config.getConnectionTimeout().toMillis() > 300000) { // 5 minutes
            throw new IllegalArgumentException("Connection timeout cannot exceed 5 minutes");
        }

        if (config.getRequestTimeout().toMillis() > 600000) { // 10 minutes
            throw new IllegalArgumentException("Request timeout cannot exceed 10 minutes");
        }

        logger.debug("Configuration validation successful");
    }

    @Override
    public void close() {
        logger.info("Closing VaultDecryptingMaterialsProvider");
        try {
            if (vaultClient != null) {
                vaultClient.close();
            }
        } catch (Exception e) {
            logger.warn("Error closing VaultTransitClient", e);
        }
    }
}