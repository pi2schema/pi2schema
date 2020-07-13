package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class KafkaGdprAwareProtobufDeserializer<T extends Message> implements Deserializer<T> {

    private final KafkaProtobufDeserializer<T> inner;

    public KafkaGdprAwareProtobufDeserializer() {
        this.inner = new KafkaProtobufDeserializer<>();
    }

    KafkaGdprAwareProtobufDeserializer(SchemaRegistryClient schemaRegistry,
                                              Map<String, ?> configs,
                                              Class<T> clazz) {
        this.inner = new KafkaProtobufDeserializer<>(schemaRegistry, configs, clazz);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.inner.configure(configs, isKey);
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        return this.inner.deserialize(topic, data);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        return this.inner.deserialize(topic, headers, data);
    }

    @Override
    public void close() {
        this.inner.close();
    }
}
