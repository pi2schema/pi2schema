package pi2schema.serialization.kafka.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientFactory;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;
import io.confluent.kafka.schemaregistry.json.SpecificationVersion;
import io.confluent.kafka.schemaregistry.json.jackson.Jackson;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializerConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializerConfig;
import io.confluent.kafka.serializers.schema.id.SchemaId;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
import org.apache.commons.lang3.ClassUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import pi2schema.schema.providers.jsonschema.JsonSchemaProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import static io.confluent.kafka.serializers.schema.id.SchemaId.VALUE_SCHEMA_ID_HEADER;

/**
 * Kafka-aware JSON Schema provider that retrieves schemas from Schema Registry.
 * Supports both consumer case (schema ID provided) and producer case (schema discovery from business object).
 */
public class KafkaJsonSchemaProvider implements JsonSchemaProvider, Configurable {

    private ObjectMapper objectMapper = Jackson.newObjectMapper();
    private SubjectNameStrategy subjectNameStrategy;
    private SpecificationVersion specVersion;
    private SchemaRegistryClient schemaRegistryClient;
    private List<String> scanPackages;
    private Boolean oneOfForNullables;
    private Boolean failUnknownProperties;

    @Override
    public JsonNode schemaFor(Object businessObject, Object context) {
        var contextParser = ContextParser.parserFor(context);

        return contextParser
            .schemaId(context)
            .map(this::getSchemaById)
            .orElseGet(() -> discoverSchemaForRecord(contextParser.topic(context), false, businessObject));
    }

    /**
     * retrieve schema by ID from Schema Registry
     */
    private JsonNode getSchemaById(UUID schemaId) {
        try {
            var schemaMetadata = schemaRegistryClient.getSchemaByGuid(schemaId.toString(), JsonSchema.TYPE);
            return ((JsonSchema) schemaMetadata).toJsonNode();
        } catch (IOException | RestClientException e) {
            throw new RuntimeException("Failed to retrieve schema with ID " + schemaId + " from Schema Registry", e);
        }
    }

    /**
     * discover schema for business object using Confluent patterns
     */
    private JsonNode discoverSchemaForRecord(String topic, boolean isKey, Object businessObject) {
        try {
            // Use Confluent's JsonSchemaUtils to create schema from business object
            var jsonSchema = JsonSchemaUtils.getSchema(
                businessObject,
                specVersion,
                scanPackages,
                oneOfForNullables,
                failUnknownProperties,
                objectMapper,
                schemaRegistryClient
            );

            // Use Confluent's subject naming strategy to determine subject name
            String subject = subjectNameStrategy.subjectName(topic, isKey, jsonSchema);

            var result = schemaRegistryClient.getLatestSchemaMetadata(subject);

            return objectMapper.readTree(result.getSchema());
        } catch (RestClientException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void configure(Map<String, ?> map) {
        KafkaJsonSchemaSerializerConfig config = new KafkaJsonSchemaSerializerConfig(map);
        this.subjectNameStrategy = config.valueSubjectNameStrategy();
        var writeDatesAsIso8601 = config.getBoolean(KafkaJsonSchemaSerializerConfig.WRITE_DATES_AS_ISO8601);
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, !writeDatesAsIso8601);
        String inclusion = config.getString(KafkaJsonSchemaSerializerConfig.DEFAULT_PROPERTY_INCLUSION);
        if (inclusion != null) {
            this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.valueOf(inclusion));
        }
        this.specVersion =
            SpecificationVersion.get(config.getString(KafkaJsonSchemaSerializerConfig.SCHEMA_SPEC_VERSION));
        this.scanPackages = config.getList(KafkaJsonSchemaSerializerConfig.SCHEMA_SCAN_PACKAGES);
        this.oneOfForNullables = config.getBoolean(KafkaJsonSchemaSerializerConfig.ONEOF_FOR_NULLABLES);
        this.failUnknownProperties = config.getBoolean(KafkaJsonSchemaDeserializerConfig.FAIL_UNKNOWN_PROPERTIES);
        this.schemaRegistryClient =
            SchemaRegistryClientFactory.newClient(
                config.getSchemaRegistryUrls(),
                config.getMaxSchemasPerSubject(),
                List.of(new io.confluent.kafka.schemaregistry.json.JsonSchemaProvider()),
                config.originalsWithPrefix(""),
                config.requestHeaders()
            );
    }

    private sealed interface ContextParser<T> permits ConsumerContextParser, ProducerContextParser {
        ConsumerContextParser consumerParser = new ConsumerContextParser();
        ProducerContextParser producerParser = new ProducerContextParser();

        String topic(T context);

        Optional<UUID> schemaId(T context);

        default Optional<UUID> schemaId(Headers headers) {
            return Optional
                .ofNullable(headers.lastHeader(VALUE_SCHEMA_ID_HEADER))
                .map(Header::value)
                .map(ByteBuffer::wrap)
                .map(bytes -> {
                    SchemaId schemaId = new SchemaId(JsonSchema.TYPE);
                    schemaId.fromBytes(bytes);
                    return schemaId.getGuid();
                });
        }

        static <X> ContextParser<X> parserFor(X context) {
            if (context instanceof ConsumerRecord<?, ?>) {
                return (ContextParser<X>) consumerParser;
            } else if (context instanceof ProducerRecord<?, ?>) {
                return (ContextParser<X>) producerParser;
            } else {
                throw new IllegalArgumentException("Unsupported context type: " + ClassUtils.getName(context));
            }
            //            return switch (context) { // java 21
            //                case ConsumerRecord<?, ?> consumerRecord -> (ContextParser<ConsumerRecord<?, ?>>) consumerParser;
            //                case ProducerRecord<?, ?> producerRecord -> (ContextParser<T>) producerParser;
            //                default -> throw new IllegalArgumentException("Unsupported context type: " + context.getClass().getName());
            //            };
        }
    }

    private static final class ConsumerContextParser implements ContextParser<ConsumerRecord<?, ?>> {

        @Override
        public String topic(ConsumerRecord<?, ?> context) {
            return context.topic();
        }

        @Override
        public Optional<UUID> schemaId(ConsumerRecord<?, ?> context) {
            return schemaId(context.headers());
        }
    }

    private static final class ProducerContextParser implements ContextParser<ProducerRecord<?, ?>> {

        @Override
        public String topic(ProducerRecord<?, ?> context) {
            return context.topic();
        }

        @Override
        public Optional<UUID> schemaId(ProducerRecord<?, ?> context) {
            return schemaId(context.headers());
        }
    }
}
