package pi2schema.crypto.providers.vault;

/**
 * Base exception for all Vault crypto provider related errors.
 * This exception serves as the parent for all specific Vault-related exceptions.
 */
public class VaultCryptoException extends RuntimeException {

    /**
     * Constructs a new VaultCryptoException with the specified detail message.
     *
     * @param message the detail message
     */
    public VaultCryptoException(String message) {
        super(message);
    }

    /**
     * Constructs a new VaultCryptoException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public VaultCryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new VaultCryptoException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public VaultCryptoException(Throwable cause) {
        super(cause);
    }
}
