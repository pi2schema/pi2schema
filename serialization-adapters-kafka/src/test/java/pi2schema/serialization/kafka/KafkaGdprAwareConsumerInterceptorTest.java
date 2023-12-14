package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import com.acme.UserValid;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pi2schema.EncryptedPersonalData;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForAvro;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForPotobuf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaGdprAwareConsumerInterceptorTest {


    private KafkaGdprAwareConsumerInterceptor<String, FarmerRegisteredEvent> protobufInterceptor;
    private KafkaGdprAwareConsumerInterceptor<String, UserValid> avroInterceptor;

    @BeforeAll
    void beforeAll() {
        protobufInterceptor = new KafkaGdprAwareConsumerInterceptor<>();
        protobufInterceptor.configure(configsForPotobuf());

        avroInterceptor = new KafkaGdprAwareConsumerInterceptor<>();
        avroInterceptor.configure(configsForAvro());
    }


    @Test
    void shouldEncryptProtobufMessage() {
        ConsumerRecords<String, FarmerRegisteredEvent> messages = new ConsumerRecords<>(
                Map.of(new TopicPartition("topic", 0),
                        List.of(new ConsumerRecord<>("topic", 0, 0, "key", FarmerRegisteredEventFixture.johnDoe().build()))));

        var transformedMessage = protobufInterceptor.onConsume(messages);

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

        var transformedMessage = deserializer.onSend(message);

        assertThat(transformedMessage.value().getEmail()).isInstanceOf(EncryptedPersonalData.class);
        //TODO full assertion and test encryption
    }

}