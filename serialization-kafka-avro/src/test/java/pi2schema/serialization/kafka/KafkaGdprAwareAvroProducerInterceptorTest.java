package pi2schema.serialization.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import pi2schema.crypto.EncryptedData;
import pi2schema.crypto.Encryptor;

import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class KafkaGdprAwareAvroProducerInterceptorTest {

    private final KafkaGdprAwareAvroProducerInterceptor<String, SpecificRecordBase> interceptor;


    private final ByteBuffer encrypted = ByteBuffer.wrap("mockEncryption".getBytes()).asReadOnlyBuffer();
    private final Encryptor encryptorMock = (subjectId, data) ->
            CompletableFuture.completedFuture(
                    new EncryptedData(encrypted, "AES/CBC/PKCS5Padding", new IvParameterSpec(new byte[0]))
            );

    KafkaGdprAwareAvroProducerInterceptorTest() {
        this.interceptor = new KafkaGdprAwareAvroProducerInterceptor<>(
                new AvroEncryptionEngine<>(encryptorMock), (key) -> null);
    }

    @Test
    public void shouldSupportNullRecordReturningNullData() {
        var tombstone = new ProducerRecord<String, SpecificRecordBase>("topic", null);

        this.interceptor.onSend(tombstone);
    }

    @Test
    public void shouldEncryptPersonalData() {

    }
}
