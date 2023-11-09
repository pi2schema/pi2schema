package pi2schema.serialization.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.providers.avro.personaldata.AvroPersonalMetadataProvider;

import java.util.concurrent.CompletableFuture;

public class AvroEncryptionEngine<T extends SpecificRecordBase> {

    private final AvroPersonalMetadataProvider personalMetadataProvider = new AvroPersonalMetadataProvider();
    private final Encryptor encryptor;

    public AvroEncryptionEngine(Encryptor encryptor) {
        this.encryptor = encryptor;
    }

    public T encrypt(T data) {
        var metadata = personalMetadataProvider.forDescriptor(data.getSchema());
        var encryptingBuilder = data; //TODO to builder/clone
        var encrypted = metadata.encryptPersonalData(encryptor, encryptingBuilder);

        return (T) CompletableFuture
            .allOf(encrypted.toArray(CompletableFuture[]::new))
            .thenApply(__ -> encryptingBuilder)
            .join();
    }
}
