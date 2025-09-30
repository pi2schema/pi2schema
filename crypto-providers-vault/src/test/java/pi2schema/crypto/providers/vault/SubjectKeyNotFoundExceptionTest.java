package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectKeyNotFoundExceptionTest {

    @Test
    @DisplayName("Should create subject key not found exception with subject ID and message")
    void shouldCreateSubjectKeyNotFoundExceptionWithSubjectIdAndMessage() {
        var subjectId = "user-123";
        var message = "Key not found for subject";
        var exception = new SubjectKeyNotFoundException(subjectId, message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getSubjectId()).isEqualTo(subjectId);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create subject key not found exception with subject ID, message and cause")
    void shouldCreateSubjectKeyNotFoundExceptionWithSubjectIdMessageAndCause() {
        var subjectId = "user-123";
        var message = "Key not found for subject";
        var cause = new RuntimeException("Vault key missing");
        var exception = new SubjectKeyNotFoundException(subjectId, message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getSubjectId()).isEqualTo(subjectId);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should preserve subject ID in exception")
    void shouldPreserveSubjectIdInException() {
        var subjectId = "user-456";
        var exception = new SubjectKeyNotFoundException(subjectId, "Test message");

        assertThat(exception.getSubjectId()).isEqualTo(subjectId);
    }

    @Test
    @DisplayName("Should handle null subject ID")
    void shouldHandleNullSubjectId() {
        var exception = new SubjectKeyNotFoundException(null, "Test message");

        assertThat(exception.getSubjectId()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Test message");
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        var exception = new SubjectKeyNotFoundException("user-123", "Test message");
        assertThat(exception).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
    }
}
