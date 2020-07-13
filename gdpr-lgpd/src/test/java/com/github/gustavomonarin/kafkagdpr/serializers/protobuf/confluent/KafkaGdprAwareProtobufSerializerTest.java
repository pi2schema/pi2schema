package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KafkaGdprAwareProtobufSerializerTest {

    private final String topic = "test";
    private final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    private final Map<String, Object> configs;

    public KafkaGdprAwareProtobufSerializerTest() {
        HashMap<String, Object> initial = new HashMap<>();
        initial.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        initial.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
        this.configs = Collections.unmodifiableMap(initial);
    }

    @Test
    public void shouldSupportNullRecordReturningNullData(){
        KafkaGdprAwareProtobufSerializer<Fruit> deserializer = new KafkaGdprAwareProtobufSerializer<>(
                schemaRegistry,
                configs,
                Fruit.class
        );

        assertNull(deserializer.serialize(topic, null));
        assertNull(deserializer.serialize(topic, new RecordHeaders(),null));
    }

    @Test
    public void shouldBeCompatibleWithPlainProtobufDeserializer() {

        Fruit preferredMelon = FruitFixture.waterMelon().build();

        KafkaGdprAwareProtobufSerializer<Fruit> serializer = new KafkaGdprAwareProtobufSerializer<>(
                schemaRegistry,
                configs,
                Fruit.class
        );
        KafkaProtobufDeserializer<Fruit> deserializer = new KafkaProtobufDeserializer<>(
                schemaRegistry,
                configs,
                Fruit.class
        );

        byte[] serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }


}