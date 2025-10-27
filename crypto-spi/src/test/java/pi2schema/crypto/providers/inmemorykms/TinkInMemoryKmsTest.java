package pi2schema.crypto.providers.inmemorykms;

import com.google.crypto.tink.Aead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TinkInMemoryKmsTest {

    private TinkInMemoryKms kms;

    @BeforeEach
    void setUp() {
        kms = new TinkInMemoryKms();
    }

    @Test
    void testEncryptionMaterialGeneration() throws Exception {
        String subjectId = "user123";

        CompletableFuture<EncryptionMaterial> future = kms.encryptionKeysFor(subjectId);
        EncryptionMaterial material = future.get();

        assertNotNull(material);
        assertNotNull(material.dataEncryptionKey());
        assertNotNull(material.encryptedDataKey());
        assertEquals(subjectId, material.encryptionContext());

        // Verify the DEK can actually encrypt/decrypt
        Aead dek = material.dataEncryptionKey();
        byte[] testData = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = dek.encrypt(testData, new byte[0]);
        byte[] decrypted = dek.decrypt(encrypted, new byte[0]);

        assertArrayEquals(testData, decrypted);

        // Verify key count increased
        assertEquals(1, kms.getKeyCount());
    }

    @Test
    void testDecryptionKeysRecovery() throws Exception {
        String subjectId = "user456";

        // First get encryption material
        EncryptionMaterial encMaterial = kms.encryptionKeysFor(subjectId).get();

        // Then recover the DEK using the encrypted data key
        CompletableFuture<Aead> future = kms.decryptionKeysFor(
            subjectId,
            encMaterial.encryptedDataKey(),
            encMaterial.encryptionContext()
        );
        Aead recoveredDek = future.get();

        assertNotNull(recoveredDek);

        // Test that both DEKs can decrypt the same data
        byte[] testData = "sensitive information".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = encMaterial.dataEncryptionKey().encrypt(testData, new byte[0]);
        byte[] decrypted = recoveredDek.decrypt(encrypted, new byte[0]);

        assertArrayEquals(testData, decrypted);
    }

    @Test
    void testMultipleSubjectsGetDifferentKeys() throws Exception {
        String subject1 = "user1";
        String subject2 = "user2";

        EncryptionMaterial material1 = kms.encryptionKeysFor(subject1).get();
        EncryptionMaterial material2 = kms.encryptionKeysFor(subject2).get();

        // Encrypted data keys should be different (different KEKs)
        assertFalse(java.util.Arrays.equals(material1.encryptedDataKey(), material2.encryptedDataKey()));

        // Encryption contexts should be different
        assertNotEquals(material1.encryptionContext(), material2.encryptionContext());

        assertEquals(2, kms.getKeyCount());
    }

    @Test
    void testSameSubjectReusesSameKek() throws Exception {
        String subjectId = "user123";

        // Get encryption material twice for same subject
        EncryptionMaterial material1 = kms.encryptionKeysFor(subjectId).get();
        EncryptionMaterial material2 = kms.encryptionKeysFor(subjectId).get();

        // DEKs should be different (fresh each time)
        assertNotSame(material1.dataEncryptionKey(), material2.dataEncryptionKey());

        // But both should be decryptable with the same KEK
        Aead dek1 = kms.decryptionKeysFor(subjectId, material1.encryptedDataKey(), material1.encryptionContext()).get();
        Aead dek2 = kms.decryptionKeysFor(subjectId, material2.encryptedDataKey(), material2.encryptionContext()).get();

        assertNotNull(dek1);
        assertNotNull(dek2);

        // Should only have one KEK stored
        assertEquals(1, kms.getKeyCount());
    }

    @Test
    void testCryptoShredding() throws Exception {
        String subjectId = "user789";

        // Create encryption material
        EncryptionMaterial material = kms.encryptionKeysFor(subjectId).get();
        assertEquals(1, kms.getKeyCount());

        // Verify we can recover the DEK before shredding
        Aead dek = kms.decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext()).get();
        assertNotNull(dek);

        // Perform crypto shredding
        assertTrue(kms.deleteKeyMaterial(subjectId));
        assertEquals(0, kms.getKeyCount());

        // Verify we can no longer recover the DEK
        CompletableFuture<Aead> failedRecovery = kms.decryptionKeysFor(
            subjectId,
            material.encryptedDataKey(),
            material.encryptionContext()
        );
        assertThrows(Exception.class, () -> failedRecovery.get());

        // Deleting again should return false
        assertFalse(kms.deleteKeyMaterial(subjectId));
    }

    @Test
    void testWrongSubjectDecryptionFails() throws Exception {
        String correctSubject = "user123";
        String wrongSubject = "user456";

        // Create material for correct subject
        EncryptionMaterial material = kms.encryptionKeysFor(correctSubject).get();

        // Try to decrypt with wrong subject
        CompletableFuture<Aead> failedDecrypt = kms.decryptionKeysFor(
            wrongSubject,
            material.encryptedDataKey(),
            material.encryptionContext()
        );
        assertThrows(Exception.class, () -> failedDecrypt.get());
    }

    @Test
    void testWrongEncryptionContextFails() throws Exception {
        String subjectId = "user123";

        EncryptionMaterial material = kms.encryptionKeysFor(subjectId).get();

        // Try to decrypt with wrong encryption context
        CompletableFuture<Aead> failedDecrypt = kms.decryptionKeysFor(
            subjectId,
            material.encryptedDataKey(),
            "wrong-context"
        );
        assertThrows(Exception.class, () -> failedDecrypt.get());
    }

    @Test
    void testCorruptedEncryptedDataKeyFails() throws Exception {
        String subjectId = "user123";

        EncryptionMaterial material = kms.encryptionKeysFor(subjectId).get();

        // Corrupt the encrypted data key
        byte[] corruptedKey = material.encryptedDataKey().clone();
        corruptedKey[0] = (byte) (corruptedKey[0] ^ 0xFF);

        CompletableFuture<Aead> failedDecrypt = kms.decryptionKeysFor(
            subjectId,
            corruptedKey,
            material.encryptionContext()
        );
        assertThrows(Exception.class, () -> failedDecrypt.get());
    }

    @Test
    void testKmsClose() throws Exception {
        String subject1 = "user1";
        String subject2 = "user2";

        kms.encryptionKeysFor(subject1).get();
        kms.encryptionKeysFor(subject2).get();
        assertEquals(2, kms.getKeyCount());

        kms.close();
        assertEquals(0, kms.getKeyCount());
    }

    @Test
    void testConcurrentOperations() throws Exception {
        int numThreads = 10;
        CompletableFuture<?>[] futures = new CompletableFuture[numThreads];

        // Create encryption materials concurrently for different subjects
        for (int i = 0; i < numThreads; i++) {
            final String subjectId = "user" + i;
            futures[i] =
                kms
                    .encryptionKeysFor(subjectId)
                    .thenCompose(material ->
                        kms
                            .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
                            .thenApply(dek -> {
                                assertNotNull(dek);
                                return null;
                            })
                    );
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).get();
        assertEquals(numThreads, kms.getKeyCount());
    }
}
