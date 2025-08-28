package pi2schema.serialization.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForJsonSchema;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaGdprAwareJsonSchemaInterceptorsTest {

    private KafkaGdprAwareProducerInterceptor<String, Map<String, Object>, JsonNode> producerInterceptor;
    private KafkaGdprAwareConsumerInterceptor<String, Map<String, Object>, JsonNode> consumerInterceptor;

    @BeforeAll
    void setupInterceptors() {
        producerInterceptor = new KafkaGdprAwareProducerInterceptor<>();
        producerInterceptor.configure(configsForJsonSchema());

        consumerInterceptor = new KafkaGdprAwareConsumerInterceptor<>();
        consumerInterceptor.configure(configsForJsonSchema());
    }

    @Test
    void shouldEncryptJsonSchemaMessage() {
        // Create a test user object with personal data
        var userData = new HashMap<String, Object>();
        userData.put("userId", UUID.randomUUID().toString());
        userData.put("email", "john.doe@email.com");
        userData.put("name", "John Doe");
        userData.put("favoriteNumber", 42);

        var message = new ProducerRecord<String, Map<String, Object>>("topic", userData);

        // Serialization with personal data to be encrypted
        var encryptedMessage = producerInterceptor.onSend(message);

        assertThat(encryptedMessage.value())
            .satisfies(manipulatedPayload -> {
                // In JSON Schema, encrypted personal data fields are replaced with JSON strings
                // containing the encrypted data structure
                assertThat(manipulatedPayload.get("email")).isInstanceOf(String.class);
                assertThat(manipulatedPayload.get("name")).isInstanceOf(String.class);

                // The encrypted fields should contain JSON structure (starts with '{')
                assertThat((String) manipulatedPayload.get("email")).startsWith("{");
                assertThat((String) manipulatedPayload.get("name")).startsWith("{");

                // Non-personal data should remain unchanged
                assertThat(manipulatedPayload).containsEntry("favoriteNumber", 42);
                assertThat(manipulatedPayload).containsKey("userId");
            });

        // Create consumer records for decryption test
        ConsumerRecords<String, Map<String, Object>> messages = new ConsumerRecords<>(
            Map.of(
                new TopicPartition("topic", 0),
                List.of(new ConsumerRecord<>("topic", 0, 1, encryptedMessage.key(), encryptedMessage.value()))
            )
        );

        // Decrypt the messages
        Iterable<ConsumerRecord<String, Map<String, Object>>> decryptedMessages = consumerInterceptor
            .onConsume(messages)
            .records("topic");

        assertThat(decryptedMessages).hasSize(1);
        assertThat(decryptedMessages)
            .first()
            .satisfies(record -> {
                Map<String, Object> decryptedValue = record.value();
                // Verify that personal data has been decrypted and restored
                assertThat(decryptedValue).containsEntry("email", "john.doe@email.com");
                assertThat(decryptedValue).containsEntry("name", "John Doe");
                assertThat(decryptedValue).containsEntry("favoriteNumber", 42);
                assertThat(decryptedValue).containsKey("userId");
            });
    }

    @Test
    void shouldHandleMessageWithoutPersonalData() {
        // Create a test object without any personal data
        var nonPersonalData = new HashMap<String, Object>();
        nonPersonalData.put("userId", UUID.randomUUID().toString());
        nonPersonalData.put("favoriteNumber", 123);

        var message = new ProducerRecord<String, Map<String, Object>>("topic", nonPersonalData);

        // Process message through interceptor
        var processedMessage = producerInterceptor.onSend(message);

        // Verify that no encryption occurred since there's no personal data
        assertThat(processedMessage.value())
            .satisfies(payload -> {
                assertThat(payload).containsEntry("favoriteNumber", 123);
                assertThat(payload).containsKey("userId");
                // Values should remain as their original types (no JSON strings)
                assertThat(payload.get("favoriteNumber")).isInstanceOf(Integer.class);
            });
    }
}
