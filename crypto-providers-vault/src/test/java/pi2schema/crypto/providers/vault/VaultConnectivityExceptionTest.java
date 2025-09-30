package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaultConnectivityExceptionTest {

    @Test
    @DisplayName("Should create connectivity exception with message")
    void shouldCreateConnectivityExceptionWithMessage() {
        var message = "Connection timeout";
        var exception = new VaultConnectivityException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create connectivity exception with message and cause")
    void shouldCreateConnectivityExceptionWithMessageAndCause() {
        var message = "Connection timeout";
        var cause = new RuntimeException("Network unreachable");
        var exception = new VaultConnectivityException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create connectivity exception with cause only")
    void shouldCreateConnectivityExceptionWithCauseOnly() {
        var cause = new RuntimeException("Network unreachable");
        var exception = new VaultConnectivityException(cause);

        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getMessage()).contains("RuntimeException");
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        var exception = new VaultConnectivityException("Test message");
        assertThat(exception).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
    }
}
