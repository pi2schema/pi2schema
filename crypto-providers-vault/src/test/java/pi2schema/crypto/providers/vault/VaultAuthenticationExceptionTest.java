package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaultAuthenticationExceptionTest {

    @Test
    @DisplayName("Should create authentication exception with message")
    void shouldCreateAuthenticationExceptionWithMessage() {
        var message = "Authentication failed";
        var exception = new VaultAuthenticationException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create authentication exception with message and cause")
    void shouldCreateAuthenticationExceptionWithMessageAndCause() {
        var message = "Authentication failed";
        var cause = new RuntimeException("Invalid token");
        var exception = new VaultAuthenticationException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create authentication exception with cause only")
    void shouldCreateAuthenticationExceptionWithCauseOnly() {
        var cause = new RuntimeException("Invalid token");
        var exception = new VaultAuthenticationException(cause);

        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getMessage()).contains("RuntimeException");
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        var exception = new VaultAuthenticationException("Test message");
        assertThat(exception).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
    }
}
