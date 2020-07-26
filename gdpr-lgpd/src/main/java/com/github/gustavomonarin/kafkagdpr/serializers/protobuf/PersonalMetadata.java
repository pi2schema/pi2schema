package com.github.gustavomonarin.kafkagdpr.serializers.protobuf;

import java.util.List;

public class PersonalMetadata {

    private final List<OneOfEncryptableField> encryptableFields;

    public PersonalMetadata(List<OneOfEncryptableField> encryptableFields) {
        this.encryptableFields = encryptableFields;
    }

    public List<OneOfEncryptableField> getEncryptableFields() {
        return encryptableFields;
    }

    public boolean containsEncryptableFields() {
        return !encryptableFields.isEmpty();
    }
}
