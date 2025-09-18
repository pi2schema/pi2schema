package pi2schema.crypto.providers.vault;

/**
 * Exception thrown when the encryption context is invalid or cannot be parsed.
 * This includes malformed context strings, missing required fields, or validation failures.
 */
public class InvalidEncryptionContextException extends VaultCryptoException {

    private final String encryptionContext;

    /**
     * Constructs a new InvalidEncryptionContextException with the specified encryption context and detail message.
     *
     * @param encryptionContext the invalid encryption context
     * @param message the detail message
     */
    public InvalidEncryptionContextException(String encryptionContext, String message) {
        super(message);
        this.encryptionContext = encryptionContext;
    }

    /**
     * Constructs a new InvalidEncryptionContextException with the specified encryption context, detail message and cause.
     *
     * @param encryptionContext the invalid encryption context
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public InvalidEncryptionContextException(String encryptionContext, String message, Throwable cause) {
        super(message, cause);
        this.encryptionContext = encryptionContext;
    }

    /**
     * Returns the invalid encryption context that caused this exception.
     *
     * @return the encryption context
     */
    public String getEncryptionContext() {
        return encryptionContext;
    }
}
