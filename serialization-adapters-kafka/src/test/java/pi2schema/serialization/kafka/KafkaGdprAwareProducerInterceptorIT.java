package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.acme.UserValid;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import pi2schema.EncryptedPersonalData;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

class KafkaGdprAwareProducerInterceptorIT {


    private final Map<String, String> configs = Map.of(PERSONAL_METADATA_PROVIDER_CONFIG, "pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider",
            MATERIALS_PROVIDER_CONFIG, "pi2schema.serialization.kafka.materials.InMemoryMaterialsProvider");

    @Test
    void shouldEncryptProtobufMessage() {
        var message = new ProducerRecord<String, FarmerRegisteredEvent>("topic",
                FarmerRegisteredEventFixture.johnDoe().build());

        // serialization with personal data to be encrypted
        var serializer = new KafkaGdprAwareProducerInterceptor<String, FarmerRegisteredEvent>();
        serializer.configure(configs);
        var transformedMessage = serializer.onSend(message);

        assertThat(transformedMessage.value())
                .satisfies(manipulatedPayload ->
                        assertThat(manipulatedPayload.hasEncryptedPersonalData()).isTrue());
        //TODO full assertion and test encryption
    }

    @Test
    void shouldEncryptAvroMessage() {

        var validUser = UserValid.newBuilder().setUuid(UUID.randomUUID().toString()).setEmail("john.doe@email.com").setFavoriteNumber(5).build();
        var message = new ProducerRecord<String, UserValid>("topic", validUser);

        // serialization with personal data to be encrypted
        var serializer = new KafkaGdprAwareProducerInterceptor<String, UserValid>();
        serializer.configure(configs);
        var transformedMessage = serializer.onSend(message);

        assertThat(transformedMessage.value().getEmail()).isInstanceOf(EncryptedPersonalData.class);;
    }
}