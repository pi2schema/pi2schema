package pi2schema.crypto.providers.vault;

/**
 * Exception thrown when a subject's encryption key cannot be found in Vault.
 * This typically occurs during decryption when the subject's key has been deleted
 * or never existed.
 */
public class SubjectKeyNotFoundException extends VaultCryptoException {

    private final String subjectId;

    /**
     * Constructs a new SubjectKeyNotFoundException with the specified subject ID and detail message.
     *
     * @param subjectId the subject ID for which the key was not found
     * @param message the detail message
     */
    public SubjectKeyNotFoundException(String subjectId, String message) {
        super(message);
        this.subjectId = subjectId;
    }

    /**
     * Constructs a new SubjectKeyNotFoundException with the specified subject ID, detail message and cause.
     *
     * @param subjectId the subject ID for which the key was not found
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public SubjectKeyNotFoundException(String subjectId, String message, Throwable cause) {
        super(message, cause);
        this.subjectId = subjectId;
    }

    /**
     * Returns the subject ID for which the key was not found.
     *
     * @return the subject ID
     */
    public String getSubjectId() {
        return subjectId;
    }
}
