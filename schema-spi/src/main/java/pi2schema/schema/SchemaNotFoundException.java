package pi2schema.schema;

/**
 * Exception thrown when a schema cannot be found for a given business object.
 */
public class SchemaNotFoundException extends RuntimeException {

    /**
     * Constructs a new SchemaNotFoundException with the specified detail message.
     *
     * @param message the detail message
     */
    public SchemaNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new SchemaNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public SchemaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
