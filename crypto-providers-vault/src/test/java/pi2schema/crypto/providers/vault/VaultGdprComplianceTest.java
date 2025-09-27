package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GDPR compliance features of the Vault crypto provider.
 * These tests verify cryptographic isolation between subjects and right-to-be-forgotten functionality.
 */
@Testcontainers
class VaultGdprComplianceTest {

    @Container
    static final VaultContainer<?> vault = new VaultContainer<>("hashicorp/vault:1.15")
        .withVaultToken("test-token")
        .withVaultPort(8200)
        .withInitCommand("secrets enable transit");

    private VaultCryptoConfiguration config;
    private VaultTransitClient transitClient;
    private VaultEncryptingMaterialsProvider encryptingProvider;
    private VaultDecryptingMaterialsProvider decryptingProvider;

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        String vaultUrl = "http://" + vault.getHost() + ":" + vault.getMappedPort(8200);

        config =
            VaultCryptoConfiguration
                .builder()
                .vaultUrl(vaultUrl)
                .vaultToken("test-token")
                .transitEnginePath("transit")
                .keyPrefix("gdpr-test")
                .connectionTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(10))
                .maxRetries(2)
                .retryBackoffMs(Duration.ofMillis(50))
                .build();

        transitClient = new VaultTransitClient(config);
        encryptingProvider = new VaultEncryptingMaterialsProvider(config);
        decryptingProvider = new VaultDecryptingMaterialsProvider(config);

        // Wait for Vault to be ready and transit engine to be enabled
        waitForVaultReady();

        System.out.println("Test setup completed for: " + testInfo.getDisplayName());
    }

    private void waitForVaultReady() throws Exception {
        // Wait up to 30 seconds for Vault to be ready
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                // Try to list keys to verify transit engine is working
                transitClient.listSubjectKeys().get();
                return; // Success
            } catch (Exception e) {
                if (i == maxAttempts - 1) {
                    throw new RuntimeException("Vault transit engine not ready after " + maxAttempts + " seconds", e);
                }
                Thread.sleep(1000); // Wait 1 second before retry
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (decryptingProvider != null) {
            decryptingProvider.close();
        }
        if (encryptingProvider != null) {
            encryptingProvider.close();
        }
        if (transitClient != null) {
            transitClient.close();
        }
    }

    @Test
    void testCryptographicIsolationBetweenSubjects() throws Exception {
        String subject1 = "user-alice";
        String subject2 = "user-bob";
        String testData = "sensitive personal information";

        // Encrypt data for subject1
        EncryptionMaterial material1 = encryptingProvider.encryptionKeysFor(subject1).get();
        byte[] encryptedData1 = material1.dataEncryptionKey().encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // Encrypt data for subject2
        EncryptionMaterial material2 = encryptingProvider.encryptionKeysFor(subject2).get();
        byte[] encryptedData2 = material2.dataEncryptionKey().encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // Verify that subject1 can decrypt their own data
        Aead decryptAead1 = decryptingProvider
            .decryptionKeysFor(subject1, material1.encryptedDataKey(), material1.encryptionContext())
            .get();
        byte[] decryptedData1 = decryptAead1.decrypt(encryptedData1, null);
        assertEquals(testData, new String(decryptedData1, StandardCharsets.UTF_8));

        // Verify that subject2 can decrypt their own data
        Aead decryptAead2 = decryptingProvider
            .decryptionKeysFor(subject2, material2.encryptedDataKey(), material2.encryptionContext())
            .get();
        byte[] decryptedData2 = decryptAead2.decrypt(encryptedData2, null);
        assertEquals(testData, new String(decryptedData2, StandardCharsets.UTF_8));

        // Verify cryptographic isolation: subject1 cannot decrypt subject2's data
        assertThrows(
            ExecutionException.class,
            () -> {
                decryptingProvider
                    .decryptionKeysFor(subject1, material2.encryptedDataKey(), material2.encryptionContext())
                    .get();
            }
        );

        // Verify cryptographic isolation: subject2 cannot decrypt subject1's data
        assertThrows(
            ExecutionException.class,
            () -> {
                decryptingProvider
                    .decryptionKeysFor(subject2, material1.encryptedDataKey(), material1.encryptionContext())
                    .get();
            }
        );

        // Verify that encrypted data keys are different
        assertFalse(java.util.Arrays.equals(material1.encryptedDataKey(), material2.encryptedDataKey()));

        // Verify that encryption contexts are different
        assertNotEquals(material1.encryptionContext(), material2.encryptionContext());
    }

    @Test
    void testSubjectKeyExistence() throws Exception {
        String subjectId = "user-charlie";

        // Initially, key should not exist
        assertFalse(transitClient.subjectKeyExists(subjectId).get());

        // Create encryption materials (this should create the key)
        encryptingProvider.encryptionKeysFor(subjectId).get();

        // Now key should exist
        assertTrue(transitClient.subjectKeyExists(subjectId).get());
    }

    @Test
    void testGdprRightToBeForgotten() throws Exception {
        String subjectId = "user-david";
        String testData = "personal data to be forgotten";

        // Step 1: Encrypt data for the subject
        EncryptionMaterial material = encryptingProvider.encryptionKeysFor(subjectId).get();
        byte[] encryptedData = material.dataEncryptionKey().encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // Step 2: Verify we can decrypt the data
        Aead decryptAead = decryptingProvider
            .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
            .get();
        byte[] decryptedData = decryptAead.decrypt(encryptedData, null);
        assertEquals(testData, new String(decryptedData, StandardCharsets.UTF_8));

        // Step 3: Verify key exists
        assertTrue(transitClient.subjectKeyExists(subjectId).get());

        // Step 4: Delete the subject's key (GDPR right-to-be-forgotten)
        transitClient.deleteSubjectKey(subjectId).get();

        // Step 5: Verify key no longer exists
        assertFalse(transitClient.subjectKeyExists(subjectId).get());

        // Step 6: Verify that data can no longer be decrypted
        assertThrows(
            ExecutionException.class,
            () -> {
                decryptingProvider
                    .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
                    .get();
            }
        );

        // Step 7: Verify that the encrypted data is now unrecoverable
        // Even if we try to create a new key with the same subject ID,
        // the old encrypted data should remain unrecoverable
        EncryptionMaterial newMaterial = encryptingProvider.encryptionKeysFor(subjectId).get();

        // The new material should be different from the old one
        assertFalse(java.util.Arrays.equals(material.encryptedDataKey(), newMaterial.encryptedDataKey()));

        // The old encrypted data should still be unrecoverable with the new key
        assertThrows(
            Exception.class,
            () -> {
                newMaterial.dataEncryptionKey().decrypt(encryptedData, null);
            }
        );
    }

    @Test
    void testDeleteNonExistentSubjectKey() throws Exception {
        String nonExistentSubject = "user-nonexistent";

        // Verify key doesn't exist
        assertFalse(transitClient.subjectKeyExists(nonExistentSubject).get());

        // Attempting to delete a non-existent key should throw SubjectKeyNotFoundException
        ExecutionException exception = assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.deleteSubjectKey(nonExistentSubject).get();
            }
        );

        assertTrue(exception.getCause() instanceof SubjectKeyNotFoundException);
        SubjectKeyNotFoundException cause = (SubjectKeyNotFoundException) exception.getCause();
        assertEquals(nonExistentSubject, cause.getSubjectId());
    }

    @Test
    void testListSubjectKeys() throws Exception {
        String subject1 = "user-eve";
        String subject2 = "user-frank";
        String subject3 = "user-grace";

        // Initially, no keys should exist
        List<String> initialKeys = transitClient.listSubjectKeys().get();
        assertTrue(initialKeys.isEmpty() || !initialKeys.contains(subject1));
        assertTrue(initialKeys.isEmpty() || !initialKeys.contains(subject2));
        assertTrue(initialKeys.isEmpty() || !initialKeys.contains(subject3));

        // Create keys for subjects
        encryptingProvider.encryptionKeysFor(subject1).get();
        encryptingProvider.encryptionKeysFor(subject2).get();
        encryptingProvider.encryptionKeysFor(subject3).get();

        // List keys and verify all subjects are present
        List<String> keys = transitClient.listSubjectKeys().get();
        assertTrue(keys.contains(subject1));
        assertTrue(keys.contains(subject2));
        assertTrue(keys.contains(subject3));

        // Delete one subject's key
        transitClient.deleteSubjectKey(subject2).get();

        // Verify the deleted subject is no longer in the list
        List<String> keysAfterDeletion = transitClient.listSubjectKeys().get();
        assertTrue(keysAfterDeletion.contains(subject1));
        assertFalse(keysAfterDeletion.contains(subject2));
        assertTrue(keysAfterDeletion.contains(subject3));
    }

    @Test
    void testSubjectKeyNamingStrategy() throws Exception {
        String subjectId = "user-123";
        String expectedKeyName = "gdpr-test/subject/user-123";

        String actualKeyName = transitClient.generateKeyName(subjectId);
        assertEquals(expectedKeyName, actualKeyName);

        // Test with special characters that should be sanitized
        String specialSubjectId = "user@domain.com";
        String sanitizedKeyName = transitClient.generateKeyName(specialSubjectId);
        assertEquals("gdpr-test/subject/user_domain_com", sanitizedKeyName);
    }

    @Test
    void testConcurrentSubjectOperations() throws Exception {
        String subject1 = "concurrent-user-1";
        String subject2 = "concurrent-user-2";
        String testData = "concurrent test data";

        // Perform concurrent encryption operations
        CompletableFuture<EncryptionMaterial> future1 = encryptingProvider.encryptionKeysFor(subject1);
        CompletableFuture<EncryptionMaterial> future2 = encryptingProvider.encryptionKeysFor(subject2);

        EncryptionMaterial material1 = future1.get();
        EncryptionMaterial material2 = future2.get();

        // Encrypt data concurrently
        CompletableFuture<byte[]> encryptFuture1 = CompletableFuture.supplyAsync(() -> {
            try {
                return material1.dataEncryptionKey().encrypt(testData.getBytes(StandardCharsets.UTF_8), null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<byte[]> encryptFuture2 = CompletableFuture.supplyAsync(() -> {
            try {
                return material2.dataEncryptionKey().encrypt(testData.getBytes(StandardCharsets.UTF_8), null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        byte[] encryptedData1 = encryptFuture1.get();
        byte[] encryptedData2 = encryptFuture2.get();

        // Perform concurrent decryption operations
        CompletableFuture<Aead> decryptFuture1 = decryptingProvider.decryptionKeysFor(
            subject1,
            material1.encryptedDataKey(),
            material1.encryptionContext()
        );
        CompletableFuture<Aead> decryptFuture2 = decryptingProvider.decryptionKeysFor(
            subject2,
            material2.encryptedDataKey(),
            material2.encryptionContext()
        );

        Aead decryptAead1 = decryptFuture1.get();
        Aead decryptAead2 = decryptFuture2.get();

        // Verify both subjects can decrypt their own data
        byte[] decryptedData1 = decryptAead1.decrypt(encryptedData1, null);
        byte[] decryptedData2 = decryptAead2.decrypt(encryptedData2, null);

        assertEquals(testData, new String(decryptedData1, StandardCharsets.UTF_8));
        assertEquals(testData, new String(decryptedData2, StandardCharsets.UTF_8));

        // Verify cryptographic isolation still holds under concurrent operations
        assertFalse(java.util.Arrays.equals(material1.encryptedDataKey(), material2.encryptedDataKey()));
        assertNotEquals(material1.encryptionContext(), material2.encryptionContext());
    }

    @Test
    void testInvalidSubjectIdHandling() throws Exception {
        // Test null subject ID
        assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.subjectKeyExists(null).get();
            }
        );

        assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.deleteSubjectKey(null).get();
            }
        );

        // Test empty subject ID
        assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.subjectKeyExists("").get();
            }
        );

        assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.deleteSubjectKey("").get();
            }
        );

        // Test whitespace-only subject ID
        assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.subjectKeyExists("   ").get();
            }
        );

        assertThrows(
            ExecutionException.class,
            () -> {
                transitClient.deleteSubjectKey("   ").get();
            }
        );
    }

    /**
     * Enables the transit engine in Vault for testing.
     */
    private void enableTransitEngine() throws Exception {
        // The dev server should have transit engine enabled by default,
        // but we'll verify it's available by attempting to list keys
        try {
            transitClient.listSubjectKeys().get();
        } catch (Exception e) {
            // If listing fails, the transit engine might not be enabled
            // In a real test environment, you might need to enable it via Vault API
            throw new RuntimeException("Transit engine not available in test Vault instance", e);
        }
    }
}
