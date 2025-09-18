package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubjectKeyNotFoundExceptionTest {

    @Test
    @DisplayName("Should create subject key not found exception with subject ID and message")
    void shouldCreateSubjectKeyNotFoundExceptionWithSubjectIdAndMessage() {
        String subjectId = "user-123";
        String message = "Key not found for subject";
        SubjectKeyNotFoundException exception = new SubjectKeyNotFoundException(subjectId, message);

        assertEquals(message, exception.getMessage());
        assertEquals(subjectId, exception.getSubjectId());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create subject key not found exception with subject ID, message and cause")
    void shouldCreateSubjectKeyNotFoundExceptionWithSubjectIdMessageAndCause() {
        String subjectId = "user-123";
        String message = "Key not found for subject";
        Throwable cause = new RuntimeException("Vault key missing");
        SubjectKeyNotFoundException exception = new SubjectKeyNotFoundException(subjectId, message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(subjectId, exception.getSubjectId());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should preserve subject ID in exception")
    void shouldPreserveSubjectIdInException() {
        String subjectId = "user-456";
        SubjectKeyNotFoundException exception = new SubjectKeyNotFoundException(subjectId, "Test message");

        assertEquals(subjectId, exception.getSubjectId());
    }

    @Test
    @DisplayName("Should handle null subject ID")
    void shouldHandleNullSubjectId() {
        SubjectKeyNotFoundException exception = new SubjectKeyNotFoundException(null, "Test message");

        assertNull(exception.getSubjectId());
        assertEquals("Test message", exception.getMessage());
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        SubjectKeyNotFoundException exception = new SubjectKeyNotFoundException("user-123", "Test message");
        assertInstanceOf(VaultCryptoException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }
}
