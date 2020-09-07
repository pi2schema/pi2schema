package pi2schema.serialization.kafka;

import pi2schema.crypto.Decryptor;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadata;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadataProvider;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ProtobufDecryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();
    private final Decryptor decryptor;

    public ProtobufDecryptionEngine(Decryptor decryptor) {
        this.decryptor = decryptor;
    }

    public T decrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder decryptingBuilder = data.toBuilder();

        Stream<CompletableFuture<Void>> decrypted =
                metadata.decryptPersonalData(decryptor, decryptingBuilder);

        return (T) CompletableFuture
                .allOf(decrypted.toArray(CompletableFuture[]::new))
                .thenApply(__ -> decryptingBuilder.build())
                .join();
    }
}
