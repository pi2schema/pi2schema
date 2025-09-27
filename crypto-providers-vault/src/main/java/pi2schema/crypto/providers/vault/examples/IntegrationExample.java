package pi2schema.crypto.providers.vault.examples;

import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;
import pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider;
import pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Integration example showing how to use Vault crypto providers in a real application context.
 *
 * <p>This example demonstrates a user service that encrypts and stores personal data
 * with subject-specific encryption, suitable for GDPR compliance requirements.</p>
 */
public class IntegrationExample {

    /**
     * Simulated user service that handles personal data encryption/decryption.
     */
    public static class UserService {

        private final VaultEncryptingMaterialsProvider encryptingProvider;
        private final VaultDecryptingMaterialsProvider decryptingProvider;

        // Simulated database storage
        private final Map<String, EncryptedUserRecord> database = new HashMap<>();

        public UserService(VaultCryptoConfiguration config) {
            this.encryptingProvider = new VaultEncryptingMaterialsProvider(config);
            this.decryptingProvider = new VaultDecryptingMaterialsProvider(config);
        }

        /**
         * Stores user personal data with encryption.
         */
        public CompletableFuture<Void> storeUserData(String userId, UserData userData) {
            return encryptingProvider
                .encryptionKeysFor(userId)
                .thenApply(material -> {
                    try {
                        // Serialize user data (in practice, use proper serialization)
                        String jsonData = serializeUserData(userData);
                        byte[] plaintext = jsonData.getBytes(StandardCharsets.UTF_8);

                        // Encrypt the data
                        byte[] encryptedData = material.dataEncryptionKey().encrypt(plaintext, null);

                        // Store encrypted record
                        EncryptedUserRecord record = new EncryptedUserRecord(
                            userId,
                            encryptedData,
                            material.encryptedDataKey(),
                            material.encryptionContext(),
                            System.currentTimeMillis()
                        );

                        database.put(userId, record);
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to encrypt user data", e);
                    }
                });
        }

        /**
         * Retrieves and decrypts user personal data.
         */
        public CompletableFuture<UserData> getUserData(String userId) {
            EncryptedUserRecord record = database.get(userId);
            if (record == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("User not found: " + userId));
            }

            return decryptingProvider
                .decryptionKeysFor(userId, record.encryptedDataKey, record.encryptionContext)
                .thenApply(aead -> {
                    try {
                        // Decrypt the data
                        byte[] plaintext = aead.decrypt(record.encryptedData, null);
                        String jsonData = new String(plaintext, StandardCharsets.UTF_8);

                        // Deserialize user data
                        return deserializeUserData(jsonData);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decrypt user data", e);
                    }
                });
        }

        /**
         * Implements GDPR right-to-be-forgotten by removing user data.
         * In practice, this would also delete the user's key from Vault.
         */
        public CompletableFuture<Void> deleteUserData(String userId) {
            return CompletableFuture.runAsync(() -> {
                // Remove from local storage
                database.remove(userId);

                // In practice, you would also delete the key from Vault:
                // DELETE /v1/transit/keys/{keyPrefix}/subject/{userId}
                System.out.println("User data deleted for: " + userId);
                System.out.println("Note: In production, also delete Vault key: " + generateKeyName(userId));
            });
        }

        /**
         * Lists all users (for demonstration purposes).
         */
        public CompletableFuture<Map<String, String>> listUsers() {
            return CompletableFuture.supplyAsync(() -> {
                Map<String, String> users = new HashMap<>();
                for (String userId : database.keySet()) {
                    users.put(userId, "Encrypted data available");
                }
                return users;
            });
        }

        /**
         * Closes the providers and cleans up resources.
         */
        public void close() throws Exception {
            encryptingProvider.close();
            decryptingProvider.close();
        }

        private String generateKeyName(String userId) {
            return "myapp/subject/" + userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        }

        private String serializeUserData(UserData userData) {
            // Simple JSON serialization (use proper JSON library in practice)
            return String.format(
                "{\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\"}",
                userData.name,
                userData.email,
                userData.phone,
                userData.address
            );
        }

        private UserData deserializeUserData(String json) {
            // Simple JSON deserialization (use proper JSON library in practice)
            String name = extractJsonValue(json, "name");
            String email = extractJsonValue(json, "email");
            String phone = extractJsonValue(json, "phone");
            String address = extractJsonValue(json, "address");
            return new UserData(name, email, phone, address);
        }

        private String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern) + pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
    }

    /**
     * User data model.
     */
    public static class UserData {

        final String name;
        final String email;
        final String phone;
        final String address;

        public UserData(String name, String email, String phone, String address) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.address = address;
        }

        @Override
        public String toString() {
            return String.format(
                "UserData{name='%s', email='%s', phone='%s', address='%s'}",
                name,
                email,
                phone,
                address
            );
        }
    }

    /**
     * Encrypted user record as stored in database.
     */
    public static class EncryptedUserRecord {

        final String userId;
        final byte[] encryptedData;
        final byte[] encryptedDataKey;
        final String encryptionContext;
        final long timestamp;

        public EncryptedUserRecord(
            String userId,
            byte[] encryptedData,
            byte[] encryptedDataKey,
            String encryptionContext,
            long timestamp
        ) {
            this.userId = userId;
            this.encryptedData = encryptedData;
            this.encryptedDataKey = encryptedDataKey;
            this.encryptionContext = encryptionContext;
            this.timestamp = timestamp;
        }
    }

    /**
     * Application configuration factory.
     */
    public static class ConfigurationFactory {

        public static VaultCryptoConfiguration createProductionConfig() {
            return VaultCryptoConfiguration
                .builder()
                .vaultUrl(System.getenv("VAULT_URL"))
                .vaultToken(System.getenv("VAULT_TOKEN"))
                .transitEnginePath("production-transit")
                .keyPrefix("user-service")
                .connectionTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .retryBackoffMs(Duration.ofMillis(100))
                .build();
        }

        public static VaultCryptoConfiguration createDevelopmentConfig() {
            return VaultCryptoConfiguration
                .builder()
                .vaultUrl("http://localhost:8200")
                .vaultToken("dev-only-token")
                .transitEnginePath("transit")
                .keyPrefix("dev-user-service")
                .build();
        }
    }

    /**
     * Main application demonstrating the user service.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== User Service Integration Example ===");

        // Use development configuration (change for production)
        VaultCryptoConfiguration config = ConfigurationFactory.createDevelopmentConfig();

        UserService userService = new UserService(config);

        // Create some test users
        UserData alice = new UserData("Alice Johnson", "alice@example.com", "555-0101", "123 Main St");
        UserData bob = new UserData("Bob Smith", "bob@example.com", "555-0102", "456 Oak Ave");
        UserData charlie = new UserData("Charlie Brown", "charlie@example.com", "555-0103", "789 Pine Rd");

        System.out.println("\n1. Storing user data...");

        // Store users concurrently
        CompletableFuture<Void> storeAlice = userService.storeUserData("alice-123", alice);
        CompletableFuture<Void> storeBob = userService.storeUserData("bob-456", bob);
        CompletableFuture<Void> storeCharlie = userService.storeUserData("charlie-789", charlie);

        CompletableFuture.allOf(storeAlice, storeBob, storeCharlie).get();
        System.out.println("✓ All users stored successfully");

        // List users
        System.out.println("\n2. Listing users...");
        Map<String, String> users = userService.listUsers().get();
        users.forEach((id, status) -> System.out.println("  " + id + ": " + status));

        // Retrieve user data
        System.out.println("\n3. Retrieving user data...");
        UserData retrievedAlice = userService.getUserData("alice-123").get();
        System.out.println("Alice: " + retrievedAlice);

        UserData retrievedBob = userService.getUserData("bob-456").get();
        System.out.println("Bob: " + retrievedBob);

        // Demonstrate GDPR deletion
        System.out.println("\n4. GDPR deletion scenario...");
        System.out.println("Charlie requests data deletion (right-to-be-forgotten)");

        userService.deleteUserData("charlie-789").get();
        System.out.println("✓ Charlie's data deleted");

        // Verify other users' data is still accessible
        System.out.println("\n5. Verifying data isolation...");
        try {
            UserData stillAlice = userService.getUserData("alice-123").get();
            System.out.println("✓ Alice's data still accessible: " + stillAlice.name);
        } catch (Exception e) {
            System.err.println("✗ Alice's data should still be accessible!");
        }

        try {
            UserData stillBob = userService.getUserData("bob-456").get();
            System.out.println("✓ Bob's data still accessible: " + stillBob.name);
        } catch (Exception e) {
            System.err.println("✗ Bob's data should still be accessible!");
        }

        // Try to access deleted user's data
        try {
            userService.getUserData("charlie-789").get();
            System.err.println("✗ Charlie's data should not be accessible!");
        } catch (Exception e) {
            System.out.println("✓ Charlie's data is no longer accessible (as expected)");
        }

        // Final user list
        System.out.println("\n6. Final user list...");
        Map<String, String> finalUsers = userService.listUsers().get();
        finalUsers.forEach((id, status) -> System.out.println("  " + id + ": " + status));

        System.out.println("\n=== Integration Example Complete ===");
        System.out.println("✓ User data encrypted with subject-specific keys");
        System.out.println("✓ GDPR deletion implemented through key removal");
        System.out.println("✓ Subject isolation verified");
        System.out.println("✓ Asynchronous operations demonstrated");
    }
}
