package pi2schema.serialization.kafka;


import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForAvro;

import com.acme.UserValid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pi2schema.EncryptedPersonalData;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaGdprAwareAvroInteceptorsTest {

  private KafkaGdprAwareProducerInterceptor<String, UserValid> producerInterceptor;
  private KafkaGdprAwareConsumerInterceptor<String, UserValid> consumerInterceptor;

  @BeforeAll
  void getStringFarmerRegisteredEventKafkaGdprAwareProducerInterceptor() {
    producerInterceptor = new KafkaGdprAwareProducerInterceptor<>();
    producerInterceptor.configure(configsForAvro());

    consumerInterceptor = new KafkaGdprAwareConsumerInterceptor<>();
    consumerInterceptor.configure(configsForAvro());
  }

  @Test
  void shouldSupportAvro() {

    var validUser = UserValid.newBuilder().setUuid(UUID.randomUUID().toString())
        .setEmail("john.doe@email.com").setFavoriteNumber(5).build();
    var message = new ProducerRecord<String, UserValid>("topic", validUser);

    // serialization with personal data to be encrypted
    var encryptedAvroMessage = producerInterceptor.onSend(message);

    assertThat(encryptedAvroMessage.value().getEmail()).isInstanceOf(EncryptedPersonalData.class);

    var messages = new ConsumerRecords<>(
        Map.of(new TopicPartition("topic", 0), List.of(
            new ConsumerRecord<>("topic", 0, 1, encryptedAvroMessage.key(),
                encryptedAvroMessage.value()))));

    var decryptedMessages = consumerInterceptor.onConsume(
            messages)
        .records("topic");
    assertThat(decryptedMessages).hasSize(1);
    assertThat(decryptedMessages).first()
        .satisfies(r ->
            assertThat(r.value().getEmail()).isEqualTo("john.doe@email.com")
        );
  }
}
