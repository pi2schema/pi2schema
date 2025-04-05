package pi2schema.schema.providers.avro.personaldata;

import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AvroPersonalMetadata<T extends SpecificRecordBase> implements PersonalMetadata<T> {

    private final List<AvroUnionPersonalDataFieldDefinition> personalDataFields;
    private final DeepCopier copier;

    public AvroPersonalMetadata(List<AvroUnionPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
        this.copier = new DeepCopier();
    }

    @Override
    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    @Override
    public T swapToEncrypted(Encryptor encryptor, T decryptedInstance) {
        var encryptingInstance = copier.copy(decryptedInstance);
        var futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToEncrypted(encryptor, encryptingInstance))
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();

        return encryptingInstance;
    }

    @Override
    public T swapToDecrypted(Decryptor decryptor, T decryptedInstance) {
        var decryptingInstance = copier.copy(decryptedInstance);
        var futures = personalDataFields
            .parallelStream()
            .map(field -> field.swapToDecrypted(decryptor, decryptingInstance))
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();

        return decryptingInstance;
    }
}
