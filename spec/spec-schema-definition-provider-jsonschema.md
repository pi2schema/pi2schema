---
title: JSON Schema Definition Provider Implementation
version: 1.0
date_created: 2025-08-08
last_updated: 2025-08-25
owner: pi2schema
tags: [schema, json-schema, provider, spi, implementation, kafka, confluent]
---

# JSON Schema Definition Provider Implementation

A specification for implementing JSON Schema definition discovery within the pi2schema framework through Confluent Schema Registry integration. Unlike Protobuf and Avro which have built-in schema methods, JSON Schema requires external schema discovery via Schema Registry.

## 1. Purpose & Scope

This specification defines the requirements for implementing a JSON Schema provider that discovers schema definitions for business objects through Confluent Schema Registry integration using multiple registry-based discovery strategies. JSON objects do not have inherent schema information like Protobuf and Avro records, making Schema Registry the only viable source for schema discovery. When all registry-based strategies fail, the provider SHALL throw SchemaNotFoundException to maintain compliance with lookup-only patterns.

**Intended Audience**: Java developers implementing JSON Schema providers for the pi2schema framework with Kafka/Confluent integration.

**Assumptions**: 
- Familiarity with JSON Schema specification (Draft 7 or later)
- Understanding of Confluent Schema Registry and Kafka serialization patterns
- Knowledge of the pi2schema SPI architecture
- Understanding that JSON objects require external schema discovery through registry-based strategies only

## 2. Definitions

- **JSON Schema**: JSON document describing the structure, constraints, and metadata of JSON data
- **Business Object**: Any Java object that can be serialized to JSON (POJO, Map, etc.)
- **Schema Registry**: Confluent Schema Registry storing and versioning JSON schemas
- **JsonNode**: Jackson representation of parsed JSON Schema documents
- **Subject Name Strategy**: Confluent strategy for determining schema subject names
- **Schema ID**: Unique identifier for schemas in Confluent Schema Registry

## 3. Requirements, Constraints & Guidelines

### Core Requirements

- **REQ-001**: The provider SHALL implement `JsonSchemaProvider` interface (which extends `SchemaProvider<JsonNode>`)
- **REQ-002**: The provider SHALL integrate with Confluent Schema Registry for all schema discovery
- **REQ-003**: The provider SHALL support consumer scenarios (schema ID provided from Kafka headers)
- **REQ-004**: The provider SHALL support producer scenarios (schema discovery from business objects)
- **REQ-005**: The provider SHALL use Confluent's JsonSchemaUtils for schema generation
- **REQ-006**: The provider SHALL use configurable SubjectNameStrategy for subject determination
- **REQ-007**: The provider SHALL be thread-safe for concurrent access
- **REQ-008**: The provider SHALL provide clear error messages when schema discovery fails

### Schema Discovery Strategies

- **REQ-009**: Consumer scenario SHALL retrieve schema from Schema Registry using provided schema ID
- **REQ-010**: Producer scenario SHALL use JsonSchemaUtils.getSchema() for schema generation
- **REQ-011**: Producer scenario SHALL use SubjectNameStrategy for subject name determination
- **REQ-012**: The provider SHALL handle schema evolution and compatibility rules
- **REQ-013**: The provider SHALL follow Confluent's lookup patterns (no automatic registration)

### Schema Registry Integration

- **REQ-014**: The provider SHALL integrate with Kafka Schema Registry when schema ID is provided
- **REQ-015**: The provider SHALL support basic schema discovery for producer scenarios
- **REQ-016**: The provider SHALL handle schema registry connectivity issues gracefully

### Performance Requirements

- **PER-001**: Schema discovery SHALL be optimized for runtime performance

### Compatibility Constraints

- **CON-001**: The provider SHALL be compatible with Jackson 2.x
- **CON-002**: The provider SHALL work with Confluent Schema Registry
- **CON-003**: The provider SHALL support standard JSON Schema Draft 7+
- **CON-004**: The provider SHALL maintain compatibility with existing JSON serializers

### Error Handling Guidelines

- **GUD-001**: Throw `SchemaNotFoundException` when no discovery strategy succeeds
- **GUD-002**: Provide clear error messages indicating which strategies were attempted
- **GUD-003**: Handle null business objects gracefully
- **GUD-004**: Log appropriate debugging information for troubleshooting
- **GUD-005**: Attempt multiple registry-based discovery strategies, then throw SchemaNotFoundException on failure (consistent with REQ-013 lookup-only patterns)

## 4. Interfaces & Data Contracts

### Core Interface Implementation

The JSON Schema provider is implemented through the `KafkaJsonSchemaProvider` class in the `serialization-adapters-kafka` module, which integrates directly with Confluent Schema Registry:

```java
package pi2schema.serialization.kafka.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
import pi2schema.schema.providers.jsonschema.JsonSchemaProvider;

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
        // Constructor implementation
    }
    
    @Override
    public JsonNode schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier) {
        // Consumer case: schema ID is provided (from Kafka headers)
        if (schemaIdSupplier != null) {
            Optional<Integer> schemaId = schemaIdSupplier.get();
            if (schemaId.isPresent()) {
                return getSchemaById(schemaId.get());
            }
        }
        
        // Producer case: derive schema from business object using Confluent patterns
        return discoverSchemaForProducer(businessObject);
    }
    
    private JsonNode getSchemaById(Integer schemaId) {
        // Retrieve schema from Schema Registry by ID
        // Implementation uses schemaRegistryClient.getSchemaById()
    }
    
    private JsonNode discoverSchemaForProducer(Object businessObject) {
        // Use JsonSchemaUtils.getSchema() and SubjectNameStrategy
        // for producer scenario schema discovery
    }
}
```

### Data Contracts

#### Input Requirements
- Business object MUST be a valid Java object serializable to JSON
- SchemaRegistryClient MUST be configured and accessible
- Topic name and isKey flag MUST be provided for proper subject name resolution
- SubjectNameStrategy MUST be configured for subject name determination

#### Output Requirements
- Returns `com.fasterxml.jackson.databind.JsonNode` representing the JSON Schema
- Schema MUST be retrieved from Confluent Schema Registry
- Schema MUST be compatible with JSON Schema Draft 7+
- Schema MUST be suitable for PII analysis by JsonSchemaPersonalMetadataProvider

## 5. Acceptance Criteria

- **AC-01**: KafkaJsonSchemaProvider implements JsonSchemaProvider interface
- **AC-02**: Schema Registry integration returns schemas by ID for consumer use cases
- **AC-03**: Producer scenarios use JsonSchemaUtils.getSchema() for schema generation
- **AC-04**: SubjectNameStrategy is used for proper subject name determination
- **AC-05**: Provider returns JsonNode schemas compatible with PersonalMetadataProvider.forSchema()
- **AC-06**: Clear error messages are provided when Schema Registry is unavailable
- **AC-07**: Thread-safe operations support concurrent Kafka consumer/producer usage

## 6. Implementation Considerations

### Dependencies

- Jackson 2.x for JSON processing
- Confluent Schema Registry client for schema retrieval
- Confluent JsonSchemaUtils for schema generation from business objects
- SLF4J for logging

### Configuration

```java
// Kafka JSON Schema provider setup
SchemaRegistryClient registryClient = new CachedSchemaRegistryClient(registryUrl, 100);
SubjectNameStrategy strategy = new TopicNameStrategy();
KafkaJsonSchemaProvider provider = new KafkaJsonSchemaProvider(
    registryClient, 
    "user-events", 
    false, // isKey
    strategy
);

// Use with PersonalMetadataProvider
JsonSchemaPersonalMetadataProvider metadataProvider = new JsonSchemaPersonalMetadataProvider();
```

### Integration with Kafka

```java
// Consumer scenario - schema ID from Kafka headers
Integer schemaId = getSchemaIdFromKafkaHeaders(consumerRecord);
JsonNode schema = provider.schemaFor(businessObject, () -> Optional.of(schemaId));

// Producer scenario - schema discovery from business object
JsonNode schema = provider.schemaFor(businessObject, null);
```

## 7. Current Implementation Status

The JSON Schema provider is **already implemented** in the `serialization-adapters-kafka` module as `KafkaJsonSchemaProvider`. This implementation:

✅ **Complete and Functional:**
- Implements the `JsonSchemaProvider` interface
- Integrates with Confluent Schema Registry
- Supports both consumer and producer scenarios
- Uses `JsonSchemaUtils.getSchema()` for schema generation
- Implements proper subject name strategy handling
- Provides comprehensive error handling

✅ **Supporting Infrastructure:**
- `JsonSchemaPersonalMetadataProvider` for PII analysis
- `JsonPersonalDataFieldDefinition` for field-level metadata
- Complete test suite with integration tests
- Proper Kafka serialization integration

## 8. No Missing Components

Unlike Protobuf and Avro providers which needed local implementations, **the JSON Schema provider is complete and requires no additional implementation**. The architecture correctly recognizes that:

1. **JSON objects have no inherent schema information** (unlike Protobuf/Avro)
2. **Schema Registry is the only viable source** for JSON Schema discovery
3. **KafkaJsonSchemaProvider is the appropriate implementation** for Confluent integration

## 9. Testing Considerations

The existing implementation includes comprehensive testing:
- Unit tests for schema resolution with valid Schema Registry IDs
- Integration tests with actual Schema Registry instances
- Error handling tests for connectivity issues
- Performance tests for concurrent access scenarios
