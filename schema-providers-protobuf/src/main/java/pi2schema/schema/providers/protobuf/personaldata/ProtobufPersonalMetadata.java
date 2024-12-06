package pi2schema.schema.providers.protobuf.personaldata;

import com.google.protobuf.Message;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;
import pi2schema.schema.personaldata.PersonalMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ProtobufPersonalMetadata<T extends Message> implements PersonalMetadata<T> {

    private final List<OneOfPersonalDataFieldDefinition> personalDataFields;

    public ProtobufPersonalMetadata(List<OneOfPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
    }

    @Override
    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    @Override
    public T swapToEncrypted(Encryptor encryptor, T decryptedInstance) {
        var encryptingBuilder = decryptedInstance.toBuilder();
        var encrypted = personalDataFields.parallelStream().map(field -> field.swapToEncrypted(encryptor, encryptingBuilder));

        return (T) CompletableFuture
                .allOf(encrypted.toArray(CompletableFuture[]::new))
                .thenApply(__ -> encryptingBuilder.build())
                .join();
    }

    @Override
    public T swapToDecrypted(Decryptor decryptor, T encryptedInstance) {
        var decryptingBuilder = encryptedInstance.toBuilder();
        var decrypted = personalDataFields.parallelStream().map(field -> field.swapToDecrypted(decryptor, decryptingBuilder));

        return (T) CompletableFuture
                .allOf(decrypted.toArray(CompletableFuture[]::new))
                .thenApply(__ -> decryptingBuilder.build())
                .join();
    }
}
