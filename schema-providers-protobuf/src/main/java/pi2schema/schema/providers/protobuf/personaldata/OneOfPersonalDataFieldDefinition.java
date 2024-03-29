package pi2schema.schema.providers.protobuf.personaldata;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pi2schema.EncryptedPersonalDataV1.EncryptedPersonalData;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.*;
import pi2schema.schema.providers.protobuf.subject.ProtobufSubjectIdentifierFieldDefinition;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.crypto.spec.IvParameterSpec;

public class OneOfPersonalDataFieldDefinition implements PersonalDataFieldDefinition<Message.Builder> {

    private static final Logger log = LoggerFactory.getLogger(OneOfPersonalDataFieldDefinition.class);

    private static final Predicate<FieldDescriptor> isEncryptedFieldType = f ->
        EncryptedPersonalData.getDescriptor().getFullName().equals(f.getMessageType().getFullName());

    private final OneofDescriptor containerOneOfDescriptor;
    private final ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition;
    private final FieldDescriptor targetFieldForEncryption;

    public OneOfPersonalDataFieldDefinition(
        OneofDescriptor descriptor,
        ProtobufSubjectIdentifierFieldDefinition subjectIdentifierFieldDefinition
    ) {
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
    public ByteBuffer valueFrom(Message.Builder actualInstance) {
        var unencryptedField = actualInstance.getOneofFieldDescriptor(containerOneOfDescriptor);
        var value = actualInstance.getField(unencryptedField);

        if (value instanceof Message) {
            return ((Message) value).toByteString().asReadOnlyByteBuffer();
        }

        throw new UnsupportedPersonalDataFieldFormatException(unencryptedField.getFullName());
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, Message.Builder encryptingInstance) {
        if (!encryptingInstance.hasOneof(containerOneOfDescriptor)) {
            log.info(
                "The oneOf personal data container {} has no data set. Optional field?",
                containerOneOfDescriptor.getFullName()
            );
            return CompletableFuture.allOf();
        }

        int sourceFieldNumber = encryptingInstance.getOneofFieldDescriptor(containerOneOfDescriptor).getNumber();

        var subjectId = subjectIdentifierFieldDefinition.subjectFrom(encryptingInstance);

        return encryptor
            .encrypt(subjectId, valueFrom(encryptingInstance))
            .thenAccept(encrypted -> {
                encryptingInstance.clearOneof(containerOneOfDescriptor);
                encryptingInstance.setField(
                    targetFieldForEncryption,
                    EncryptedPersonalData
                        .newBuilder()
                        .setSubjectId(subjectId)
                        .setData(ByteString.copyFrom(encrypted.data())) //TODO input/output stream
                        .setPersonalDataFieldNumber(sourceFieldNumber)
                        .setUsedTransformation(encrypted.usedTransformation())
                        .setInitializationVector(ByteString.copyFrom(encrypted.initializationVector().getIV()))
                        .build()
                );
            });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, Message.Builder decryptingInstance) {
        var encryptedValue = decryptingInstance.getField(targetFieldForEncryption);
        if (!(encryptedValue instanceof EncryptedPersonalData)) {
            throw new UnsupportedEncryptedFieldFormatException(
                EncryptedPersonalData.class.getName(),
                targetFieldForEncryption.getFullName(),
                encryptedValue.getClass()
            );
        }
        var encryptedPersonalData = (EncryptedPersonalData) encryptedValue;

        var encryptedData = new EncryptedData(
            encryptedPersonalData.getData().asReadOnlyByteBuffer(),
            encryptedPersonalData.getUsedTransformation(),
            new IvParameterSpec(encryptedPersonalData.getInitializationVector().toByteArray())
        );

        return decryptor
            .decrypt(encryptedPersonalData.getSubjectId(), encryptedData)
            .thenAccept(decrypted -> {
                decryptingInstance.clearOneof(containerOneOfDescriptor);
                var personalDataUnencryptedField = containerOneOfDescriptor
                    .getContainingType()
                    .findFieldByNumber(encryptedPersonalData.getPersonalDataFieldNumber());
                try {
                    decryptingInstance
                        .getFieldBuilder(personalDataUnencryptedField)
                        .mergeFrom(ByteString.copyFrom(decrypted));
                } catch (InvalidProtocolBufferException e) {
                    throw new InvalidEncryptedMessageException(e);
                }
            });
    }

    static boolean hasPersonalData(OneofDescriptor descriptor) {
        return descriptor.getFields().stream().anyMatch(isEncryptedFieldType);
    }

    private FieldDescriptor determineEncryptionField() {
        var encryptionFields = containerOneOfDescriptor
            .getFields()
            .stream()
            .filter(isEncryptedFieldType)
            .collect(Collectors.toList());

        if (encryptionFields.isEmpty()) {
            throw new EncryptionTargetFieldNotFoundException(
                EncryptedPersonalData.getDescriptor().getFullName(),
                containerOneOfDescriptor.getFullName()
            );
        }

        if (encryptionFields.size() > 1) {
            throw new TooManyEncryptionTargetFieldsException(
                EncryptedPersonalData.getDescriptor().getFullName(),
                containerOneOfDescriptor.getFullName(),
                encryptionFields.size()
            );
        }

        return encryptionFields.get(0);
    }
}
