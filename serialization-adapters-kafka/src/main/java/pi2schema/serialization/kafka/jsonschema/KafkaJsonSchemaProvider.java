package pi2schema.serialization.kafka.jsonschema;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
import org.everit.json.schema.Schema;
import pi2schema.schema.providers.jsonschema.JsonSchemaProvider;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Kafka-aware JSON Schema provider that retrieves schemas from Schema Registry.
 * Supports both consumer case (schema ID provided) and producer case (schema discovery from business object).
 */
public class KafkaJsonSchemaProvider implements JsonSchemaProvider {

    private final SchemaRegistryClient schemaRegistryClient;
    private final SubjectNameStrategy subjectNameStrategy;
    private final String topic;
    private final boolean isKey;

    public KafkaJsonSchemaProvider(
        SchemaRegistryClient schemaRegistryClient,
        String topic,
        boolean isKey,
        SubjectNameStrategy subjectNameStrategy
    ) {
        this.schemaRegistryClient = Objects.requireNonNull(schemaRegistryClient, "schemaRegistryClient cannot be null");
        this.topic = Objects.requireNonNull(topic, "topic cannot be null");
        this.isKey = isKey;
        this.subjectNameStrategy = Objects.requireNonNull(subjectNameStrategy, "subjectNameStrategy cannot be null");
    }

    @Override
    public Schema schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier) {
        // Consumer case: schema ID is provided (retrieved from payload headers)
        if (schemaIdSupplier != null) {
            Optional<Integer> schemaId = schemaIdSupplier.get();
            if (schemaId.isPresent()) {
                return getSchemaById(schemaId.get());
            }
        }

        // Producer case: derive schema from business object
        return discoverSchemaForProducer(businessObject);
    }

    /**
     * Consumer case: retrieve schema by ID from Schema Registry
     */
    private Schema getSchemaById(Integer schemaId) {
        try {
            var schemaMetadata = schemaRegistryClient.getSchemaById(schemaId);
            return convertToEveritSchema(schemaMetadata);
        } catch (IOException | RestClientException e) {
            throw new RuntimeException("Failed to retrieve schema with ID " + schemaId + " from Schema Registry", e);
        }
    }

    /**
     * Producer case: discover schema for business object using Confluent patterns
     */
    private Schema discoverSchemaForProducer(Object businessObject) {
        try {
            // Use Confluent's JsonSchemaUtils to create schema from business object
            JsonSchema jsonSchema = JsonSchemaUtils.getSchema(businessObject);

            // Use Confluent's subject naming strategy to determine subject name
            String subject = subjectNameStrategy.subjectName(topic, isKey, jsonSchema);

            // Get schema ID using proper Confluent approach: lookup existing or register new
            Integer schemaId = getIdForSchema(jsonSchema, subject);

            // Retrieve the schema by ID to ensure consistency
            return getSchemaById(schemaId);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to discover schema for business object of type: " +
                businessObject.getClass().getSimpleName() +
                " on topic: " +
                topic +
                " (isKey: " +
                isKey +
                ")",
                e
            );
        }
    }

    /**
     * Get schema ID using Confluent's lookup approach - only lookup, never register
     * This follows the pattern used in CachedSchemaRegistryClient.getIdFromRegistry()
     */
    private Integer getIdForSchema(JsonSchema jsonSchema, String subject) {
        try {
            // Look up existing schema ID using Confluent's getId method
            // This uses lookUpSubjectVersion internally, not getLatestSchemaMetadata
            return schemaRegistryClient.getId(subject, jsonSchema);
        } catch (RestClientException e) {
            throw new RuntimeException(
                "Schema not found in registry for subject " +
                subject +
                ". Schema must be registered externally before using this provider.",
                e
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to lookup schema ID for subject " + subject, e);
        }
    }

    /**
     * Convert Confluent ParsedSchema to Everit Schema for consistency with JsonSchemaProvider interface
     */
    private Schema convertToEveritSchema(ParsedSchema parsedSchema) {
        return (Schema) parsedSchema.rawSchema();
    }
}
