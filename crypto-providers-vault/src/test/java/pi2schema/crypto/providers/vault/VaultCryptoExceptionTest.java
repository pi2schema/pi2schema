package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for all Vault crypto exception types.
 * This consolidated test class covers all exception constructors and inheritance relationships.
 */
class VaultCryptoExceptionTest {

    @ParameterizedTest
    @MethodSource("exceptionConstructors")
    @DisplayName("Should create exception with message")
    void shouldCreateExceptionWithMessage(String exceptionType, Function<String, VaultCryptoException> constructor) {
        var message = "Test error message for " + exceptionType;
        var exception = constructor.apply(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @ParameterizedTest
    @MethodSource("exceptionConstructorsWithCause")
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause(
        String exceptionType,
        java.util.function.BiFunction<String, Throwable, VaultCryptoException> constructor
    ) {
        var message = "Test error message for " + exceptionType;
        var cause = new RuntimeException("Root cause");
        var exception = constructor.apply(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @ParameterizedTest
    @MethodSource("exceptionCauseConstructors")
    @DisplayName("Should create exception with cause only")
    void shouldCreateExceptionWithCauseOnly(
        String exceptionType,
        Function<Throwable, VaultCryptoException> constructor
    ) {
        var cause = new RuntimeException("Root cause");
        var exception = constructor.apply(cause);

        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getMessage()).contains("RuntimeException");
    }

    @Test
    @DisplayName("Should verify exception inheritance hierarchy")
    void shouldVerifyExceptionInheritanceHierarchy() {
        var vaultCryptoException = new VaultCryptoException("Test message");
        var authException = new VaultAuthenticationException("Auth failed");
        var connectivityException = new VaultConnectivityException("Connection failed");
        var keyNotFoundException = new SubjectKeyNotFoundException("user-123", "Key not found");

        // All should be instances of VaultCryptoException and RuntimeException
        assertThat(vaultCryptoException).isInstanceOf(RuntimeException.class);
        assertThat(authException).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
        assertThat(connectivityException).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
        assertThat(keyNotFoundException).isInstanceOf(VaultCryptoException.class).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle special exception properties")
    void shouldHandleSpecialExceptionProperties() {
        // InvalidEncryptionContextException removed from MVP

        // Test SubjectKeyNotFoundException special properties
        var subjectId = "user-456";
        var keyNotFoundException = new SubjectKeyNotFoundException(subjectId, "Key not found");
        assertThat(keyNotFoundException.getSubjectId()).isEqualTo(subjectId);

        // Test with null subject ID
        var nullSubjectException = new SubjectKeyNotFoundException(null, "Key not found");
        assertThat(nullSubjectException.getSubjectId()).isNull();
    }

    // Data providers for parameterized tests
    static Stream<Arguments> exceptionConstructors() {
        return Stream.of(
            Arguments.of("VaultCryptoException", (Function<String, VaultCryptoException>) VaultCryptoException::new),
            Arguments.of(
                "VaultAuthenticationException",
                (Function<String, VaultCryptoException>) VaultAuthenticationException::new
            ),
            Arguments.of(
                "VaultConnectivityException",
                (Function<String, VaultCryptoException>) VaultConnectivityException::new
            )
        );
    }

    static Stream<Arguments> exceptionConstructorsWithCause() {
        return Stream.of(
            Arguments.of(
                "VaultCryptoException",
                (java.util.function.BiFunction<String, Throwable, VaultCryptoException>) VaultCryptoException::new
            ),
            Arguments.of(
                "VaultAuthenticationException",
                (java.util.function.BiFunction<
                        String,
                        Throwable,
                        VaultCryptoException
                    >) VaultAuthenticationException::new
            ),
            Arguments.of(
                "VaultConnectivityException",
                (java.util.function.BiFunction<String, Throwable, VaultCryptoException>) VaultConnectivityException::new
            ),
            Arguments.of(
                "SubjectKeyNotFoundException",
                (java.util.function.BiFunction<String, Throwable, VaultCryptoException>) (msg, cause) ->
                    new SubjectKeyNotFoundException("test-subject", msg, cause)
            )
        );
    }

    static Stream<Arguments> exceptionCauseConstructors() {
        return Stream.of(
            Arguments.of("VaultCryptoException", (Function<Throwable, VaultCryptoException>) VaultCryptoException::new),
            Arguments.of(
                "VaultAuthenticationException",
                (Function<Throwable, VaultCryptoException>) VaultAuthenticationException::new
            ),
            Arguments.of(
                "VaultConnectivityException",
                (Function<Throwable, VaultCryptoException>) VaultConnectivityException::new
            )
        );
    }
}
