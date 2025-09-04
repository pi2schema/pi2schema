package pi2schema.serialization.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.KafkaTestConfigs.configsForJsonSchema;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaGdprAwareJsonSchemaInterceptorsTest {

    private KafkaGdprAwareProducerInterceptor<String, TestUserData, JsonNode> producerInterceptor;
    private KafkaGdprAwareConsumerInterceptor<String, TestUserData, JsonNode> consumerInterceptor;

    private final String TOPIC_NAME = "topic";

    @BeforeAll
    void setupInterceptors() throws RestClientException, IOException {
        producerInterceptor = new KafkaGdprAwareProducerInterceptor<>();
        producerInterceptor.configure(configsForJsonSchema());

        consumerInterceptor = new KafkaGdprAwareConsumerInterceptor<>();
        consumerInterceptor.configure(configsForJsonSchema());

        MockSchemaRegistry
            .getClientForScope("test-scope")
            .register(
                TOPIC_NAME + "-value",
                new JsonSchema(
                    """
                    {
                      "$schema": "http://json-schema.org/draft-07/schema#",
                      "type": "object",
                      "properties": {
                        "userId": {
                          "type": "string",
                          "pi2schema-subject-identifier": true
                        },
                        "email": {
                          "type": "string",
                          "pi2schema-personal-data": true
                        },
                        "name": {
                          "type": "string",
                          "pi2schema-personal-data": true
                        },
                        "favoriteNumber": {
                          "type": "integer"
                        }
                      }
                    }
                    """
                ),
                true
            );
    }

    @Test
    void shouldEncryptJsonSchemaMessage() {
        // Create a test user object with personal data
        var userData = new TestUserData(UUID.randomUUID().toString(), "john.doe@email.com", "John Doe", 42);

        var message = new ProducerRecord<String, TestUserData>(TOPIC_NAME, userData);

        // Serialization with personal data to be encrypted
        var encryptedMessage = producerInterceptor.onSend(message);

        assertThat(encryptedMessage.value())
            .satisfies(manipulatedPayload -> {
                // In JSON Schema, encrypted personal data fields are replaced with JSON strings
                // containing the encrypted data structure
                assertThat(manipulatedPayload.email).isInstanceOf(String.class);
                assertThat(manipulatedPayload.name).isInstanceOf(String.class);

                // The encrypted fields should contain JSON structure (starts with '{')
                assertThat(manipulatedPayload.email).startsWith("{");
                assertThat(manipulatedPayload.name).startsWith("{");

                // Non-personal data should remain unchanged
                assertThat(manipulatedPayload.favoriteNumber).isEqualTo(42);
                assertThat(manipulatedPayload.userId).isNotNull();
            });

        // Create consumer records for decryption test
        ConsumerRecords<String, TestUserData> messages = new ConsumerRecords<>(
            Map.of(
                new TopicPartition(TOPIC_NAME, 0),
                List.of(new ConsumerRecord<>(TOPIC_NAME, 0, 1, encryptedMessage.key(), encryptedMessage.value()))
            )
        );

        // Decrypt the messages
        Iterable<ConsumerRecord<String, TestUserData>> decryptedMessages = consumerInterceptor
            .onConsume(messages)
            .records(TOPIC_NAME);

        assertThat(decryptedMessages).hasSize(1);
        assertThat(decryptedMessages)
            .first()
            .satisfies(record -> {
                TestUserData decryptedValue = record.value();
                // Verify that personal data has been decrypted and restored
                assertThat(decryptedValue.email).isEqualTo("john.doe@email.com");
                assertThat(decryptedValue.name).isEqualTo("John Doe");
                assertThat(decryptedValue.favoriteNumber).isEqualTo(42);
                assertThat(decryptedValue.userId).isNotNull();
            });
    }

    @Test
    void shouldHandleMessageWithoutPersonalData() {
        // Create a test object without any personal data
        var nonPersonalData = new TestUserData(
            UUID.randomUUID().toString(),
            null, // no email
            null, // no name
            123
        );

        var message = new ProducerRecord<String, TestUserData>(TOPIC_NAME, nonPersonalData);

        // Process message through interceptor
        var processedMessage = producerInterceptor.onSend(message);

        // Verify that no encryption occurred since there's no personal data
        assertThat(processedMessage.value())
            .satisfies(payload -> {
                assertThat(payload.favoriteNumber).isEqualTo(123);
                assertThat(payload.userId).isNotNull();
                // Values should remain as their original types (no JSON strings)
                assertThat(payload.favoriteNumber).isInstanceOf(Integer.class);
            });
    }

    // Inner test class to represent user data structure
    public static class TestUserData {

        public String userId;
        public String email;
        public String name;
        public Integer favoriteNumber;

        public TestUserData() {}

        public TestUserData(String userId, String email, String name, Integer favoriteNumber) {
            this.userId = userId;
            this.email = email;
            this.name = name;
            this.favoriteNumber = favoriteNumber;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getFavoriteNumber() {
            return favoriteNumber;
        }

        public void setFavoriteNumber(Integer favoriteNumber) {
            this.favoriteNumber = favoriteNumber;
        }
    }
}
