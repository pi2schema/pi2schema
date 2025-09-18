package pi2schema.crypto.providers.vault;

/**
 * Exception thrown when there are connectivity issues with Vault.
 * This includes network timeouts, connection refused, and other network-related errors.
 */
public class VaultConnectivityException extends VaultCryptoException {

    /**
     * Constructs a new VaultConnectivityException with the specified detail message.
     *
     * @param message the detail message
     */
    public VaultConnectivityException(String message) {
        super(message);
    }

    /**
     * Constructs a new VaultConnectivityException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public VaultConnectivityException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new VaultConnectivityException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public VaultConnectivityException(Throwable cause) {
        super(cause);
    }
}
