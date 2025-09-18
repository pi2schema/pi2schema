package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultConnectivityExceptionTest {

    @Test
    @DisplayName("Should create connectivity exception with message")
    void shouldCreateConnectivityExceptionWithMessage() {
        String message = "Connection timeout";
        VaultConnectivityException exception = new VaultConnectivityException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create connectivity exception with message and cause")
    void shouldCreateConnectivityExceptionWithMessageAndCause() {
        String message = "Connection timeout";
        Throwable cause = new RuntimeException("Network unreachable");
        VaultConnectivityException exception = new VaultConnectivityException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should create connectivity exception with cause only")
    void shouldCreateConnectivityExceptionWithCauseOnly() {
        Throwable cause = new RuntimeException("Network unreachable");
        VaultConnectivityException exception = new VaultConnectivityException(cause);

        assertEquals(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("RuntimeException"));
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        VaultConnectivityException exception = new VaultConnectivityException("Test message");
        assertInstanceOf(VaultCryptoException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }
}
