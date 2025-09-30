package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaultCryptoExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void shouldCreateExceptionWithMessage() {
        var message = "Test error message";
        var exception = new VaultCryptoException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        var message = "Test error message";
        var cause = new RuntimeException("Root cause");
        var exception = new VaultCryptoException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create exception with cause only")
    void shouldCreateExceptionWithCauseOnly() {
        var cause = new RuntimeException("Root cause");
        var exception = new VaultCryptoException(cause);

        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getMessage()).contains("RuntimeException");
    }

    @Test
    @DisplayName("Should be instance of RuntimeException")
    void shouldBeInstanceOfRuntimeException() {
        var exception = new VaultCryptoException("Test message");
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
