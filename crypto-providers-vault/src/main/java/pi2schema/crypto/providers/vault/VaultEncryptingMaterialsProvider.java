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
 * <p>This provider generates Data Encryption Keys (DEKs) using Tink's AEAD primitive and encrypts
 * them using Vault's transit engine with subject-specific keys for GDPR compliance.</p>
 * 
 * <h3>Key Management Architecture:</h3>
 * <ol>
 *   <li>Generate a new 256-bit DEK using Tink's AES-GCM AEAD primitive</li>
 *   <li>Encrypt the DEK using Vault's transit engine with a subject-specific KEK</li>
 *   <li>Return EncryptionMaterial containing the plaintext DEK, encrypted DEK, and context</li>
 * </ol>
 * 
 * <h3>Subject Isolation:</h3>
 * <p>Each subject gets a unique key in Vault following the pattern: {@code {keyPrefix}/subject/{subjectId}}.
 * This ensures cryptographic isolation between subjects and enables GDPR right-to-be-forgotten
 * compliance through selective key deletion.</p>
 * 
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
 *     .vaultUrl("https://vault.example.com:8200")
 *     .vaultToken("hvs.CAESIJ...")
 *     .build();
 * 
 * try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
 *     CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("user-12345");
 *     EncryptionMaterial material = future.get();
 *     
 *     // Use material.aead() for encrypting data
 *     // Store material.encryptedDataKey() and material.encryptionContext() with encrypted data
 * }
 * }</pre>
 * 
 * <h3>Thread Safety:</h3>
 * <p>This class is thread-safe and designed for concurrent use. All operations are asynchronous
 * and return CompletableFuture instances.</p>
 * 
 * <h3>Error Handling:</h3>
 * <p>Operations may throw the following exceptions:
 * <ul>
 *   <li>{@link VaultAuthenticationException} - Invalid or expired Vault token</li>
 *   <li>{@link VaultConnectivityException} - Network or connectivity issues</li>
 *   <li>{@link VaultCryptoException} - General cryptographic or Vault operation errors</li>
 * </ul>
 * 
 * @since 1.0
 * @see VaultDecryptingMaterialsProvider
 * @see VaultCryptoConfiguration
 * @see pi2schema.crypto.providers.EncryptingMaterialsProvider
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
     * <p>This constructor initializes the Vault client and validates the configuration.
     * The Tink AEAD configuration is automatically registered during class loading.</p>
     *
     * @param config the Vault configuration containing connection details and settings
     * @throws IllegalArgumentException if configuration is null or invalid
     * @throws VaultCryptoException if Tink AEAD configuration fails to register
     */
    public VaultEncryptingMaterialsProvider(VaultCryptoConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        // Validate configuration during initialization
        validateConfiguration(config);

        this.config = config;
        this.vaultClient = new VaultTransitClient(config);

        logger.info("VaultEncryptingMaterialsProvider initialized [vaultUrl={}, transitEngine={}, keyPrefix={}]", 
                   sanitizeUrl(config.getVaultUrl()), config.getTransitEnginePath(), config.getKeyPrefix());
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
     * Generates encryption materials for the specified subject.
     *
     * <p>This method performs the following operations asynchronously:
     * <ol>
     *   <li>Generates a new 256-bit DEK using Tink's AES-GCM AEAD primitive</li>
     *   <li>Encrypts the DEK using Vault's transit engine with a subject-specific key</li>
     *   <li>Returns EncryptionMaterial containing the plaintext DEK, encrypted DEK, and context</li>
     * </ol>
     * 
     * <p>The subject-specific key in Vault follows the pattern: {@code {keyPrefix}/subject/{subjectId}}.
     * If the key doesn't exist, it will be automatically created.</p>
     * 
     * <p>The encryption context includes the subject ID, timestamp, and provider version
     * for validation during decryption.</p>
     *
     * @param subjectId the subject identifier for key isolation (must not be null or empty)
     * @return a CompletableFuture containing the encryption materials
     * @throws IllegalArgumentException if subjectId is null or empty
     * @throws VaultAuthenticationException if Vault authentication fails
     * @throws VaultConnectivityException if Vault is unreachable
     * @throws VaultCryptoException if DEK generation or encryption fails
     */
    @Override
    public CompletableFuture<EncryptionMaterial> encryptionKeysFor(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            String errorMsg = "Subject ID cannot be null or empty";
            logger.error("Encryption materials generation failed: {}", errorMsg);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        }

        logger.debug("Generating encryption materials for subject: {}", subjectId);

        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    return generateDataEncryptionKey();
                } catch (Exception e) {
                    logger.error("Failed to generate DEK for subject: {}", subjectId, e);
                    throw e;
                }
            })
            .thenCompose(dekMaterial -> encryptDataEncryptionKey(subjectId, dekMaterial))
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to generate encryption materials [subjectId={}, error={}]", 
                               subjectId, throwable.getMessage(), throwable);
                } else {
                    logger.debug("Successfully generated encryption materials [subjectId={}, contextLength={}]", 
                               subjectId, result != null ? result.encryptionContext().length() : 0);
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

        logger.debug("Encrypting DEK with Vault [subjectId={}, keyName={}, contextLength={}]", 
                    subjectId, keyName, encryptionContext.length());

        return vaultClient
            .encrypt(keyName, dekMaterial.keyBytes(), encryptionContext)
            .thenApply(encryptedDek -> {
                logger.debug("Successfully encrypted DEK [subjectId={}, encryptedDekSize={}]", 
                           subjectId, encryptedDek.length);
                return new EncryptionMaterial(dekMaterial.aead(), encryptedDek, encryptionContext);
            })
            .exceptionally(throwable -> {
                // Unwrap CompletionException if present
                Throwable actualThrowable = throwable;
                if (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null) {
                    actualThrowable = throwable.getCause();
                }
                
                logger.error("Failed to encrypt DEK [subjectId={}, keyName={}, errorType={}, error={}]", 
                           subjectId, keyName, actualThrowable.getClass().getSimpleName(), 
                           sanitizeLogMessage(actualThrowable.getMessage()), actualThrowable);
                
                // Preserve the original exception type and chain properly
                if (actualThrowable instanceof RuntimeException) {
                    throw (RuntimeException) actualThrowable;
                } else {
                    throw new VaultCryptoException(
                        String.format("Failed to encrypt data encryption key for subject: %s", subjectId), 
                        actualThrowable
                    );
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
        logger.info("Closing VaultEncryptingMaterialsProvider");
        try {
            if (vaultClient != null) {
                vaultClient.close();
                logger.debug("VaultTransitClient closed successfully");
            }
        } catch (Exception e) {
            logger.warn("Error closing VaultTransitClient [error={}]", e.getMessage(), e);
        }
    }

    /**
     * Internal record to hold DEK material during generation.
     */
    private record DataEncryptionKeyMaterial(Aead aead, byte[] keyBytes) {}
}
