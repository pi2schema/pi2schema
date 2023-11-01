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

import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class AvroUnionPersonalDataFieldDefinition implements PersonalDataFieldDefinition<SpecificRecordBase> {

    private final Field personalField;
    private final Schema parentSchema;

    public AvroUnionPersonalDataFieldDefinition(Field personalField, Schema parentSchema) {
        this.personalField = personalField;
        this.parentSchema = parentSchema;
    }

    public Field getPersonalField() {
        return personalField;
    }

    public Schema getParentSchema() {
        return parentSchema;
    }


    @Override
    public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, SpecificRecordBase buildingInstance) {
        return null;
    }

    @Override
    public CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, SpecificRecordBase decryptingInstance) {
        var encryptedValue = decryptingInstance.get(personalField.name());

        if (!(encryptedValue instanceof EncryptedPersonalData)) {
            throw new UnsupportedEncryptedFieldFormatException(
                    EncryptedPersonalData.class.getName(), personalField.name(),
                    encryptedValue.getClass());
        }
        var encryptedPersonalData = (EncryptedPersonalData) encryptedValue;

        var subjectIdentifier = encryptedPersonalData.getSubjectId();

        var encryptedData = new EncryptedData(
                encryptedPersonalData.getData(),
                encryptedPersonalData.getUsedTransformation(),
                new IvParameterSpec(encryptedPersonalData.getInitializationVector().array()));

        return decryptor.decrypt(subjectIdentifier, encryptedData).thenAccept((decryptedData) -> {
            decryptingInstance.put(personalField.name(), new String(decryptedData.array()));
                });

    }

    @Override
    public ByteBuffer valueFrom(SpecificRecordBase instance) {
        return null;
    }

    public static boolean hasPersonalData(Field field) {
        return field.schema().isUnion() &&
                field.schema().getTypes()
                        .stream()
                        .anyMatch(type -> "pi2schema.EncryptedPersonalData".equals(type.getFullName()));
    }
}
