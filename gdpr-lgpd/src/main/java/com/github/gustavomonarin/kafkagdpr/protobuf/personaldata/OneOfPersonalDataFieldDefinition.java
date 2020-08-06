package com.github.gustavomonarin.kafkagdpr.protobuf.personaldata;


import com.github.gustavomonarin.gdpr.EncryptedPersonalDataV1.EncryptedPersonalData;
import com.github.gustavomonarin.kafkagdpr.core.encryption.Decryptor;
import com.github.gustavomonarin.kafkagdpr.core.encryption.EncryptedData;
import com.github.gustavomonarin.kafkagdpr.core.encryption.Encryptor;
import com.github.gustavomonarin.kafkagdpr.core.personaldata.*;
import com.github.gustavomonarin.kafkagdpr.protobuf.subject.ProtobufSubjectIdentifierFieldDefinition;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.IvParameterSpec;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OneOfPersonalDataFieldDefinition
        implements PersonalDataFieldDefinition<Message.Builder> {

    private static final Logger log = LoggerFactory.getLogger(OneOfPersonalDataFieldDefinition.class);

    private static final Predicate<FieldDescriptor> isEncryptedFieldType = (f) ->
            EncryptedPersonalData.getDescriptor().getFullName().equals(f.getMessageType().getFullName());

    private final OneofDescriptor containerOneOfDescriptor;
    private final ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition;
    private final FieldDescriptor targetFieldForEncryption;

    public OneOfPersonalDataFieldDefinition(@NotNull OneofDescriptor descriptor,
                                            @NotNull ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition) {
        this.containerOneOfDescriptor = descriptor;
        this.subjectIdentifierFieldDefinition = subjectIdentifierFieldDefinition;
        this.targetFieldForEncryption = determineEncryptionField();
    }

    FieldDescriptor encryptionTargetField() {
        return targetFieldForEncryption;
    }

    FieldDescriptor personalDataTargetField(int fieldNumber) {
        return containerOneOfDescriptor.getContainingType().findFieldByNumber(fieldNumber);
    }

    @Override
    public byte[] valueFrom(Message.Builder actualInstance) {
        Descriptors.FieldDescriptor unencryptedField = actualInstance.getOneofFieldDescriptor(containerOneOfDescriptor);

        Object value = actualInstance.getField(unencryptedField);

        if (value instanceof Message) {
            return ((Message) value).toByteArray();
        }

        throw new UnsupportedPersonalDataFieldFormatException(unencryptedField.getFullName());
    }

    @Override
    public void swapToEncrypted(Encryptor encryptor, Message.Builder encryptingInstance) {

        if (!encryptingInstance.hasOneof(containerOneOfDescriptor)) {
            log.info("The oneOf personal data container {} has no data set. Optional field?",
                    containerOneOfDescriptor.getFullName());
            return;
        }

        int sourceFieldNumber = encryptingInstance.getOneofFieldDescriptor(containerOneOfDescriptor).getNumber();

        String subjectId = subjectIdentifierFieldDefinition.subjectFrom(encryptingInstance);
        EncryptedData encryptedData = encryptor.encrypt(subjectId, valueFrom(encryptingInstance));

        encryptingInstance.clearOneof(containerOneOfDescriptor);
        encryptingInstance.setField(targetFieldForEncryption, EncryptedPersonalData.newBuilder()
                .setSubjectId(subjectId)
                .setData(ByteString.copyFrom(encryptedData.data())) //TODO input/output stream
                .setPersonalDataFieldNumber(sourceFieldNumber)
                .setUsedTransformation(encryptedData.usedTransformation())
                .setInitializationVector(ByteString.copyFrom(encryptedData.initializationVector().getIV()))
                .build());
    }

    public void swapToDecrypted(Decryptor decryptor, Message.Builder decryptingInstance) {

        Object encryptedValue = decryptingInstance.getField(targetFieldForEncryption);
        if (!(encryptedValue instanceof EncryptedPersonalData)) {
            throw new UnsupportedEncryptedFieldFormatException(
                    targetFieldForEncryption.getFullName(),
                    encryptedValue.getClass());
        }
        EncryptedPersonalData encryptedPersonalData = (EncryptedPersonalData) encryptedValue;

        byte[] decrypted = decryptor.decrypt(encryptedPersonalData.getSubjectId(),
                new EncryptedData(encryptedPersonalData.getData().toByteArray(),
                        encryptedPersonalData.getUsedTransformation(),
                        new IvParameterSpec(encryptedPersonalData.getInitializationVector().toByteArray())));

        decryptingInstance.clearOneof(containerOneOfDescriptor);

        FieldDescriptor personalDataUnencryptedField = containerOneOfDescriptor.getContainingType().findFieldByNumber(
                encryptedPersonalData.getPersonalDataFieldNumber());

        try {
            decryptingInstance.getFieldBuilder(personalDataUnencryptedField)
                    .mergeFrom(decrypted);
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidEncryptedMessageException(e);
        }
    }

    static boolean hasPersonalData(@NotNull OneofDescriptor descriptor) {
        return descriptor.getFields()
                .stream()
                .anyMatch(isEncryptedFieldType);
    }

    private FieldDescriptor determineEncryptionField() {
        List<FieldDescriptor> encryptionFields = containerOneOfDescriptor.getFields()
                .stream()
                .filter(isEncryptedFieldType)
                .collect(Collectors.toList());

        if (encryptionFields.isEmpty()) {
            throw new EncryptionTargetFieldNotFoundException(containerOneOfDescriptor.getFullName());
        }

        if (encryptionFields.size() > 1) {
            throw new TooManyEncryptionTargetFieldsException(containerOneOfDescriptor.getFullName(), encryptionFields.size());
        }

        return encryptionFields.get(0);
    }

}
