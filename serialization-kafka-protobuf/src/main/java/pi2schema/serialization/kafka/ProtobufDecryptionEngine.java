package pi2schema.serialization.kafka;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.Decryptor;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadataProvider;

import java.util.concurrent.CompletableFuture;

public class ProtobufDecryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();
    private final Decryptor decryptor;

    public ProtobufDecryptionEngine(Decryptor decryptor) {
        this.decryptor = decryptor;
    }

    public T decrypt(@NotNull T data) {
        var metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());
        var decryptingBuilder = data.toBuilder();
        var decrypted = metadata.decryptPersonalData(decryptor, decryptingBuilder);

        return (T) CompletableFuture
            .allOf(decrypted.toArray(CompletableFuture[]::new))
            .thenApply(__ -> decryptingBuilder.build())
            .join();
    }
}
