package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for GDPR compliance methods in VaultTransitClient.
 * These tests focus on the key naming strategy and validation logic.
 */
class VaultTransitClientGdprTest {

    private VaultCryptoConfiguration config;
    private VaultTransitClient transitClient;

    @BeforeEach
    void setUp() {
        config =
            VaultCryptoConfiguration
                .builder()
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
        var subjectId = "user-123";
        var expectedKeyName = "test-prefix_subject_user-123";

        var actualKeyName = transitClient.generateKeyName(subjectId);
        assertThat(actualKeyName).isEqualTo(expectedKeyName);
    }

    @Test
    void testGenerateKeyNameWithSpecialCharacters() {
        var subjectId = "user@domain.com";
        var expectedKeyName = "test-prefix_subject_user_domain_com";

        var actualKeyName = transitClient.generateKeyName(subjectId);
        assertThat(actualKeyName).isEqualTo(expectedKeyName);
    }

    @Test
    void testGenerateKeyNameWithNullSubjectId() {
        assertThatThrownBy(() -> transitClient.generateKeyName(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGenerateKeyNameWithEmptySubjectId() {
        assertThatThrownBy(() -> transitClient.generateKeyName("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSubjectKeyExistsWithNullSubjectId() {
        assertThatThrownBy(() -> transitClient.subjectKeyExists(null).get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSubjectKeyExistsWithEmptySubjectId() {
        assertThatThrownBy(() -> transitClient.subjectKeyExists("").get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDeleteSubjectKeyWithNullSubjectId() {
        assertThatThrownBy(() -> transitClient.deleteSubjectKey(null).get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDeleteSubjectKeyWithEmptySubjectId() {
        assertThatThrownBy(() -> transitClient.deleteSubjectKey("").get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
