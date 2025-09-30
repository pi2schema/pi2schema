package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidEncryptionContextExceptionTest {

    @Test
    @DisplayName("Should create invalid encryption context exception with context and message")
    void shouldCreateInvalidEncryptionContextExceptionWithContextAndMessage() {
        var encryptionContext = "invalid-context";
        var message = "Invalid encryption context format";
        var exception = new InvalidEncryptionContextException(encryptionContext, message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getEncryptionContext()).isEqualTo(encryptionContext);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create invalid encryption context exception with context, message and cause")
    void shouldCreateInvalidEncryptionContextExceptionWithContextMessageAndCause() {
        var encryptionContext = "invalid-context";
        var message = "Invalid encryption context format";
        var cause = new RuntimeException("Parse error");
        var exception = new InvalidEncryptionContextException(encryptionContext, message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getEncryptionContext()).isEqualTo(encryptionContext);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should preserve encryption context in exception")
    void shouldPreserveEncryptionContextInException() {
        var encryptionContext = "subjectId=user-123;timestamp=invalid";
        var exception = new InvalidEncryptionContextException(encryptionContext, "Test message");

        assertThat(exception.getEncryptionContext()).isEqualTo(encryptionContext);
    }

    @Test
    @DisplayName("Should handle null encryption context")
    void shouldHandleNullEncryptionContext() {
        var exception = new InvalidEncryptionContextException(null, "Test message");

        assertThat(exception.getEncryptionContext()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Test message");
    }

    @Test
    @DisplayName("Should handle empty encryption context")
    void shouldHandleEmptyEncryptionContext() {
        var encryptionContext = "";
        var exception = new InvalidEncryptionContextException(encryptionContext, "Test message");

        assertThat(exception.getEncryptionContext()).isEqualTo(encryptionContext);
        assertThat(exception.getMessage()).isEqualTo("Test message");
    }

    @Test
    @DisplayName("Should be instance of VaultCryptoException")
    void shouldBeInstanceOfVaultCryptoException() {
        var exception = new InvalidEncryptionContextException("invalid-context", "Test message");
        assertThat(exception).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
    }
}
