package pi2schema.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.providers.inmemorykms.TinkInMemoryKms;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class LocalCryptoTest {

    private TinkInMemoryKms kms;
    private LocalEncryptor encryptor;
    private LocalDecryptor decryptor;

    @BeforeEach
    void setUp() {
        kms = new TinkInMemoryKms();
        encryptor = new LocalEncryptor(kms);
        decryptor = new LocalDecryptor(kms);
    }

    @Test
    void testBasicEncryptionDecryption() throws Exception {
        String subjectId = "user123";
        String originalData = "sensitive personal information";
        ByteBuffer plaintext = ByteBuffer.wrap(originalData.getBytes(StandardCharsets.UTF_8));

        // Encrypt
        CompletableFuture<EncryptedData> encryptFuture = encryptor.encrypt(subjectId, plaintext);
        EncryptedData encryptedData = encryptFuture.get();

        assertNotNull(encryptedData);
        assertNotNull(encryptedData.data());
        assertNotNull(encryptedData.encryptedDataKey());
        assertEquals(subjectId, encryptedData.keysetHandle());

        // Verify encrypted data is different from original
        assertNotEquals(plaintext, encryptedData.data());

        // Decrypt
        CompletableFuture<ByteBuffer> decryptFuture = decryptor.decrypt(subjectId, encryptedData);
        ByteBuffer decrypted = decryptFuture.get();

        assertNotNull(decrypted);
        byte[] decryptedBytes = new byte[decrypted.remaining()];
        decrypted.get(decryptedBytes);
        String decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);

        assertEquals(originalData, decryptedText);
    }

    @Test
    void testMultipleSubjectsHaveDifferentKeys() throws Exception {
        String subject1 = "user123";
        String subject2 = "user456";
        String testData = "test data";
        ByteBuffer plaintext = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));

        // Encrypt same data with different subjects
        EncryptedData encrypted1 = encryptor.encrypt(subject1, plaintext.duplicate()).get();
        EncryptedData encrypted2 = encryptor.encrypt(subject2, plaintext.duplicate()).get();

        // Encrypted data keys should be different (different KEKs)
        assertNotEquals(encrypted1.encryptedDataKey(), encrypted2.encryptedDataKey());

        // Both should decrypt correctly with their respective subject IDs
        ByteBuffer decrypted1 = decryptor.decrypt(subject1, encrypted1).get();
        ByteBuffer decrypted2 = decryptor.decrypt(subject2, encrypted2).get();

        assertEquals(testData, new String(getBytesFromBuffer(decrypted1), StandardCharsets.UTF_8));
        assertEquals(testData, new String(getBytesFromBuffer(decrypted2), StandardCharsets.UTF_8));
    }

    @Test
    void testCryptoShredding() throws Exception {
        String subjectId = "user789";
        String testData = "data to be shredded";
        ByteBuffer plaintext = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));

        // Encrypt data
        EncryptedData encryptedData = encryptor.encrypt(subjectId, plaintext).get();

        // Verify we can decrypt before shredding
        ByteBuffer decrypted = decryptor.decrypt(subjectId, encryptedData).get();
        assertEquals(testData, new String(getBytesFromBuffer(decrypted), StandardCharsets.UTF_8));

        // Perform crypto shredding
        assertTrue(kms.deleteKeyMaterial(subjectId));

        // Verify decryption now fails
        CompletableFuture<ByteBuffer> failedDecrypt = decryptor.decrypt(subjectId, encryptedData);
        assertThrows(Exception.class, () -> failedDecrypt.get());
    }

    @Test
    void testWrongSubjectIdDecryptionFails() throws Exception {
        String correctSubject = "user123";
        String wrongSubject = "user456";
        String testData = "secret data";
        ByteBuffer plaintext = ByteBuffer.wrap(testData.getBytes(StandardCharsets.UTF_8));

        // Encrypt with correct subject
        EncryptedData encryptedData = encryptor.encrypt(correctSubject, plaintext).get();

        // Try to decrypt with wrong subject ID
        CompletableFuture<ByteBuffer> failedDecrypt = decryptor.decrypt(wrongSubject, encryptedData);
        assertThrows(Exception.class, () -> failedDecrypt.get());
    }

    @Test
    void testEmptyData() throws Exception {
        String subjectId = "user123";
        ByteBuffer emptyData = ByteBuffer.allocate(0);

        EncryptedData encryptedData = encryptor.encrypt(subjectId, emptyData).get();
        ByteBuffer decrypted = decryptor.decrypt(subjectId, encryptedData).get();

        assertEquals(0, decrypted.remaining());
    }

    @Test
    void testLargeData() throws Exception {
        String subjectId = "user123";
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        ByteBuffer plaintext = ByteBuffer.wrap(largeData);

        EncryptedData encryptedData = encryptor.encrypt(subjectId, plaintext).get();
        ByteBuffer decrypted = decryptor.decrypt(subjectId, encryptedData).get();

        byte[] decryptedBytes = getBytesFromBuffer(decrypted);
        assertArrayEquals(largeData, decryptedBytes);
    }

    @Test
    void testKmsMetrics() throws Exception {
        assertEquals(0, kms.getKeyCount());

        // Create keys for different subjects
        encryptor.encrypt("user1", ByteBuffer.wrap("data1".getBytes())).get();
        assertEquals(1, kms.getKeyCount());

        encryptor.encrypt("user2", ByteBuffer.wrap("data2".getBytes())).get();
        assertEquals(2, kms.getKeyCount());

        // Same subject should not create new key
        encryptor.encrypt("user1", ByteBuffer.wrap("data3".getBytes())).get();
        assertEquals(2, kms.getKeyCount());

        // Delete one key
        kms.deleteKeyMaterial("user1");
        assertEquals(1, kms.getKeyCount());
    }

    private byte[] getBytesFromBuffer(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
