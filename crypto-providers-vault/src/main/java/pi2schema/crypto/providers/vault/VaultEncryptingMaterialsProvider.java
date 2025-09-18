package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of EncryptingMaterialsProvider that uses HashiCorp Vault's transit encryption
 * to manage Key Encryption Keys (KEKs) for subject-specific data encryption.
 *
 * This provider generates Data Encryption Keys (DEKs) using Tink's AEAD primitive and encrypts
 * them using Vault's transit engine with subject-specific keys for GDPR compliance.
 */
public class VaultEncryptingMaterialsProvider implements EncryptingMaterialsProvider {

    private static final Logger logger = LoggerFactory.getLogger(VaultEncryptingMaterialsProvider.class);
    private static final String PROVIDER_VERSION = "1.0";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     * Creates a new VaultEncryptingMaterialsProvider with the specified configuration.
     *
     * @param config the Vault configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    public VaultEncryptingMaterialsProvider(VaultCryptoConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        // Validate configuration during initialization
        validateConfiguration(config);

        this.config = config;
        this.vaultClient = new VaultTransitClient(config);

        logger.info("VaultEncryptingMaterialsProvider initialized");
    }

    /**
     * Generates encryption materials for the specified subject.
     *
     * This method:
     * 1. Generates a new DEK using Tink's AEAD primitive
     * 2. Encrypts the DEK using Vault's transit engine with a subject-specific key
     * 3. Returns EncryptionMaterial containing the plaintext DEK, encrypted DEK, and context
     *
     * @param subjectId the subject identifier for key isolation
     * @return a CompletableFuture containing the encryption materials
     * @throws IllegalArgumentException if subjectId is null or empty
     */
    @Override
    public CompletableFuture<EncryptionMaterial> encryptionKeysFor(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Subject ID cannot be null or empty"));
        }

        logger.debug("Generating encryption materials for subject: {}", subjectId);

        return CompletableFuture
            .supplyAsync(() -> generateDataEncryptionKey())
            .thenCompose(dekMaterial -> encryptDataEncryptionKey(subjectId, dekMaterial))
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to generate encryption materials for subject: {}", subjectId, throwable);
                } else {
                    logger.debug("Successfully generated encryption materials for subject: {}", subjectId);
                }
            });
    }

    /**
     * Generates a new Data Encryption Key using Tink's AEAD primitive.
     *
     * @return a DataEncryptionKeyMaterial containing the AEAD primitive and raw key bytes
     */
    private DataEncryptionKeyMaterial generateDataEncryptionKey() {
        try {
            logger.debug("Generating new DEK using Tink AEAD");

            // Generate a 256-bit (32 bytes) random key for AES-GCM
            byte[] keyBytes = new byte[32];
            SECURE_RANDOM.nextBytes(keyBytes);

            // Create AEAD primitive directly from the key bytes
            Aead aead = new AesGcmJce(keyBytes);

            logger.debug("Successfully generated DEK (key size: {} bytes)", keyBytes.length);

            return new DataEncryptionKeyMaterial(aead, keyBytes);
        } catch (GeneralSecurityException e) {
            throw new VaultCryptoException("Failed to generate data encryption key", e);
        }
    }

    /**
     * Encrypts the Data Encryption Key using Vault's transit engine.
     *
     * @param subjectId the subject identifier
     * @param dekMaterial the DEK material to encrypt
     * @return a CompletableFuture containing the complete EncryptionMaterial
     */
    private CompletableFuture<EncryptionMaterial> encryptDataEncryptionKey(
        String subjectId,
        DataEncryptionKeyMaterial dekMaterial
    ) {
        String keyName = vaultClient.generateKeyName(subjectId);
        String encryptionContext = generateEncryptionContext(subjectId);

        logger.debug("Encrypting DEK with Vault key: {} (context: {})", keyName, encryptionContext);

        return vaultClient
            .encrypt(keyName, dekMaterial.keyBytes(), encryptionContext)
            .thenApply(encryptedDek -> {
                logger.debug("Successfully encrypted DEK for subject: {}", subjectId);
                return new EncryptionMaterial(dekMaterial.aead(), encryptedDek, encryptionContext);
            })
            .exceptionally(throwable -> {
                logger.error("Failed to encrypt DEK for subject: {}", subjectId, throwable);
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                } else {
                    throw new VaultCryptoException("Failed to encrypt data encryption key", throwable);
                }
            });
    }

    /**
     * Generates an encryption context string containing subject ID, timestamp, and version.
     *
     * @param subjectId the subject identifier
     * @return the encryption context string
     */
    private String generateEncryptionContext(String subjectId) {
        long timestamp = Instant.now().toEpochMilli();
        return String.format("subjectId=%s;timestamp=%d;version=%s", subjectId, timestamp, PROVIDER_VERSION);
    }

    @Override
    public void close() {
        logger.info("Closing VaultEncryptingMaterialsProvider");
        try {
            vaultClient.close();
        } catch (Exception e) {
            logger.warn("Error closing VaultTransitClient", e);
        }
    }

    /**
     * Internal record to hold DEK material during generation.
     */
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

    private record DataEncryptionKeyMaterial(Aead aead, byte[] keyBytes) {}
}
