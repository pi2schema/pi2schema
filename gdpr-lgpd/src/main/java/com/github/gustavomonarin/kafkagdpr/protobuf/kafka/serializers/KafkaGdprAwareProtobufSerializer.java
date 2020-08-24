package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.github.gustavomonarin.kafkagdpr.core.encryption.Encryptor;
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
    private ProtobufEncryptionEngine<T> protobufEncryptionEngine;

    public KafkaGdprAwareProtobufSerializer() {
        this.inner = new KafkaProtobufSerializer<>();

    }

    KafkaGdprAwareProtobufSerializer(Encryptor encryptor,
                                     SchemaRegistryClient schemaRegistry,
                                     Map<String, ?> configs,
                                     Class<T> clazz) {
        this.protobufEncryptionEngine = new ProtobufEncryptionEngine<>(encryptor);
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

        T encrypted = protobufEncryptionEngine.encrypt(data);

        return inner.serialize(topic, headers, encrypted);
    }

    @Override
    public void close() {
        inner.close();
    }
}
