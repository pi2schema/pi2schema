package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

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
        config =
            VaultCryptoConfiguration
                .builder()
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
        var subjectId = "user-simple-test-001";
        var testData = "Simple integration test data";

        // When - Encrypt data
        var encryptionMaterial = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);

        assertThat(encryptionMaterial).isNotNull();
        assertThat(encryptionMaterial.dataEncryptionKey()).isNotNull();
        assertThat(encryptionMaterial.encryptedDataKey()).isNotNull();
        assertThat(encryptionMaterial.encryptionContext()).isNotNull();

        // Encrypt the test data using the DEK
        var encryptingAead = encryptionMaterial.dataEncryptionKey();
        var encryptedData = encryptingAead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        // When - Decrypt data
        var decryptingAead = decryptingProvider
            .decryptionKeysFor(subjectId, encryptionMaterial.encryptedDataKey(), encryptionMaterial.encryptionContext())
            .get(10, TimeUnit.SECONDS);

        assertThat(decryptingAead).isNotNull();

        // Decrypt the test data using the reconstructed DEK
        var decryptedData = decryptingAead.decrypt(encryptedData, null);
        var decryptedText = new String(decryptedData, StandardCharsets.UTF_8);

        // Then
        assertThat(decryptedText).isEqualTo(testData);
        logger.info("Successfully completed basic encrypt/decrypt cycle for subject: {}", subjectId);
    }

    /**
     * Test subject isolation with local Vault.
     */
    @Test
    void testSubjectIsolation() throws Exception {
        // Given
        var subjectId1 = "user-simple-isolation-001";
        var subjectId2 = "user-simple-isolation-002";
        var testData = "Subject isolation test data";

        // When - Encrypt data for both subjects
        var material1 = encryptingProvider.encryptionKeysFor(subjectId1).get(10, TimeUnit.SECONDS);
        var material2 = encryptingProvider.encryptionKeysFor(subjectId2).get(10, TimeUnit.SECONDS);

        // Then - Different subjects should have different encryption materials
        assertThat(material1.encryptionContext()).isNotEqualTo(material2.encryptionContext());
        assertThat(material1.encryptedDataKey()).isNotEqualTo(material2.encryptedDataKey());

        // Verify each subject can decrypt their own data
        var aead1 = material1.dataEncryptionKey();
        var encryptedData1 = aead1.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);

        var decryptingAead1 = decryptingProvider
            .decryptionKeysFor(subjectId1, material1.encryptedDataKey(), material1.encryptionContext())
            .get(10, TimeUnit.SECONDS);

        var decrypted1 = decryptingAead1.decrypt(encryptedData1, null);
        assertThat(new String(decrypted1, StandardCharsets.UTF_8)).isEqualTo(testData);

        logger.info("Successfully verified subject isolation between {} and {}", subjectId1, subjectId2);
    }

    /**
     * Test encryption context validation.
     */
    @Test
    void testEncryptionContextValidation() throws Exception {
        // Given
        var subjectId = "user-context-validation-001";

        // When - Create encryption material
        var material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);

        // Then - Valid context should work
        assertThatCode(() ->
                decryptingProvider
                    .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
                    .get(10, TimeUnit.SECONDS)
            )
            .doesNotThrowAnyException();

        // Invalid context should fail
        assertThatThrownBy(() ->
                decryptingProvider
                    .decryptionKeysFor(subjectId, material.encryptedDataKey(), "invalid-context-format")
                    .get(10, TimeUnit.SECONDS)
            )
            .hasCauseInstanceOf(InvalidEncryptionContextException.class);

        logger.info("Successfully verified encryption context validation");
    }

    /**
     * Test provider resource lifecycle.
     */
    @Test
    void testProviderResourceLifecycle() throws Exception {
        // Given
        var subjectId = "user-lifecycle-001";

        // When - Use providers normally
        var material = encryptingProvider.encryptionKeysFor(subjectId).get(10, TimeUnit.SECONDS);
        assertThat(material).isNotNull();

        var aead = decryptingProvider
            .decryptionKeysFor(subjectId, material.encryptedDataKey(), material.encryptionContext())
            .get(10, TimeUnit.SECONDS);
        assertThat(aead).isNotNull();

        // Then - Closing should work without exceptions
        assertThatCode(() -> {
                encryptingProvider.close();
                decryptingProvider.close();
            })
            .doesNotThrowAnyException();

        // Multiple closes should be safe
        assertThatCode(() -> {
                encryptingProvider.close();
                decryptingProvider.close();
            })
            .doesNotThrowAnyException();

        logger.info("Successfully verified provider resource lifecycle");
    }
}
