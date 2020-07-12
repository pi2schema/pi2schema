package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.acme.FruitOuterClass.Fruit;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaGdprAwareProtobufSerializerTest {

    private final String topic = "test";

    @Test
    public void shouldBeCompatibleWithPlainProtobufDeserializer() {

        Fruit preferredMelon = Fruit.newBuilder()
                .setName("Watermelon")
                .setSeedless(true)
                .setFamily("Cucurbitaceae")
                .build();

        MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();

        HashMap<String, Object> configuration = new HashMap<>();
        configuration.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        configuration.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");

        KafkaGdprAwareProtobufSerializer<Fruit> serializer = new KafkaGdprAwareProtobufSerializer<>(
                schemaRegistry,
                configuration,
                Fruit.class
        );
        KafkaProtobufDeserializer<Fruit> deserializer = new KafkaProtobufDeserializer<>(
                schemaRegistry,
                configuration,
                Fruit.class
        );

        byte[] serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }


}