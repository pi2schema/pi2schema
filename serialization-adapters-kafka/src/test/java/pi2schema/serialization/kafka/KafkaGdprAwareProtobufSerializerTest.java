package pi2schema.serialization.kafka;

import org.junit.jupiter.api.Test;
import pi2schema.crypto.Decryptor;

import java.util.concurrent.CompletableFuture;

class KafkaGdprAwareProtobufSerializerTest {

    private final String topic = "test";

    private final Decryptor noOpDecryptor = (subj, encryptedData) ->
        CompletableFuture.completedFuture(encryptedData.data());

    @Test
    void shouldSupportNullRecordReturningNullData() {}

    @Test
    void shouldBeCompatibleWithPlainProtobufDeserializer() {}

    @Test
    void shouldEncryptUnionFieldsContainingEncryptedPersonalData() {}
}
