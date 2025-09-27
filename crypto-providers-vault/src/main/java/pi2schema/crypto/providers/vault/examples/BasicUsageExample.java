package pi2schema.crypto.providers.vault.examples;

import com.google.crypto.tink.Aead;
import pi2schema.crypto.providers.EncryptionMaterial;
import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;
import pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider;
import pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Basic usage example demonstrating encryption and decryption with Vault crypto providers.
 *
 * <p>This example shows the typical workflow for encrypting and decrypting data
 * using the Vault crypto providers with subject-specific key isolation.</p>
 */
public class BasicUsageExample {

    public static void main(String[] args) throws Exception {
        // Configure Vault connection
        VaultCryptoConfiguration config = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN")) // Get token from environment
            .transitEnginePath("transit")
            .keyPrefix("myapp")
            .connectionTimeout(Duration.ofSeconds(10))
            .requestTimeout(Duration.ofSeconds(30))
            .maxRetries(3)
            .build();

        // Subject identifier for key isolation
        String subjectId = "user-12345";

        // Data to encrypt
        String sensitiveData = "Personal information for user 12345";
        byte[] plaintext = sensitiveData.getBytes(StandardCharsets.UTF_8);

        // Step 1: Encrypt data
        byte[] encryptedData;
        byte[] encryptedDataKey;
        String encryptionContext;

        try (VaultEncryptingMaterialsProvider encryptingProvider = new VaultEncryptingMaterialsProvider(config)) {
            // Get encryption materials for the subject
            CompletableFuture<EncryptionMaterial> materialFuture = encryptingProvider.encryptionKeysFor(subjectId);
            EncryptionMaterial material = materialFuture.get();

            // Extract components
            Aead aead = material.dataEncryptionKey();
            encryptedDataKey = material.encryptedDataKey();
            encryptionContext = material.encryptionContext();

            // Encrypt the actual data
            encryptedData = aead.encrypt(plaintext, null);

            System.out.println("Data encrypted successfully");
            System.out.println("Encrypted data size: " + encryptedData.length + " bytes");
            System.out.println("Encrypted DEK size: " + encryptedDataKey.length + " bytes");
            System.out.println("Encryption context: " + encryptionContext);
        }

        // Step 2: Decrypt data (typically in a different process/time)
        try (VaultDecryptingMaterialsProvider decryptingProvider = new VaultDecryptingMaterialsProvider(config)) {
            // Get decryption materials for the subject
            CompletableFuture<Aead> aeadFuture = decryptingProvider.decryptionKeysFor(
                subjectId,
                encryptedDataKey,
                encryptionContext
            );
            Aead aead = aeadFuture.get();

            // Decrypt the data
            byte[] decryptedData = aead.decrypt(encryptedData, null);
            String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

            System.out.println("Data decrypted successfully");
            System.out.println("Decrypted text: " + decryptedText);

            // Verify data integrity
            if (sensitiveData.equals(decryptedText)) {
                System.out.println("✓ Data integrity verified");
            } else {
                System.err.println("✗ Data integrity check failed");
            }
        }
    }
}
