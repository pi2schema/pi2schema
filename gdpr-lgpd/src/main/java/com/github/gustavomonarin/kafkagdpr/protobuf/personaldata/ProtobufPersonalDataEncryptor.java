package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;

import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass;
import com.github.gustavomonarin.gdpr.EncryptedPersonalDataOuterClass.EncryptedPersonalData;
import com.github.gustavomonarin.kafkagdpr.core.kms.Encryptor;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.PersonalDataEncryptor;
import com.google.protobuf.ByteString;

public class ProtobufPersonalDataEncryptor implements PersonalDataEncryptor {

    private final Encryptor encryptor;

    public ProtobufPersonalDataEncryptor(Encryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public EncryptedPersonalData encrypt(String subject, byte[] personalData) {
        byte[] encryptedPersonalData = encryptor.encrypt(subject, personalData);

        return EncryptedPersonalDataOuterClass.EncryptedPersonalData.newBuilder()
                .setSubjectId(subject)
                .setData(ByteString.copyFrom(encryptedPersonalData)) //TODO input/output stream
                .build();
    }
}
