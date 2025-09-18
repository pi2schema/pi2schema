package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultCryptoExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void shouldCreateExceptionWithMessage() {
        String message = "Test error message";
        VaultCryptoException exception = new VaultCryptoException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        VaultCryptoException exception = new VaultCryptoException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should create exception with cause only")
    void shouldCreateExceptionWithCauseOnly() {
        Throwable cause = new RuntimeException("Root cause");
        VaultCryptoException exception = new VaultCryptoException(cause);

        assertEquals(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("RuntimeException"));
    }

    @Test
    @DisplayName("Should be instance of RuntimeException")
    void shouldBeInstanceOfRuntimeException() {
        VaultCryptoException exception = new VaultCryptoException("Test message");
        assertInstanceOf(RuntimeException.class, exception);
    }
}
