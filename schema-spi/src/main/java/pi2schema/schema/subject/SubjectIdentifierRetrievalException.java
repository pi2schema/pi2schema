package pi2schema.schema.subject;

public class SubjectIdentifierRetrievalException extends RuntimeException {

    private final Object instance;
    private final String identifierFieldPath;

    public SubjectIdentifierRetrievalException(Object instance, String identifierFieldPath, Exception e) {
        super(e);
        this.instance = instance;
        this.identifierFieldPath = identifierFieldPath;
    }

    @Override
    public String getMessage() {
        return String.format(
            "Could not retrieve subject identifier for field %s from the object %s",
            identifierFieldPath,
            instance
        );
    }
}
