package pi2schema.schema.personaldata;

public class EncryptionTargetFieldNotFoundException extends RuntimeException {

    private final String targetEncryptionFieldType;
    private final String containerFieldName;

    public EncryptionTargetFieldNotFoundException(String targetEncryptionFieldType, String containerFieldName) {
        this.targetEncryptionFieldType = targetEncryptionFieldType;
        this.containerFieldName = containerFieldName;
    }

    @Override
    public String getMessage() {
        return String.format("The personal data container %s does not encapsulate a %s while exact one is required",
                containerFieldName,
                targetEncryptionFieldType
        );
    }
}
