package pi2schema.schema.providers.avro.personaldata;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalDataFieldDefinition;

public class AvroPersonalDataFieldDefinition implements PersonalDataFieldDefinition<Field> {


  private final Field personalField;
  private final Schema parentSchema;

  public AvroPersonalDataFieldDefinition(Field personalField, Schema parentSchema) {
    this.personalField = personalField;
    this.parentSchema = parentSchema;
  }

  @Override
  public ByteBuffer valueFrom(Field instance) {
    return null;
  }

  @Override
  public CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, Field buildingInstance) {
    return null;
  }

  public Field getPersonalField() {
    return personalField;
  }

  public Schema getParentSchema() {
    return parentSchema;
  }
}
