package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;

import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of DecryptingMaterialsProvider that uses HashiCorp Vault's transit encryption
 * to decrypt Key Encryption Keys (KEKs) for subject-specific data decryption.
 *
 * <p>This provider decrypts encrypted Data Encryption Keys (DEKs) using Vault's transit engine
 * with subject-specific keys for GDPR compliance.</p>
 *
 * <h3>Decryption Process:</h3>
 * <ol>
 *   <li>Decrypt the encrypted DEK using Vault's transit engine with the subject-specific KEK</li>
 *   <li>Reconstruct the Tink AEAD primitive from the decrypted DEK bytes</li>
 * </ol>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
 *     .vaultUrl("https://vault.example.com:8200")
 *     .vaultToken("hvs.CAESIJ...")
 *     .build();
 *
 * try (VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config)) {
 *     CompletableFuture<Aead> future = provider.decryptionKeysFor(
 *         "user-12345",
 *         encryptedDataKey,
 *         null  // encryption context not used
 *     );
 *     Aead aead = future.get();
 *
 *     // Use aead for decrypting data
 * }
 * }</pre>
 *
 * <h3>GDPR Compliance:</h3>
 * <p>When a subject's key is deleted from Vault (right-to-be-forgotten), attempts to decrypt
 * their data will fail with {@link SubjectKeyNotFoundException}, ensuring data becomes
 * permanently inaccessible.</p>
 *
 * <h3>Thread Safety:</h3>
 * <p>This class is thread-safe and designed for concurrent use. All operations are asynchronous
 * and return CompletableFuture instances.</p>
 *
 * <h3>Error Handling:</h3>
 * <p>Operations may throw the following exceptions:
 * <ul>
 *   <li>{@link SubjectKeyNotFoundException} - Subject's key not found in Vault</li>
 *   <li>{@link VaultAuthenticationException} - Invalid or expired Vault token</li>
 *   <li>{@link VaultConnectivityException} - Network or connectivity issues</li>
 *   <li>{@link VaultCryptoException} - General cryptographic or Vault operation errors</li>
 * </ul>
 *
 * @since 1.0
 * @see VaultEncryptingMaterialsProvider
 * @see VaultCryptoConfiguration
 * @see pi2schema.crypto.providers.DecryptingMaterialsProvider
 */
public class VaultDecryptingMaterialsProvider implements DecryptingMaterialsProvider {

    private static final Logger logger = LoggerFactory.getLogger(VaultDecryptingMaterialsProvider.class);

    private final VaultTransitClient vaultClient;

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
     * @param config the Vault crypto configuration (must not be null)
     * @throws IllegalArgumentException if config is null
     * @throws VaultCryptoException if initialization fails
     */
    public VaultDecryptingMaterialsProvider(VaultCryptoConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("VaultCryptoConfiguration cannot be null");
        }

        try {
            this.vaultClient = new VaultTransitClient(config);
            logger.info(
                "VaultDecryptingMaterialsProvider initialized [vaultUrl={}, transitEngine={}, keyPrefix={}]",
                sanitizeUrl(config.getVaultUrl()),
                config.getTransitEnginePath(),
                config.getKeyPrefix()
            );
        } catch (Exception e) {
            String errorMsg = "Failed to initialize VaultDecryptingMaterialsProvider";
            logger.error(errorMsg, e);
            throw new VaultCryptoException(errorMsg, e);
        }
    }

    /**
     * Decrypts the encrypted Data Encryption Key using Vault's transit engine and reconstructs the Aead primitive.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Decrypts the encrypted DEK using Vault's transit engine with the subject-specific KEK</li>
     *   <li>Reconstructs the Tink AEAD primitive from the decrypted DEK bytes</li>
     * </ol>
     *
     * <p>The subject-specific key in Vault follows the pattern: {@code {keyPrefix}/subject/{subjectId}}.</p>
     *
     * @param subjectId the subject identifier to locate the appropriate KEK (must not be null or empty)
     * @param encryptedDataKey the encrypted DEK to decrypt (must not be null or empty)
     * @param encryptionContext unused parameter (kept for interface compatibility, can be null)
     * @return CompletableFuture containing the Aead primitive with the decrypted DEK
     * @throws IllegalArgumentException if subjectId or encryptedDataKey is null or empty
     * @throws SubjectKeyNotFoundException if the subject's key is not found in Vault
     * @throws VaultAuthenticationException if Vault authentication fails
     * @throws VaultConnectivityException if Vault is unreachable
     * @throws VaultCryptoException if DEK decryption or AEAD reconstruction fails
     */
    @Override
    public CompletableFuture<Aead> decryptionKeysFor(
        String subjectId,
        byte[] encryptedDataKey,
        String encryptionContext
    ) {
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

        logger.debug("Decrypting materials [subjectId={}, encryptedKeySize={}]", subjectId, encryptedDataKey.length);

        return decryptDataEncryptionKey(subjectId, encryptedDataKey)
            .thenApply(dekBytes -> {
                try {
                    logger.debug("Reconstructing AEAD primitive from decrypted DEK [subjectId={}]", subjectId);
                    Aead aead = new AesGcmJce(dekBytes);
                    return aead;
                } catch (GeneralSecurityException e) {
                    String errorMsg = String.format(
                        "Failed to reconstruct AEAD primitive from decrypted DEK [subjectId=%s]",
                        subjectId
                    );
                    logger.error(errorMsg, e);
                    throw new VaultCryptoException(errorMsg, e);
                }
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error(
                        "Failed to decrypt materials [subjectId={}, error={}]",
                        subjectId,
                        throwable.getMessage(),
                        throwable
                    );
                } else {
                    logger.debug("Successfully decrypted materials [subjectId={}]", subjectId);
                }
            });
    }

    /**
     * Decrypts the encrypted DEK using Vault's transit engine.
     *
     * @param subjectId the subject identifier
     * @param encryptedDataKey the encrypted DEK bytes
     * @return CompletableFuture containing the decrypted DEK bytes
     */
    private CompletableFuture<byte[]> decryptDataEncryptionKey(String subjectId, byte[] encryptedDataKey) {
        String keyName = vaultClient.generateKeyName(subjectId);

        logger.debug("Decrypting DEK with Vault [subjectId={}, keyName={}]", subjectId, keyName);

        return vaultClient.decrypt(keyName, encryptedDataKey, null);
    }

    /**
     * Closes this provider and releases any associated resources.
     */
    @Override
    public void close() {
        if (vaultClient != null) {
            try {
                vaultClient.close();
                logger.debug("VaultDecryptingMaterialsProvider closed successfully");
            } catch (Exception e) {
                logger.warn("Error closing VaultDecryptingMaterialsProvider", e);
            }
        }
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

        return url;
    }
}
