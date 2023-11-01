package pi2schema.schema.providers.avro.personaldata;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.avro.specific.SpecificRecordBuilderBase;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;

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
        return null;
    }

    @Override
    public ByteBuffer valueFrom(SpecificRecordBase instance) {
        return null;
    }
}
