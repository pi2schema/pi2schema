package pi2schema.schema.personaldata;

public class UnsupportedEncryptedFieldFormatException extends RuntimeException {

    private final String expectedEncryptionFieldType;
    private final String encryptionTargetField;
    private final String instanceClassName;

    public UnsupportedEncryptedFieldFormatException(
        String expectedEncryptionFieldType,
        String encryptionTargetField,
        Class<?> instanceClass
    ) {
        this.expectedEncryptionFieldType = expectedEncryptionFieldType;
        this.encryptionTargetField = encryptionTargetField;
        this.instanceClassName = instanceClass.getName();
    }

    @Override
    public String getMessage() {
        return String.format(
            "The field %s was supposed to be encrypted containing the wrapper class %s, however the class %s was found",
            encryptionTargetField,
            expectedEncryptionFieldType,
            instanceClassName
        );
    }
}
