package pi2schema.schema.providers.avro.personaldata;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.EncryptedPersonalData;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;
import pi2schema.schema.personaldata.UnsupportedEncryptedFieldFormatException;
import pi2schema.schema.providers.avro.subject.AvroSiblingSubjectIdentifierFinder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.IvParameterSpec;

public class AvroUnionPersonalDataFieldDefinition implements PersonalDataFieldDefinition<SpecificRecordBase> {

    private final Field personalField;
    private final Schema parentSchema;
    private final AvroSiblingSubjectIdentifierFinder subjectIdentifierFinder;

    public AvroUnionPersonalDataFieldDefinition(Field personalField, Schema parentSchema) {
        this.personalField = personalField;
        this.parentSchema = parentSchema;
        this.subjectIdentifierFinder = new AvroSiblingSubjectIdentifierFinder();
    }

    public Field getPersonalField() {
        return personalField;
    }

    public Schema getParentSchema() {
        return parentSchema;
    }

    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, SpecificRecordBase encryptingInstance) {
        var decryptedValue = (String) encryptingInstance.get(personalField.name());
        var subjectIdentifier = subjectIdentifierFinder.find(this).subjectFrom(encryptingInstance);

        return encryptor
            .encrypt(subjectIdentifier, ByteBuffer.wrap(decryptedValue.getBytes()))
            .thenAccept(encrypted -> {
                encryptingInstance.put(
                    personalField.name(),
                    EncryptedPersonalData
                        .newBuilder()
                        .setSubjectId(subjectIdentifier)
                        .setData(cloneByteBuffer(encrypted.data())) //TODO input/output stream
                        .setPersonalDataFieldNumber("0")
                        .setUsedTransformation("") // Obsolete
                        .setInitializationVector(ByteBuffer.wrap(new byte[0])) // Obsolete
                        .setKmsId("unused-kafkaKms") //TODO
                        .build()
                );
            });
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, SpecificRecordBase decryptingInstance) {
        var encryptedValue = decryptingInstance.get(personalField.name());

        if (!(encryptedValue instanceof EncryptedPersonalData)) {
            throw new UnsupportedEncryptedFieldFormatException(
                EncryptedPersonalData.class.getName(),
                personalField.name(),
                encryptedValue.getClass()
            );
        }
        var encryptedPersonalData = (EncryptedPersonalData) encryptedValue;

        var encryptedData = new EncryptedData(encryptedPersonalData.getData());

        return decryptor
            .decrypt(encryptedData)
            .thenAccept(decryptedData -> decryptingInstance.put(personalField.name(), decodeAvro(decryptedData)));
    }

    //TODO: decode and encode full avro definitions not only strings
    private static String decodeAvro(ByteBuffer decryptedData) {
        return StandardCharsets.UTF_8.decode(decryptedData).toString();
    }

    @Override
    public ByteBuffer valueFrom(SpecificRecordBase instance) {
        return null;
    }

    public static boolean hasPersonalData(Field field) {
        return isUnion(field) && hasType(field, "pi2schema.EncryptedPersonalData") && hasType(field, "string");
    }

    private static boolean hasType(Field field, String expectedType) {
        return field.schema().getTypes().stream().anyMatch(type -> expectedType.equals(type.getFullName()));
    }

    private static boolean isUnion(Field field) {
        return field.schema().isUnion();
    }

    private static ByteBuffer cloneByteBuffer(ByteBuffer byteBuffer) {
        byte[] dataBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(dataBytes);
        return ByteBuffer.wrap(dataBytes);
    }
}
