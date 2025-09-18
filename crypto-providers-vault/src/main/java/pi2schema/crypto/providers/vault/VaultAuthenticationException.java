package pi2schema.crypto.providers.vault;

/**
 * Exception thrown when Vault authentication fails.
 * This includes invalid tokens, expired tokens, or insufficient permissions.
 */
public class VaultAuthenticationException extends VaultCryptoException {

    /**
     * Constructs a new VaultAuthenticationException with the specified detail message.
     *
     * @param message the detail message
     */
    public VaultAuthenticationException(String message) {
        super(message);
    }

    /**
     * Constructs a new VaultAuthenticationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public VaultAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new VaultAuthenticationException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public VaultAuthenticationException(Throwable cause) {
        super(cause);
    }
}
