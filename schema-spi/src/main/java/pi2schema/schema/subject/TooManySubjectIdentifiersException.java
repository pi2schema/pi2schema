package pi2schema.schema.subject;

public class TooManySubjectIdentifiersException extends RuntimeException {

    private final String strategyName;
    private final String fieldName;
    private final int subjectIdentifiersFound;

    public TooManySubjectIdentifiersException(Class<?> strategy, String fieldName, int subjectIdentifiersFound) {
        this.strategyName = strategy.getSimpleName();
        this.fieldName = fieldName;
        this.subjectIdentifiersFound = subjectIdentifiersFound;
    }

    @Override
    public String getMessage() {
        return String.format(
            "The strategy %s has found %d possible identifiers for the field %s while exact one is required",
            strategyName,
            subjectIdentifiersFound,
            fieldName
        );
    }
}
