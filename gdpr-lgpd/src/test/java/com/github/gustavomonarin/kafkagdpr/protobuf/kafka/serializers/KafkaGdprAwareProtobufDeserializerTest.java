package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.acme.FruitFixture;
import com.acme.FruitOuterClass.Fruit;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class KafkaGdprAwareProtobufDeserializerTest {

    private final String topic = "test";
    private final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    private final Map<String, Object> configs;

    public KafkaGdprAwareProtobufDeserializerTest() {
        HashMap<String, Object> initial = new HashMap<>();
        initial.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        initial.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "bogus");
        this.configs = Collections.unmodifiableMap(initial);
    }

    @Test
    public void shouldSupportNullRecordReturningNullData(){
        KafkaGdprAwareProtobufDeserializer<Fruit> deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                schemaRegistry,
                configs,
                Fruit.class
        );

        assertNull(deserializer.deserialize(topic, null));
        assertNull(deserializer.deserialize(topic, new RecordHeaders(),null));
    }

    @Test
    public void shouldBeCompatibleWithPlainProtobufSerializer() {

        Fruit preferredMelon = FruitFixture.waterMelon().build();

        KafkaProtobufSerializer<Fruit> serializer = new KafkaProtobufSerializer<>(
                schemaRegistry,
                configs
        );
        KafkaGdprAwareProtobufDeserializer<Fruit> deserializer = new KafkaGdprAwareProtobufDeserializer<>(
                schemaRegistry,
                configs,
                Fruit.class
        );

        byte[] serialized = serializer.serialize(topic, preferredMelon);
        assertEquals(preferredMelon, deserializer.deserialize(topic, serialized));
    }

}
