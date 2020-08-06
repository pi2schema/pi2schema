package com.github.gustavomonarin.kafkagdpr.core.personaldata;

import com.github.gustavomonarin.gdpr.EncryptedPersonalDataV1.EncryptedPersonalData;

public class EncryptionTargetFieldNotFoundException extends RuntimeException {

    private static final String TARGET_ENCRYPTION_FIELD_TYPE = EncryptedPersonalData.getDescriptor().getFullName();

    private final String containerFieldName;

    public EncryptionTargetFieldNotFoundException(String containerFieldName) {
        this.containerFieldName = containerFieldName;
    }

    @Override
    public String getMessage() {
        return String.format("The personal data container %s does not encapsulate a %s while exact one is required",
                containerFieldName,
                TARGET_ENCRYPTION_FIELD_TYPE
        );
    }
}
