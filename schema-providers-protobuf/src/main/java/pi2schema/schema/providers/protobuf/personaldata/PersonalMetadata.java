package pi2schema.schema.providers.protobuf.personaldata;

import com.google.protobuf.Message;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class PersonalMetadata {

    private final List<OneOfPersonalDataFieldDefinition> personalDataFields;

    public PersonalMetadata(List<OneOfPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
    }

    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    public Stream<CompletableFuture<Void>> encryptPersonalData(
            Encryptor encryptor, Message.Builder encryptingBuilder) {
        return personalDataFields
                .parallelStream()
                .map(field -> field.swapToEncrypted(encryptor, encryptingBuilder));
    }

    public Stream<CompletableFuture<Void>> decryptPersonalData(
            Decryptor decryptor, Message.Builder decryptingBuilder) {
        return personalDataFields
                .parallelStream()
                .map(field -> field.swapToDecrypted(decryptor, decryptingBuilder));
    }
}
