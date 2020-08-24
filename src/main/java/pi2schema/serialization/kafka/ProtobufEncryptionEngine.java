package pi2schema.serialization.kafka;

import pi2schema.crypto.Encryptor;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadata;
import pi2schema.schema.providers.protobuf.personaldata.PersonalMetadataProvider;
import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

public class ProtobufEncryptionEngine<T extends Message> {

    private final PersonalMetadataProvider personalMetadataProvider = new PersonalMetadataProvider();
    private final Encryptor encryptor;

    public ProtobufEncryptionEngine(Encryptor encryptor) {
        this.encryptor = encryptor;
    }

    public T encrypt(@NotNull T data) {
        PersonalMetadata metadata = personalMetadataProvider.forDescriptor(data.getDescriptorForType());

        Message.Builder encryptingBuilder = data.toBuilder();

        metadata.encryptPersonalData(encryptor, encryptingBuilder);

        //TODO check how schema registry solves this cast
        return (T) encryptingBuilder.build();
    }
}
