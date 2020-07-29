package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;

import java.util.List;

public class PersonalMetadata {

    private final List<OneOfPersonalDataFieldDefinition> encryptableFields;

    public PersonalMetadata(List<OneOfPersonalDataFieldDefinition> encryptableFields) {
        this.encryptableFields = encryptableFields;
    }

    public List<OneOfPersonalDataFieldDefinition> getEncryptableFields() {
        return encryptableFields;
    }

    public boolean containsEncryptableFields() {
        return !encryptableFields.isEmpty();
    }
}
