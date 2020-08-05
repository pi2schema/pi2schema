package com.github.gustavomonarin.kafkagdpr.protobuf.kafka.serializers;

import com.github.gustavomonarin.kafkagdpr.core.kms.Decryptor;
import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

import static org.apache.kafka.common.record.Record.EMPTY_HEADERS;

public class KafkaGdprAwareProtobufDeserializer<T extends Message> implements Deserializer<T> {

    private static final RecordHeaders EMPTY_HEADERS = new RecordHeaders();

    private final KafkaProtobufDeserializer<T> inner;
    private ProtobufDecryptionEngine<T> decryptionEngine;

    public KafkaGdprAwareProtobufDeserializer() {
        this.inner = new KafkaProtobufDeserializer<>();
    }

    KafkaGdprAwareProtobufDeserializer(Decryptor decryptor,
                                       SchemaRegistryClient schemaRegistry,
                                       Map<String, ?> configs,
                                       Class<T> clazz) {
        this.inner = new KafkaProtobufDeserializer<>(schemaRegistry, configs, clazz);
        decryptionEngine = new ProtobufDecryptionEngine<>(decryptor);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.inner.configure(configs, isKey);
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        return this.deserialize(topic, EMPTY_HEADERS, data);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        T deserialized = this.inner.deserialize(topic, headers, data);

        return decryptionEngine.decrypt(deserialized);
    }

    @Override
    public void close() {
        this.inner.close();
    }
}
