package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GDPR compliance methods in VaultTransitClient.
 * These tests focus on the key naming strategy and validation logic.
 */
class VaultTransitClientGdprTest {

    private VaultCryptoConfiguration config;
    private VaultTransitClient transitClient;

    @BeforeEach
    void setUp() {
        config = VaultCryptoConfiguration.builder()
            .vaultUrl("http://localhost:8200")
            .vaultToken("test-token")
            .transitEnginePath("transit")
            .keyPrefix("test-prefix")
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(10))
            .maxRetries(0) // Disable retries for unit tests
            .retryBackoffMs(Duration.ofMillis(0))
            .build();

        transitClient = new VaultTransitClient(config);
    }

    @Test
    void testGenerateKeyName() {
        String subjectId = "user-123";
        String expectedKeyName = "test-prefix/subject/user-123";
        
        String actualKeyName = transitClient.generateKeyName(subjectId);
        assertEquals(expectedKeyName, actualKeyName);
    }

    @Test
    void testGenerateKeyNameWithSpecialCharacters() {
        String subjectId = "user@domain.com";
        String expectedKeyName = "test-prefix/subject/user_domain_com";
        
        String actualKeyName = transitClient.generateKeyName(subjectId);
        assertEquals(expectedKeyName, actualKeyName);
    }

    @Test
    void testGenerateKeyNameWithNullSubjectId() {
        assertThrows(IllegalArgumentException.class, () -> {
            transitClient.generateKeyName(null);
        });
    }

    @Test
    void testGenerateKeyNameWithEmptySubjectId() {
        assertThrows(IllegalArgumentException.class, () -> {
            transitClient.generateKeyName("");
        });
    }

    @Test
    void testSubjectKeyExistsWithNullSubjectId() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            transitClient.subjectKeyExists(null).get();
        });
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testSubjectKeyExistsWithEmptySubjectId() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            transitClient.subjectKeyExists("").get();
        });
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testDeleteSubjectKeyWithNullSubjectId() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            transitClient.deleteSubjectKey(null).get();
        });
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testDeleteSubjectKeyWithEmptySubjectId() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            transitClient.deleteSubjectKey("").get();
        });
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
}