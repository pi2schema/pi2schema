package pi2schema.serialization.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.crypto.Decryptor;
import pi2schema.schema.providers.avro.personaldata.AvroPersonalMetadataProvider;


import java.util.concurrent.CompletableFuture;

public class AvroDecryptionEngine<T extends SpecificRecordBase> {

    private final AvroPersonalMetadataProvider personalMetadataProvider = new AvroPersonalMetadataProvider();
    private final Decryptor decryptor;

    public AvroDecryptionEngine(Decryptor decryptor) {
        this.decryptor = decryptor;
    }

    public T decrypt(T data) {
        var metadata = personalMetadataProvider.forDescriptor(data.getSchema());
        var decryptingBuilder = data; //TODO: find a way toBuilder()
        var decrypted = metadata.decryptPersonalData(decryptor, decryptingBuilder);

        return (T) CompletableFuture
                .allOf(decrypted.toArray(CompletableFuture[]::new))
                .thenApply(__ -> decryptingBuilder)
                .join();
    }
}
