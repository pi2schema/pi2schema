package com.github.gustavomonarin.kafkagdpr.core.personaldata;

import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;

public class UnsupportedEncryptedFieldFormatException extends RuntimeException {

    private static final String TARGET_ENCRYPTION_FIELD_TYPE = EncryptedPersonalDataOuterClass.EncryptedPersonalData.class.getName();

    private final String encryptionTargetField;
    private final Class<?> instanceClass;

    public UnsupportedEncryptedFieldFormatException(String encryptionTargetField, Class<?> instanceClass) {
        this.encryptionTargetField = encryptionTargetField;
        this.instanceClass = instanceClass;
    }

    @Override
    public String getMessage() {
        return String.format(
                "The field %s was supposed to be encrypted containing the wrapper class %s, however the class %s was found",
                encryptionTargetField,
                TARGET_ENCRYPTION_FIELD_TYPE,
                instanceClass
        );
    }
}
