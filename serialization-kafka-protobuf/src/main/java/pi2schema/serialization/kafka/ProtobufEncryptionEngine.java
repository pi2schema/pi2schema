package pi2schema.serialization.kafka;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadataProvider;

import java.util.concurrent.CompletableFuture;

public class ProtobufEncryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();
    private final Encryptor encryptor;

    public ProtobufEncryptionEngine(Encryptor encryptor) {
        this.encryptor = encryptor;
    }

    public T encrypt(@NotNull T data) {
        var metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());
        var encryptingBuilder = data.toBuilder();
        var encrypted = metadata.encryptPersonalData(encryptor, encryptingBuilder);

        return (T) CompletableFuture
            .allOf(encrypted.toArray(CompletableFuture[]::new))
            .thenApply(__ -> encryptingBuilder.build())
            .join();
    }
}
