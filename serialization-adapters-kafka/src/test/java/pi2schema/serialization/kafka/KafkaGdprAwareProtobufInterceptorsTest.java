package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForPotobuffer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaGdprAwareProtobufInterceptorsTest {

    private KafkaGdprAwareProducerInterceptor<String, FarmerRegisteredEvent> producerInterceptor;
    private KafkaGdprAwareConsumerInterceptor<String, FarmerRegisteredEvent> consumerInterceptor;

    @BeforeAll
    void getStringFarmerRegisteredEventKafkaGdprAwareProducerInterceptor() {
        producerInterceptor = new KafkaGdprAwareProducerInterceptor<>();
        producerInterceptor.configure(configsForPotobuffer());

        consumerInterceptor = new KafkaGdprAwareConsumerInterceptor<>();
        consumerInterceptor.configure(configsForPotobuffer());
    }

    @Test
    void shouldEncryptProtobufMessage() {
        var message = new ProducerRecord<String, FarmerRegisteredEvent>(
            "topic",
            FarmerRegisteredEventFixture.johnDoe().build()
        );

        // serialization with personal data to be encrypted
        var encryptedMessage = producerInterceptor.onSend(message);

        assertThat(encryptedMessage.value())
            .satisfies(manipulatedPayload -> {
                assertThat(manipulatedPayload.hasEncryptedPersonalData()).isTrue();
                assertThat(manipulatedPayload.hasContactInfo()).isFalse();
            });

        ConsumerRecords<String, FarmerRegisteredEvent> messages = new ConsumerRecords<>(
            Map.of(
                new TopicPartition("topic", 0),
                List.of(new ConsumerRecord<>("topic", 0, 1, encryptedMessage.key(), encryptedMessage.value()))
            )
        );

        Iterable<ConsumerRecord<String, FarmerRegisteredEvent>> decryptedMessages = consumerInterceptor
            .onConsume(messages)
            .records("topic");
        assertThat(decryptedMessages).hasSize(1);
        assertThat(decryptedMessages)
            .first()
            .satisfies(r -> {
                assertThat(r.value().hasContactInfo()).isTrue();
                assertThat(r.value().getContactInfo().getName()).isEqualTo("John Doe");
                assertThat(r.value().getContactInfo().getEmail()).isEqualTo("john.doe@acme.com");
            });
    }
}
