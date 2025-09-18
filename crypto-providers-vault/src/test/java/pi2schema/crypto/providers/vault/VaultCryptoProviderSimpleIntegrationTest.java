package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration tests for Vault crypto providers that can run against a local Vault instance.
 * 
 * To run these tests, start a local Vault instance:
 * 
 * 1. Start Vault in dev mode:
 *    vault server -dev -dev-root-token-id=test-token
 * 
 * 2. Enable transit engine:
 *    export VAULT_ADDR='http://127.0.0.1:8200'
 *    export VAULT_TOKEN='test-token'
 *    vault secrets enable transit
 * 
 * 3. Run tests with system property:
 *    ./gradlew test -Dvault.integration.enabled=true
 */
@EnabledIfSystemProperty(named = "vault.integration.enabled", matches = "true")
class VaultCryptoProviderSimpleIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(VaultCryptoProviderSimpleIntegrationTest.class);

    private VaultCryptoConfiguration config;
    private VaultEncryptingMaterialsProvider encryptingProvider;
    private VaultDecryptingMaterialsProvider decryptingProvider;

    @BeforeEach
    void setUp() {
        // Configure providers to use local Vault dev server
        config = VaultCryptoConfiguration.builder()
            .vaultUrl("http://127.0.0.1:8200")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("pi2schema-simple-test")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(10))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();

        encryptingProvider = new VaultEncryptingMaterialsProvider(config);
        decryptingProvider = new VaultDecryptingMaterialsProvider(config);

        logger.info("Simple integration test setup complete with local Vault at: {}", config.getVaultUrl());
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
     * Test basic encrypt/decrypt cycle with local Vault.
     */
    @Test
    void testBasicEncryptDecryptCycle() throws Exception {
        // Given
        String subjectId = "user-simple-test-001";
        String testData = "Simple integration test data";

        // When - Encrypt data
        EncryptionMaterial encryptionMaterial = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);
        
        assertNotNull(encryptionMaterial);
        assertNotNull(encryptionMaterial.dataEncryptionKey());
        assertNotNull(encryptionMaterial.encryptedDataKey());
        assertNotNull(encryptionMaterial.encryptionContext());

        // Encrypt the test data using the DEK
        Aead encryptingAead = encryptionMaterial.dataEncryptionKey();
        byte[] encryptedData = encryptingAead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // When - Decrypt data
        Aead decryptingAead = decryptingProvider.decryptionKeysFor(
            subjectId,
            encryptionMaterial.encryptedDataKey(),
            encryptionMaterial.encryptionContext()
        ).get(10, TimeUnit.SECONDS);

        assertNotNull(decryptingAead);

        // Decrypt the test data using the reconstructed DEK
        byte[] decryptedData = decryptingAead.decrypt(encryptedData, null);
        String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

        // Then
        assertEquals(testData, decryptedText);
        logger.info("Successfully completed basic encrypt/decrypt cycle for subject: {}", subjectId);
    }

    /**
     * Test subject isolation with local Vault.
     */
    @Test
    void testSubjectIsolation() throws Exception {
        // Given
        String subjectId1 = "user-simple-isolation-001";
        String subjectId2 = "user-simple-isolation-002";
        String testData = "Subject isolation test data";

        // When - Encrypt data for both subjects
        EncryptionMaterial material1 = encryptingProvider.encryptionKeysFor(subjectId1).get(10, TimeUnit.SECONDS);
        EncryptionMaterial material2 = encryptingProvider.encryptionKeysFor(subjectId2).get(10, TimeUnit.SECONDS);

        // Then - Different subjects should have different encryption materials
        assertNotEquals(material1.encryptionContext(), material2.encryptionContext());
        assertFalse(java.util.Arrays.equals(material1.encryptedDataKey(), material2.encryptedDataKey()));

        // Verify each subject can decrypt their own data
        Aead aead1 = material1.dataEncryptionKey();
        byte[] encryptedData1 = aead1.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        Aead decryptingAead1 = decryptingProvider.decryptionKeysFor(
            subjectId1,
            material1.encryptedDataKey(),
            material1.encryptionContext()
        ).get(10, TimeUnit.SECONDS);

        byte[] decrypted1 = decryptingAead1.decrypt(encryptedData1, null);
        assertEquals(testData, new String(decrypted1, StandardCharsets.UTF_8));

        logger.info("Successfully verified subject isolation between {} and {}", subjectId1, subjectId2);
    }

    /**
     * Test encryption context validation.
     */
    @Test
    void testEncryptionContextValidation() throws Exception {
        // Given
        String subjectId = "user-context-validation-001";
        
        // When - Create encryption material
        EncryptionMaterial material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);

        // Then - Valid context should work
        assertDoesNotThrow(() -> {
            decryptingProvider.decryptionKeysFor(
                subjectId,
                material.encryptedDataKey(),
                material.encryptionContext()
            ).get(10, TimeUnit.SECONDS);
        });

        // Invalid context should fail
        Exception exception = assertThrows(Exception.class, () -> {
            decryptingProvider.decryptionKeysFor(
                subjectId,
                material.encryptedDataKey(),
                "invalid-context-format"
            ).get(10, TimeUnit.SECONDS);
        });

        assertTrue(
            exception.getCause() instanceof InvalidEncryptionContextException,
            "Expected InvalidEncryptionContextException, but got: " + exception.getCause()
        );

        logger.info("Successfully verified encryption context validation");
    }

    /**
     * Test provider resource lifecycle.
     */
    @Test
    void testProviderResourceLifecycle() throws Exception {
        // Given
        String subjectId = "user-lifecycle-001";

        // When - Use providers normally
        EncryptionMaterial material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);
        assertNotNull(material);

        Aead aead = decryptingProvider.decryptionKeysFor(
            subjectId,
            material.encryptedDataKey(),
            material.encryptionContext()
        ).get(10, TimeUnit.SECONDS);
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
}