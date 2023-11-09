package pi2schema.schema.providers.avro.personaldata;

import org.apache.avro.specific.SpecificRecordBase;
import pi2schema.crypto.Decryptor;
import pi2schema.crypto.Encryptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class AvroPersonalMetadata {

    private final List<AvroUnionPersonalDataFieldDefinition> personalDataFields;

    public AvroPersonalMetadata(List<AvroUnionPersonalDataFieldDefinition> personalDataFields) {
        this.personalDataFields = personalDataFields;
    }

    public boolean requiresEncryption() {
        return !personalDataFields.isEmpty();
    }

    public Stream<CompletableFuture<Void>> encryptPersonalData(
        Encryptor encryptor,
        SpecificRecordBase encryptingBuilder
    ) {
        return personalDataFields.parallelStream().map(field -> field.swapToEncrypted(encryptor, encryptingBuilder));
    }

    public Stream<CompletableFuture<Void>> decryptPersonalData(
        Decryptor decryptor,
        SpecificRecordBase decryptingBuilder
    ) {
        return personalDataFields.parallelStream().map(field -> field.swapToDecrypted(decryptor, decryptingBuilder));
    }
}
