package pi2schema.schema.personaldata;

public class TooManyEncryptionTargetFieldsException extends RuntimeException {

    private final String targetEncryptionFieldType;
    private final String containerFieldName;
    private final int targetEncryptionFields;

    public TooManyEncryptionTargetFieldsException(String targetEncryptionFieldType, String containerFieldName, int targetEncryptionFields) {
        this.targetEncryptionFieldType = targetEncryptionFieldType;
        this.containerFieldName = containerFieldName;
        this.targetEncryptionFields = targetEncryptionFields;
    }

    @Override
    public String getMessage() {
        return String.format("The personal data container %s has %d fields of type %s while exact one is required",
                containerFieldName,
                targetEncryptionFields,
                targetEncryptionFieldType
        );
    }
}
