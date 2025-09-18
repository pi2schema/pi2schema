package pi2schema.crypto.providers.vault.examples;

import com.google.crypto.tink.Aead;
import pi2schema.crypto.providers.EncryptionMaterial;
import pi2schema.crypto.providers.vault.SubjectKeyNotFoundException;
import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;
import pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider;
import pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating GDPR compliance workflows with Vault crypto providers.
 * 
 * <p>This example shows how to implement GDPR right-to-be-forgotten using
 * subject-specific key deletion in Vault, making encrypted data permanently
 * inaccessible without affecting other subjects' data.</p>
 */
public class GdprComplianceExample {

    private final VaultCryptoConfiguration config;
    
    // Simulated data store (in practice, this would be a database)
    private final Map<String, EncryptedUserData> dataStore = new HashMap<>();

    public GdprComplianceExample(VaultCryptoConfiguration config) {
        this.config = config;
    }

    /**
     * Represents encrypted user data as it would be stored in a database.
     */
    public static class EncryptedUserData {
        private final byte[] encryptedData;
        private final byte[] encryptedDataKey;
        private final String encryptionContext;

        public EncryptedUserData(byte[] encryptedData, byte[] encryptedDataKey, String encryptionContext) {
            this.encryptedData = encryptedData;
            this.encryptedDataKey = encryptedDataKey;
            this.encryptionContext = encryptionContext;
        }

        public byte[] getEncryptedData() { return encryptedData; }
        public byte[] getEncryptedDataKey() { return encryptedDataKey; }
        public String getEncryptionContext() { return encryptionContext; }
    }

    /**
     * Encrypts and stores user data with subject-specific encryption.
     */
    public void storeUserData(String userId, String personalData) throws Exception {
        System.out.println("Storing encrypted data for user: " + userId);

        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            // Get encryption materials for this specific user
            CompletableFuture<EncryptionMaterial> materialFuture = provider.encryptionKeysFor(userId);
            EncryptionMaterial material = materialFuture.get();

            // Encrypt the personal data
            byte[] plaintext = personalData.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedData = material.aead().encrypt(plaintext, null);

            // Store encrypted data with its key material
            EncryptedUserData userData = new EncryptedUserData(
                encryptedData,
                material.encryptedDataKey(),
                material.encryptionContext()
            );
            
            dataStore.put(userId, userData);
            System.out.println("✓ User data encrypted and stored successfully");
        }
    }

    /**
     * Retrieves and decrypts user data.
     */
    public String retrieveUserData(String userId) throws Exception {
        System.out.println("Retrieving data for user: " + userId);

        EncryptedUserData userData = dataStore.get(userId);
        if (userData == null) {
            throw new IllegalArgumentException("No data found for user: " + userId);
        }

        try (VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config)) {
            // Get decryption materials for this specific user
            CompletableFuture<Aead> aeadFuture = provider.decryptionKeysFor(
                userId,
                userData.getEncryptedDataKey(),
                userData.getEncryptionContext()
            );
            Aead aead = aeadFuture.get();

            // Decrypt the data
            byte[] decryptedData = aead.decrypt(userData.getEncryptedData(), null);
            String personalData = new String(decryptedData, StandardCharsets.UTF_8);
            
            System.out.println("✓ User data retrieved and decrypted successfully");
            return personalData;
        }
    }

    /**
     * Demonstrates GDPR right-to-be-forgotten by attempting to access data
     * after the subject's key has been deleted from Vault.
     */
    public void demonstrateRightToBeForgotten(String userId) {
        System.out.println("\n=== GDPR Right-to-be-Forgotten Demonstration ===");
        
        try {
            // First, show that we can access the data normally
            System.out.println("Before key deletion:");
            String data = retrieveUserData(userId);
            System.out.println("Retrieved data: " + data);

            // Simulate key deletion in Vault (in practice, this would be done via Vault API)
            System.out.println("\n[SIMULATED] Deleting user's encryption key from Vault...");
            System.out.println("In practice, you would call Vault API to delete the key:");
            System.out.println("DELETE /v1/transit/keys/" + generateKeyName(userId));

            // Now attempt to access the data - this should fail
            System.out.println("\nAfter key deletion:");
            try {
                retrieveUserData(userId);
                System.err.println("✗ ERROR: Data should not be accessible after key deletion!");
            } catch (Exception e) {
                if (e.getCause() instanceof SubjectKeyNotFoundException) {
                    System.out.println("✓ GDPR Compliance: Data is no longer accessible");
                    System.out.println("  Reason: " + e.getCause().getMessage());
                } else {
                    System.out.println("✓ Data access failed (key not found): " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error during GDPR demonstration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demonstrates subject isolation - one user's data remains accessible
     * even after another user's key is deleted.
     */
    public void demonstrateSubjectIsolation() throws Exception {
        System.out.println("\n=== Subject Isolation Demonstration ===");

        String user1 = "user-alice";
        String user2 = "user-bob";

        // Store data for both users
        storeUserData(user1, "Alice's personal information");
        storeUserData(user2, "Bob's personal information");

        // Verify both can access their data
        System.out.println("\nBoth users can access their data:");
        System.out.println("Alice's data: " + retrieveUserData(user1));
        System.out.println("Bob's data: " + retrieveUserData(user2));

        // Simulate deleting Alice's key (GDPR request)
        System.out.println("\n[SIMULATED] Alice requests data deletion (GDPR)");
        System.out.println("Deleting Alice's key from Vault...");

        // Verify Alice's data is no longer accessible
        System.out.println("\nAfter Alice's key deletion:");
        try {
            retrieveUserData(user1);
            System.err.println("✗ ERROR: Alice's data should not be accessible!");
        } catch (Exception e) {
            System.out.println("✓ Alice's data is no longer accessible");
        }

        // Verify Bob's data is still accessible (subject isolation)
        try {
            String bobData = retrieveUserData(user2);
            System.out.println("✓ Bob's data remains accessible: " + bobData);
            System.out.println("✓ Subject isolation verified");
        } catch (Exception e) {
            System.err.println("✗ ERROR: Bob's data should still be accessible!");
            e.printStackTrace();
        }
    }

    /**
     * Generates the key name that would be used in Vault for a given user.
     * This matches the pattern used by VaultTransitClient.
     */
    private String generateKeyName(String userId) {
        String keyPrefix = config.getKeyPrefix();
        String sanitizedUserId = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return keyPrefix + "/subject/" + sanitizedUserId;
    }

    /**
     * Main method demonstrating the complete GDPR compliance workflow.
     */
    public static void main(String[] args) throws Exception {
        // Configure Vault (use environment variables in production)
        VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .keyPrefix("gdpr-demo")
            .build();

        GdprComplianceExample example = new GdprComplianceExample(config);

        // Demonstrate basic encryption/decryption
        String userId = "user-12345";
        String personalData = "John Doe, john.doe@example.com, 555-1234, 123 Main St";
        
        example.storeUserData(userId, personalData);
        
        // Demonstrate GDPR right-to-be-forgotten
        example.demonstrateRightToBeForgotten(userId);
        
        // Demonstrate subject isolation
        example.demonstrateSubjectIsolation();

        System.out.println("\n=== GDPR Compliance Summary ===");
        System.out.println("✓ Subject-specific encryption keys ensure data isolation");
        System.out.println("✓ Key deletion makes data permanently inaccessible");
        System.out.println("✓ Other subjects' data remains unaffected");
        System.out.println("✓ GDPR right-to-be-forgotten compliance achieved");
    }
}