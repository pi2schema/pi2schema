package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultAuthenticationExceptionTest {

    @Test
    @DisplayName("Should create authentication exception with message")
    void shouldCreateAuthenticationExceptionWithMessage() {
        String message = "Authentication failed";
        VaultAuthenticationException exception = new VaultAuthenticationException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create authentication exception with message and cause")
    void shouldCreateAuthenticationExceptionWithMessageAndCause() {
        String message = "Authentication failed";
        Throwable cause = new RuntimeException("Invalid token");
        VaultAuthenticationException exception = new VaultAuthenticationException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should create authentication exception with cause only")
    void shouldCreateAuthenticationExceptionWithCauseOnly() {
        Throwable cause = new RuntimeException("Invalid token");
        VaultAuthenticationException exception = new VaultAuthenticationException(cause);

        assertEquals(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("RuntimeException"));
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        VaultAuthenticationException exception = new VaultAuthenticationException("Test message");
        assertInstanceOf(VaultCryptoException.class, exception);
        assertInstanceOf(RuntimeException.class, exception);
    }
}
