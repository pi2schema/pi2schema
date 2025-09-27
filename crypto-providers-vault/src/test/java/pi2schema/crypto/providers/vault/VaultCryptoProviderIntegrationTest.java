package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Vault crypto providers using a real Vault instance via Testcontainers.
 *
 * These tests verify:
 * - Complete encrypt/decrypt cycle with subject isolation
 * - GDPR compliance scenarios (key deletion and data inaccessibility)
 * - Concurrent operations and performance characteristics
 * - Real Vault API interactions
 */
@Testcontainers
class VaultCryptoProviderIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(VaultCryptoProviderIntegrationTest.class);

    @Container
    static final VaultContainer<?> vaultContainer = new VaultContainer<>("hashicorp/vault:1.17.2")
        .withVaultToken("test-token")
        .withVaultPort(8200)
        .withInitCommand("secrets enable transit");

    private VaultCryptoConfiguration config;
    private VaultEncryptingMaterialsProvider encryptingProvider;
    private VaultDecryptingMaterialsProvider decryptingProvider;

    @BeforeEach
    void setUp() {
        // Configure providers to use the test Vault container
        config =
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("http://" + vaultContainer.getHost() + ":" + vaultContainer.getMappedPort(8200))
                .vaultToken("test-token")
                .transitEnginePath("transit")
                .keyPrefix("pi2schema-test")
                .connectionTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .retryBackoffMs(Duration.ofMillis(100))
                .build();

        encryptingProvider = new VaultEncryptingMaterialsProvider(config);
        decryptingProvider = new VaultDecryptingMaterialsProvider(config);

        logger.info("Integration test setup complete with Vault at: {}", config.getVaultUrl());
    }

    @AfterEach
    void tearDown() {
        if (encryptingProvider != null) {
            encryptingProvider.close();
        }
        if (decryptingProvider != null) {
            decryptingProvider.close();
        }
    }

    /**
     * Test complete encrypt/decrypt cycle with subject isolation.
     * Requirements: 4.1, 6.1
     */
    @Test
    void testCompleteEncryptDecryptCycle() throws Exception {
        // Given
        String subjectId = "user-integration-test-001";
        String testData = "This is sensitive personal data for integration testing";

        // When - Encrypt data
        EncryptionMaterial encryptionMaterial = encryptingProvider
            .encryptionKeysFor(subjectId)
            .get(10, TimeUnit.SECONDS);

        assertNotNull(encryptionMaterial);
        assertNotNull(encryptionMaterial.dataEncryptionKey());
        assertNotNull(encryptionMaterial.encryptedDataKey());
        assertNotNull(encryptionMaterial.encryptionContext());

        // Encrypt the test data using the DEK
        Aead encryptingAead = encryptionMaterial.dataEncryptionKey();
        byte[] encryptedData = encryptingAead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // When - Decrypt data
        Aead decryptingAead = decryptingProvider
            .decryptionKeysFor(subjectId, encryptionMaterial.encryptedDataKey(), encryptionMaterial.encryptionContext())
            .get(10, TimeUnit.SECONDS);

        assertNotNull(decryptingAead);

        // Decrypt the test data using the reconstructed DEK
        byte[] decryptedData = decryptingAead.decrypt(encryptedData, null);
        String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

        // Then
        assertEquals(testData, decryptedText);
        logger.info("Successfully completed encrypt/decrypt cycle for subject: {}", subjectId);
    }

    /**
     * Test subject isolation - different subjects should have different keys.
     * Requirements: 6.1, 6.3
     */
    @Test
    void testSubjectIsolation() throws Exception {
        // Given
        String subjectId1 = "user-isolation-test-001";
        String subjectId2 = "user-isolation-test-002";
        String testData = "Subject isolation test data";

        // When - Encrypt data for subject 1
        EncryptionMaterial material1 = encryptingProvider.encryptionKeysFor(subjectId1).get(10, TimeUnit.SECONDS);
        Aead aead1 = material1.dataEncryptionKey();
        byte[] encryptedData1 = aead1.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // When - Encrypt data for subject 2
        EncryptionMaterial material2 = encryptingProvider.encryptionKeysFor(subjectId2).get(10, TimeUnit.SECONDS);
        Aead aead2 = material2.dataEncryptionKey();
        byte[] encryptedData2 = aead2.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // Then - Different subjects should have different encryption materials
        assertNotEquals(material1.encryptionContext(), material2.encryptionContext());
        assertFalse(java.util.Arrays.equals(material1.encryptedDataKey(), material2.encryptedDataKey()));
        assertFalse(java.util.Arrays.equals(encryptedData1, encryptedData2));

        // Verify subject 1 cannot decrypt subject 2's data
        Aead decryptingAead1 = decryptingProvider
            .decryptionKeysFor(subjectId1, material1.encryptedDataKey(), material1.encryptionContext())
            .get(10, TimeUnit.SECONDS);

        // This should work - subject 1 decrypting their own data
        byte[] decrypted1 = decryptingAead1.decrypt(encryptedData1, null);
        assertEquals(testData, new String(decrypted1, StandardCharsets.UTF_8));

        // This should fail - subject 1 trying to decrypt subject 2's data with wrong key
        assertThrows(
            Exception.class,
            () -> {
                decryptingAead1.decrypt(encryptedData2, null);
            }
        );

        logger.info("Successfully verified subject isolation between {} and {}", subjectId1, subjectId2);
    }

    /**
     * Test GDPR compliance scenario - key deletion makes data inaccessible.
     * Requirements: 6.4, 6.5
     */
    @Test
    void testGdprKeyDeletionScenario() throws Exception {
        // Given
        String subjectId = "user-gdpr-test-001";
        String sensitiveData = "Personal data that must be deletable for GDPR compliance";

        // When - Encrypt data
        EncryptionMaterial material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);
        Aead encryptingAead = material.dataEncryptionKey();
        byte[] encryptedData = encryptingAead.encrypt(sensitiveData.getBytes(StandardCharsets.UTF_8), null);

        // Verify data can be decrypted initially
        Aead decryptingAead = decryptingProvider
            .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
            .get(10, TimeUnit.SECONDS);

        byte[] decryptedData = decryptingAead.decrypt(encryptedData, null);
        assertEquals(sensitiveData, new String(decryptedData, StandardCharsets.UTF_8));

        // Simulate GDPR deletion by deleting the subject's key from Vault
        // Note: In a real implementation, this would be done through a separate GDPR deletion service
        VaultTransitClient transitClient = new VaultTransitClient(config);
        String keyName = transitClient.generateKeyName(subjectId);

        // Delete the key (this simulates the GDPR right-to-be-forgotten)
        // We'll use the Vault API directly to delete the key
        deleteVaultKey(keyName);
        transitClient.close();

        // Then - Attempting to decrypt should fail because the key no longer exists
        CompletableFuture<Aead> decryptionFuture = decryptingProvider.decryptionKeysFor(
            subjectId,
            material.encryptedDataKey(),
            material.encryptionContext()
        );

        // The decryption should fail with a SubjectKeyNotFoundException
        Exception exception = assertThrows(
            Exception.class,
            () -> {
                decryptionFuture.get(10, TimeUnit.SECONDS);
            }
        );

        // Verify the exception indicates the key was not found
        assertTrue(
            exception.getCause() instanceof SubjectKeyNotFoundException ||
            exception.getMessage().contains("404") ||
            exception.getMessage().contains("not found"),
            "Expected SubjectKeyNotFoundException or 404 error, but got: " + exception.getMessage()
        );

        logger.info("Successfully verified GDPR key deletion scenario for subject: {}", subjectId);
    }

    /**
     * Test concurrent operations and performance characteristics.
     * Requirements: 4.2
     */
    @Test
    @Timeout(60) // 60 second timeout for performance test
    void testConcurrentOperationsAndPerformance() throws Exception {
        // Given
        int numberOfThreads = 5;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads * operationsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Exception> firstException = new AtomicReference<>();
        List<Long> operationTimes = new ArrayList<>();

        logger.info(
            "Starting concurrent operations test with {} threads, {} operations per thread",
            numberOfThreads,
            operationsPerThread
        );

        // When - Execute concurrent encrypt/decrypt operations
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        long startTime = System.currentTimeMillis();

                        String subjectId = "user-concurrent-" + threadId + "-" + j;
                        String testData = "Concurrent test data for " + subjectId;

                        // Encrypt
                        EncryptionMaterial material = encryptingProvider
                            .encryptionKeysFor(subjectId)
                            .get(30, TimeUnit.SECONDS);

                        Aead encryptingAead = material.dataEncryptionKey();
                        byte[] encryptedData = encryptingAead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

                        // Decrypt
                        Aead decryptingAead = decryptingProvider
                            .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
                            .get(30, TimeUnit.SECONDS);

                        byte[] decryptedData = decryptingAead.decrypt(encryptedData, null);
                        String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

                        // Verify
                        assertEquals(testData, decryptedText);

                        long operationTime = System.currentTimeMillis() - startTime;
                        synchronized (operationTimes) {
                            operationTimes.add(operationTime);
                        }

                        successCount.incrementAndGet();

                        if (j % 5 == 0) {
                            logger.debug("Thread {} completed operation {} in {}ms", threadId, j, operationTime);
                        }
                    } catch (Exception e) {
                        firstException.compareAndSet(null, e);
                        logger.error("Concurrent operation failed in thread {} operation {}", threadId, j, e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Then - Wait for all operations to complete
        assertTrue(latch.await(45, TimeUnit.SECONDS), "Operations did not complete within timeout");

        if (firstException.get() != null) {
            fail("Concurrent operation failed: " + firstException.get().getMessage(), firstException.get());
        }

        assertEquals(numberOfThreads * operationsPerThread, successCount.get());

        // Performance analysis
        double averageTime = operationTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxTime = operationTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
        long minTime = operationTimes.stream().mapToLong(Long::longValue).min().orElse(0L);

        logger.info("Concurrent operations completed successfully:");
        logger.info("  Total operations: {}", successCount.get());
        logger.info("  Average time per operation: {:.2f}ms", averageTime);
        logger.info("  Min time: {}ms", minTime);
        logger.info("  Max time: {}ms", maxTime);

        // Performance assertions - these are reasonable expectations for Vault operations
        assertTrue(
            averageTime < 5000,
            "Average operation time should be less than 5 seconds, was: " + averageTime + "ms"
        );
        assertTrue(maxTime < 10000, "Max operation time should be less than 10 seconds, was: " + maxTime + "ms");

        executor.shutdown();
    }

    /**
     * Test encryption context validation during decryption.
     * Requirements: 2.2, 2.5
     */
    @Test
    void testEncryptionContextValidation() throws Exception {
        // Given
        String subjectId = "user-context-test-001";

        // When - Create encryption material
        EncryptionMaterial material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);

        // Then - Valid context should work
        assertDoesNotThrow(() -> {
            decryptingProvider
                .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
                .get(10, TimeUnit.SECONDS);
        });

        // Invalid context should fail
        CompletableFuture<Aead> invalidContextFuture = decryptingProvider.decryptionKeysFor(
            subjectId,
            material.encryptedDataKey(),
            "invalid-context-format"
        );

        Exception exception = assertThrows(
            Exception.class,
            () -> {
                invalidContextFuture.get(10, TimeUnit.SECONDS);
            }
        );

        assertTrue(
            exception.getCause() instanceof InvalidEncryptionContextException,
            "Expected InvalidEncryptionContextException, but got: " + exception.getCause()
        );

        // Wrong subject ID in context should fail
        String wrongSubjectContext = material.encryptionContext().replace(subjectId, "wrong-subject");
        CompletableFuture<Aead> wrongSubjectFuture = decryptingProvider.decryptionKeysFor(
            subjectId,
            material.encryptedDataKey(),
            wrongSubjectContext
        );

        Exception wrongSubjectException = assertThrows(
            Exception.class,
            () -> {
                wrongSubjectFuture.get(10, TimeUnit.SECONDS);
            }
        );

        assertTrue(
            wrongSubjectException.getCause() instanceof InvalidEncryptionContextException,
            "Expected InvalidEncryptionContextException for wrong subject, but got: " + wrongSubjectException.getCause()
        );

        logger.info("Successfully verified encryption context validation");
    }

    /**
     * Test provider resource lifecycle and cleanup.
     * Requirements: 3.3
     */
    @Test
    void testProviderResourceLifecycle() throws Exception {
        // Given
        String subjectId = "user-lifecycle-test-001";

        // When - Use providers normally
        EncryptionMaterial material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);
        assertNotNull(material);

        Aead aead = decryptingProvider
            .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
            .get(10, TimeUnit.SECONDS);
        assertNotNull(aead);

        // Then - Closing should work without exceptions
        assertDoesNotThrow(() -> {
            encryptingProvider.close();
            decryptingProvider.close();
        });

        // Multiple closes should be safe
        assertDoesNotThrow(() -> {
            encryptingProvider.close();
            decryptingProvider.close();
        });

        logger.info("Successfully verified provider resource lifecycle");
    }

    /**
     * Test error handling with invalid Vault configuration.
     * Requirements: 5.1, 5.2, 5.3
     */
    @Test
    void testErrorHandlingWithInvalidConfiguration() {
        // Test with invalid Vault URL
        VaultCryptoConfiguration invalidConfig = VaultCryptoConfiguration
            .builder()
            .vaultUrl("http://invalid-vault-host:8200")
            .vaultToken("test-token")
            .connectionTimeout(Duration.ofSeconds(2)) // Short timeout for faster test
            .requestTimeout(Duration.ofSeconds(5))
            .maxRetries(1) // Fewer retries for faster test
            .build();

        VaultEncryptingMaterialsProvider invalidProvider = new VaultEncryptingMaterialsProvider(invalidConfig);

        // When - Try to encrypt with invalid Vault
        CompletableFuture<EncryptionMaterial> future = invalidProvider.encryptionKeysFor("test-subject");

        // Then - Should fail with connectivity exception
        Exception exception = assertThrows(
            Exception.class,
            () -> {
                future.get(15, TimeUnit.SECONDS);
            }
        );

        assertTrue(
            exception.getCause() instanceof VaultConnectivityException ||
            exception.getMessage().contains("ConnectException") ||
            exception.getMessage().contains("connection"),
            "Expected connectivity-related exception, but got: " + exception.getMessage()
        );

        invalidProvider.close();
        logger.info("Successfully verified error handling with invalid configuration");
    }

    /**
     * Helper method to delete a Vault key for GDPR testing.
     * This simulates the GDPR right-to-be-forgotten by removing the key from Vault.
     */
    private void deleteVaultKey(String keyName) throws Exception {
        // Use Vault's HTTP API directly to delete the key
        String vaultUrl = config.getVaultUrl() + "/v1/" + config.getTransitEnginePath() + "/keys/" + keyName;

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest
            .newBuilder()
            .uri(java.net.URI.create(vaultUrl))
            .header("X-Vault-Token", config.getVaultToken())
            .DELETE()
            .build();

        java.net.http.HttpResponse<String> response = client.send(
            request,
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 204 && response.statusCode() != 404) {
            throw new RuntimeException("Failed to delete Vault key: " + response.statusCode() + " " + response.body());
        }

        logger.debug("Successfully deleted Vault key: {}", keyName);
    }
}
