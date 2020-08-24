package pi2schema.serialization.kafka;

import pi2schema.crypto.Decryptor;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadata;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadataProvider;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

public class ProtobufDecryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();
    private final Decryptor decryptor;

    public ProtobufDecryptionEngine(Decryptor decryptor) {
        this.decryptor = decryptor;
    }

    public T decrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder encryptingBuilder = data.toBuilder();

        metadata.decryptPersonalData(decryptor, encryptingBuilder);

        //TODO check how schema registry solves this cast
        return (T) encryptingBuilder.build();
    }
}
