package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaGdprAwareProtobufSerializer<T extends Message> implements Serializer<T> {

    private static final RecordHeaders EMPTY_HEADERS = new RecordHeaders();

    private final KafkaProtobufSerializer<T> inner;
    private final EncryptionEngine<T> encryptionEngine = new EncryptionEngine<>();

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
        return this.serialize(topic, EMPTY_HEADERS, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (data == null) {
            return null;
        }

        T encrypted = encryptionEngine.encrypt(data);

        return inner.serialize(topic, headers, encrypted);
    }

    @Override
    public void close() {
        inner.close();
    }
}
