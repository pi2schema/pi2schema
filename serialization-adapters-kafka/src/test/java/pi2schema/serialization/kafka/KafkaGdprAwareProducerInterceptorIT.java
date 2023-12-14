package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.acme.UserValid;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pi2schema.EncryptedPersonalData;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForAvro;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForPotobuf;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaGdprAwareProducerInterceptorIT {

    private KafkaGdprAwareProducerInterceptor<String, FarmerRegisteredEvent> protobufInterceptor;
    private KafkaGdprAwareProducerInterceptor<String, UserValid> avroInterceptor;

    @BeforeAll
    void getStringFarmerRegisteredEventKafkaGdprAwareProducerInterceptor() {
        protobufInterceptor = new KafkaGdprAwareProducerInterceptor<>();
        protobufInterceptor.configure(configsForPotobuf());

        avroInterceptor = new KafkaGdprAwareProducerInterceptor<>();
        avroInterceptor.configure(configsForAvro());
    }

    @Test
    void shouldEncryptProtobufMessage() {
        var message = new ProducerRecord<String, FarmerRegisteredEvent>("topic",
                FarmerRegisteredEventFixture.johnDoe().build());

        // serialization with personal data to be encrypted
        var transformedMessage = protobufInterceptor.onSend(message);

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
        var transformedMessage = avroInterceptor.onSend(message);

        assertThat(transformedMessage.value().getEmail()).isInstanceOf(EncryptedPersonalData.class);
        //TODO full assertion and test encryption
    }

}