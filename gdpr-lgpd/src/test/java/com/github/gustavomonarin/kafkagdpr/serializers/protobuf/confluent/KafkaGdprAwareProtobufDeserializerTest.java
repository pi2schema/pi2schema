package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaGdprAwareProtobufDeserializerTest {

    private final String topic = "test";

    @Test
    public void shouldBeCompatibleWithPlainProtobufSerializer() {

        Fruit preferredMelon = FruitFixture.waterMelon().build();

        MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();

        HashMap<String, Object> configuration = new HashMap<>();
        configuration.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        configuration.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");

        KafkaProtobufSerializer<Fruit> serializer = new KafkaProtobufSerializer<>(
                schemaRegistry,
                configuration
        );
        KafkaGdprAwareProtobufDeserializer<Fruit> deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                schemaRegistry,
                configuration,
                Fruit.class
        );

        byte[] serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }

}
