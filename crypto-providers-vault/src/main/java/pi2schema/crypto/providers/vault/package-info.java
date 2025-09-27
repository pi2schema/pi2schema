/**
 * HashiCorp Vault-based implementation of crypto-spi interfaces for GDPR-compliant encryption key management.
 *
 * <p>This package provides a complete implementation of the crypto-spi interfaces using HashiCorp Vault's
 * transit encryption engine for managing Key Encryption Keys (KEKs). The implementation ensures subject-specific
 * key isolation and supports GDPR right-to-be-forgotten requirements through selective key deletion.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider} - Generates and encrypts DEKs</li>
 *   <li>{@link pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider} - Decrypts DEKs for data access</li>
 *   <li>{@link pi2schema.crypto.providers.vault.VaultCryptoConfiguration} - Configuration for Vault connection</li>
 *   <li>{@link pi2schema.crypto.providers.vault.VaultTransitClient} - Low-level Vault API client</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The implementation uses a two-tier key hierarchy:</p>
 * <pre>
 * Subject Data → DEK (Tink AEAD) → Encrypted with KEK → KEK managed by Vault Transit Engine
 * </pre>
 *
 * <p>Each subject gets a unique key in Vault following the pattern: {@code {keyPrefix}/subject/{subjectId}}.
 * This ensures cryptographic isolation between subjects and enables GDPR compliance.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Configuration
 * VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
 *     .vaultUrl("https://vault.example.com:8200")
 *     .vaultToken(System.getenv("VAULT_TOKEN"))
 *     .build();
 *
 * // Encryption
 * try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
 *     CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("user-12345");
 *     EncryptionMaterial material = future.get();
 *
 *     byte[] encryptedData = material.aead().encrypt(plaintext, null);
 *     // Store material.encryptedDataKey() and material.encryptionContext() with encrypted data
 * }
 *
 * // Decryption
 * try (VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config)) {
 *     CompletableFuture<Aead> future = provider.decryptionKeysFor(
 *         "user-12345", encryptedDataKey, encryptionContext
 *     );
 *     Aead aead = future.get();
 *
 *     byte[] plaintext = aead.decrypt(encryptedData, null);
 * }
 * }</pre>
 *
 * <h2>GDPR Compliance</h2>
 * <p>To implement GDPR right-to-be-forgotten:</p>
 * <ol>
 *   <li>Delete the subject's key from Vault: {@code DELETE /v1/transit/keys/{keyPrefix}/subject/{subjectId}}</li>
 *   <li>Previously encrypted data becomes permanently inaccessible</li>
 *   <li>Other subjects' data remains unaffected due to key isolation</li>
 * </ol>
 *
 * <h2>Error Handling</h2>
 * <p>The package includes specific exceptions for different failure scenarios:</p>
 * <ul>
 *   <li>{@link pi2schema.crypto.providers.vault.VaultAuthenticationException} - Authentication failures</li>
 *   <li>{@link pi2schema.crypto.providers.vault.VaultConnectivityException} - Network issues</li>
 *   <li>{@link pi2schema.crypto.providers.vault.SubjectKeyNotFoundException} - Key not found (e.g., after deletion)</li>
 *   <li>{@link pi2schema.crypto.providers.vault.InvalidEncryptionContextException} - Context validation failures</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>The implementation is optimized for high performance:</p>
 * <ul>
 *   <li>Asynchronous operations using CompletableFuture</li>
 *   <li>HTTP connection pooling and keep-alive</li>
 *   <li>Configurable retry logic with exponential backoff</li>
 *   <li>Thread-safe for concurrent operations</li>
 * </ul>
 *
 * <h2>Security</h2>
 * <p>Security features include:</p>
 * <ul>
 *   <li>No sensitive data exposure in logs</li>
 *   <li>Encryption context validation to prevent key confusion</li>
 *   <li>Subject ID sanitization to prevent path traversal</li>
 *   <li>HTTPS-only communication with Vault</li>
 * </ul>
 *
 * @since 1.0
 * @see pi2schema.crypto.providers.EncryptingMaterialsProvider
 * @see pi2schema.crypto.providers.DecryptingMaterialsProvider
 */
package pi2schema.crypto.providers.vault;
