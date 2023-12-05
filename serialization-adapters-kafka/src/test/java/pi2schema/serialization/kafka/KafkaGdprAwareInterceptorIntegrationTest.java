package pi2schema.serialization.kafka;

import com.acme.FarmerRegisteredEventFixture;
import com.acme.FarmerRegisteredEventOuterClass.FarmerRegisteredEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.MATERIALS_PROVIDER_CONFIG;
import static pi2schema.serialization.kafka.PiiAwareInterceptorConfig.PERSONAL_METADATA_PROVIDER_CONFIG;

public class KafkaGdprAwareInterceptorIntegrationTest {


    private final Map<String, String> configs = Map.of(PERSONAL_METADATA_PROVIDER_CONFIG, "pi2schema.schema.providers.protobuf.personaldata.PersonalMetadataProvider",
            MATERIALS_PROVIDER_CONFIG, "pi2schema.serialization.kafka.materials.InMemoryMaterialsProvider");

    @Test
    void configuredUsingKafkaMaterialsProvider() {
        var message = new ProducerRecord<String, FarmerRegisteredEvent>("topic", FarmerRegisteredEventFixture.johnDoe().build());

        // serialization with personal data to be encrypted
        var serializer = new KafkaGdprAwareProducerInterceptor<String, FarmerRegisteredEvent>();
        serializer.configure(configs);
        var transformedMessage = serializer.onSend(message);

        assertThat(transformedMessage.value())
                .satisfies(manipulatedPayload ->
                {
                    assertThat(manipulatedPayload.hasEncryptedPersonalData()).isTrue();
                });


//        // standard deserialization, should be compatible and provide an encrypted value
//        try (var standardDeserializer = new KafkaProtobufDeserializer<FarmerRegisteredEvent>()) {
//            standardDeserializer.configure(configs, false);
//            var deserializedEncrypted = standardDeserializer.deserialize("", transformedMessage);
//
//            assertThat(deserializedEncrypted).isNotNull();
//            assertThat(deserializedEncrypted.getPersonalDataCase()).isEqualByComparingTo(ENCRYPTEDPERSONALDATA);
//            assertThat(deserializedEncrypted.getContactInfo()).isEqualTo(ContactInfo.getDefaultInstance());
//            assertThat(message).isNotEqualTo(deserializedEncrypted);
//        }
//
//        try (var deserializer = new KafkaGdprAwareProtobufDeserializer<FarmerRegisteredEvent>()) {
//            deserializer.configure(configs, false);
//
//            await()
//                    .atMost(Duration.ofSeconds(600))
//                    .untilAsserted(() -> {
//                        try {
//                            var decrypted = deserializer.deserialize("", transformedMessage);
//                            assertThat(message).isEqualTo(decrypted);
//                        } catch (CompletionException e) {
//                            // failed exception in order to be retried by the awaitability.
//                            if (e.getCause() instanceof MissingCryptoMaterialsException) {
//                                fail(e);
//                            }
//                            throw e;
//                        }
//                    });
//        } finally {
//            //also close the initial serializer
//            serializer.close();
//        }
    }
}
