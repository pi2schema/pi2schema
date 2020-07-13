package com.github.gustavomonarin.kafkagdpr.serializers.protobuf.confluent;

import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaGdprAwareProtobufSerializer<T extends Message> implements Serializer<T> {

    private final KafkaProtobufSerializer<T> inner;

    public KafkaGdprAwareProtobufSerializer() {
        this.inner = new KafkaProtobufSerializer<>();
    }

    KafkaGdprAwareProtobufSerializer(SchemaRegistryClient schemaRegistry,
                                     Map<String, ?> configs,
                                     Class<T> clazz) {
        this.inner = new KafkaProtobufSerializer<>(schemaRegistry, configs);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        inner.configure(configs, isKey);
    }

    @Override
    public byte[] serialize(String topic, T data) {
        return inner.serialize(topic, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        return inner.serialize(topic, headers, data);
    }

    @Override
    public void close() {
        inner.close();
    }
}
