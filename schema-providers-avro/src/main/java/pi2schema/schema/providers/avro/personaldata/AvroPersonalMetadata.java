package pi2schema.schema.providers.avro.personaldata;

import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AvroPersonalMetadata<T extends SpecificRecordBase> implements PersonalMetadata<T> {

    private final List<AvroUnionPersonalDataFieldDefinition> personalDataFields;

    public AvroPersonalMetadata(List<AvroUnionPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
    }

    @Override
    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    @Override
    public T swapToEncrypted(Encryptor encryptor, T decryptedInstance) {
        var encryptingBuilder = decryptedInstance; //TODO to builder/clone
        var encrypted = personalDataFields
            .parallelStream()
            .map(field -> field.swapToEncrypted(encryptor, decryptedInstance));

        return CompletableFuture
            .allOf(encrypted.toArray(CompletableFuture[]::new))
            .thenApply(__ -> encryptingBuilder)
            .join();
    }

    @Override
    public T swapToDecrypted(Decryptor decryptor, T decryptedInstance) {
        var decryptingBuilder = decryptedInstance; //TODO: find a way toBuilder()
        var decrypted = personalDataFields
            .parallelStream()
            .map(field -> field.swapToDecrypted(decryptor, decryptedInstance));

        return (T) CompletableFuture
            .allOf(decrypted.toArray(CompletableFuture[]::new))
            .thenApply(__ -> decryptingBuilder)
            .join();
    }
}
