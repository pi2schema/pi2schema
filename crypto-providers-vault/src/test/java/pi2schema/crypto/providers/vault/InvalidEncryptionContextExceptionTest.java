package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvalidEncryptionContextExceptionTest {

    @Test
    @DisplayName("Should create invalid encryption context exception with context and message")
    void shouldCreateInvalidEncryptionContextExceptionWithContextAndMessage() {
        String encryptionContext = "invalid-context";
        String message = "Invalid encryption context format";
        InvalidEncryptionContextException exception = new InvalidEncryptionContextException(encryptionContext, message);

        assertEquals(message, exception.getMessage());
        assertEquals(encryptionContext, exception.getEncryptionContext());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create invalid encryption context exception with context, message and cause")
    void shouldCreateInvalidEncryptionContextExceptionWithContextMessageAndCause() {
        String encryptionContext = "invalid-context";
        String message = "Invalid encryption context format";
        Throwable cause = new RuntimeException("Parse error");
        InvalidEncryptionContextException exception = new InvalidEncryptionContextException(
            encryptionContext,
            message,
            cause
        );

        assertEquals(message, exception.getMessage());
        assertEquals(encryptionContext, exception.getEncryptionContext());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should preserve encryption context in exception")
    void shouldPreserveEncryptionContextInException() {
        String encryptionContext = "subjectId=user-123;timestamp=invalid";
        InvalidEncryptionContextException exception = new InvalidEncryptionContextException(
            encryptionContext,
            "Test message"
        );

        assertEquals(encryptionContext, exception.getEncryptionContext());
    }

    @Test
    @DisplayName("Should handle null encryption context")
    void shouldHandleNullEncryptionContext() {
        InvalidEncryptionContextException exception = new InvalidEncryptionContextException(null, "Test message");

        assertNull(exception.getEncryptionContext());
        assertEquals("Test message", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle empty encryption context")
    void shouldHandleEmptyEncryptionContext() {
        String encryptionContext = "";
        InvalidEncryptionContextException exception = new InvalidEncryptionContextException(
            encryptionContext,
            "Test message"
        );

        assertEquals(encryptionContext, exception.getEncryptionContext());
        assertEquals("Test message", exception.getMessage());
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        InvalidEncryptionContextException exception = new InvalidEncryptionContextException(
            "invalid-context",
            "Test message"
        );
        assertInstanceOf(VaultCryptoException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }
}
