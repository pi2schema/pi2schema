package pi2schema.schema.personaldata;

import static pi2schema.EncryptedPersonalDataV1.EncryptedPersonalData;

public class TooManyEncryptionTargetFieldsException extends RuntimeException {

    private static final String TARGET_ENCRYPTION_FIELD_TYPE = EncryptedPersonalData.getDescriptor().getFullName();

    private final String containerFieldName;
    private final int targetEncryptionFields;

    public TooManyEncryptionTargetFieldsException(String containerFieldName, int targetEncryptionFields) {
        this.containerFieldName = containerFieldName;
        this.targetEncryptionFields = targetEncryptionFields;
    }

    @Override
    public String getMessage() {
        return String.format("The personal data container %s has %d fields of type %s while exact one is required",
                containerFieldName,
                targetEncryptionFields,
                TARGET_ENCRYPTION_FIELD_TYPE
        );
    }
}
