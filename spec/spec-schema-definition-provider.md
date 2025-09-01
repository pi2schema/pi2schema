---
title: Schema Definition Provider Architecture for Pi2Schema Framework
version: 1.0
date_created: 2025-08-08
last_updated: 2025-08-08
owner: pi2schema
tags: [schema, spi, architecture, provider, abstraction]
---

# Schema Definition Provider Architecture for Pi2Schema Framework

A specification defining the architectural separation between schema definition retrieval and personal metadata analysis within the pi2schema framework, establishing clear SPI interfaces for schema providers and their interaction with metadata providers.

## 1. Purpose & Scope

This specification defines the requirements for implementing a clear separation of concerns between schema definition retrieval and PII metadata analysis. The goal is to establish a consistent architecture where `SchemaProvider` interfaces handle schema definition discovery, while `PersonalMetadataProvider` interfaces receive schema definitions as parameters instead of extracting them directly from business objects.

**Intended Audience**: Java developers implementing schema providers and adapters for the pi2schema framework.

**Assumptions**: 
- Familiarity with Service Provider Interface (SPI) patterns
- Understanding of schema definition formats (Protobuf, Avro, JSON Schema)
- Knowledge of dependency injection and interface-based design
- Understanding of Kafka serialization and adapter patterns

## 2. Definitions

- **Schema Definition**: The structural metadata describing data format (e.g., Protobuf Descriptor, Avro Schema, JSON Schema)
- **Schema Provider**: Component responsible for discovering/retrieving schema definitions from business objects
- **Personal Metadata Provider**: Component responsible for analyzing schema definitions to identify PII fields
- **Business Object**: The actual data object being processed (e.g., Protobuf Message, Avro SpecificRecord, POJO)
- **SPI (Service Provider Interface)**: Generic interface defining contracts for implementations
- **Provider**: Concrete implementation of SPI interfaces for specific technologies
- **Adapter**: Integration component bridging providers with external systems

## 3. Requirements, Constraints & Guidelines

### Core Architecture Requirements

- **REQ-001**: `SchemaProvider<S>` interface SHALL be responsible for schema definition discovery only
- **REQ-002**: `PersonalMetadataProvider<T>` interface SHALL accept schema definitions as parameters
- **REQ-003**: Schema providers SHALL be technology-specific (Protobuf, Avro, JSON Schema)
- **REQ-004**: Personal metadata providers SHALL be decoupled from schema discovery logic
- **REQ-005**: Adapters/Interceptors SHALL orchestrate the interaction between schema and metadata providers
- **REQ-006**: All schema providers SHALL implement the generic `SchemaProvider<S>` SPI interface
- **REQ-007**: Schema providers SHALL support both producer and consumer scenarios

### Method Signature Requirements

- **REQ-008**: `SchemaProvider.schemaFor(Object, Object)` SHALL return schema definition
- **REQ-009**: `PersonalMetadataProvider.forSchema(S schema)` SHALL accept schema definition parameter
- **REQ-010**: Schema providers SHALL handle schema registry integration when applicable

### Separation of Concerns Constraints

- **CON-001**: Schema providers SHALL NOT perform PII analysis
- **CON-002**: Personal metadata providers SHALL NOT perform schema discovery
- **CON-003**: Business object introspection SHALL be limited to schema providers only
- **CON-004**: The implementation SHALL be compatible with Java 17+

### Performance Requirements

- **PER-001**: Schema discovery SHALL be optimized for runtime performance
- **PER-002**: Schema definitions SHALL be reusable across multiple metadata operations
- **PER-003**: Provider initialization SHALL be optimized for runtime performance

### Implementation Guidelines

- **GUD-001**: Use composition over inheritance for provider implementations
- **GUD-002**: Implement fail-fast validation for invalid schema definitions
- **GUD-003**: Provide clear error messages for unsupported schema formats
- **GUD-004**: Follow existing package structure patterns: `pi2schema.schema.providers.<technology>`
- **GUD-005**: Use dependency injection for provider configuration in adapters

## 4. Interfaces & Data Contracts

### Core SPI Interfaces

#### SchemaProvider<S> Interface
```java
public interface SchemaProvider<S> {
    /**
     * Discovers the schema for a given business object using optional context information.
     * @param businessObject The object to find schema for
     * @param context Optional context that provides additional information for schema discovery.
     *                Different adapters can provide different context types (e.g., ConsumerRecord for Kafka,
     *                SQS Message for AWS, etc.)
     * @return The schema definition instance
     * @throws SchemaNotFoundException if no schema can be found
     */
    S schemaFor(Object businessObject, Object context);
    
    /**
     * Discovers the schema for a given business object without additional context.
     * @param businessObject The object to find schema for
     * @return The schema definition instance
     */
    default S schemaFor(Object businessObject) {
        return schemaFor(businessObject, null);
    }
}
```

#### Enhanced PersonalMetadataProvider<T> Interface
```java
public interface PersonalMetadataProvider<T> {
    /**
     * Creates PersonalMetadata for a given schema definition.
     * This is the method for schema-based providers.
     * @param schema The schema definition to analyze
     * @return PersonalMetadata containing PII field definitions
     */
    PersonalMetadata<T> forSchema(S schema);
}
```

### Technology-Specific Providers

#### Protobuf Schema Provider
```java
public interface ProtobufSchemaProvider extends SchemaProvider<Descriptor> {
    // Inherits schemaFor methods with Descriptor return type
}
```

#### Avro Schema Provider
```java
public interface AvroSchemaProvider extends SchemaProvider<org.apache.avro.Schema> {
    // Inherits schemaFor methods with Avro Schema return type
}
```

#### JSON Schema Provider
```java
public interface JsonSchemaProvider extends SchemaProvider<JsonNode> {
    // Inherits schemaFor methods with JsonNode return type
}
```

### Adapter Integration Pattern

#### Updated Interceptor Pattern
```java
public class KafkaGdprAwareProducerInterceptor<K, V> {
    private SchemaProvider<S> schemaProvider;
    private PersonalMetadataProvider<V> metadataProvider;
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (record.value() == null) return record;
        
        // Step 1: Discover schema definition
        S schema = schemaProvider.schemaFor(record.value());
        
        // Step 2: Analyze schema for PII metadata
        PersonalMetadata<V> metadata = metadataProvider.forSchema(schema);
        
        // Step 3: Perform encryption
        V encryptedValue = metadata.swapToEncrypted(encryptor, record.value());
        
        return new ProducerRecord<>(/*...*/);
    }
}
```

## 5. Acceptance Criteria

- **AC-001**: Given a business object, When SchemaProvider.schemaFor() is called, Then it returns the appropriate schema definition without performing PII analysis
- **AC-002**: Given a schema definition, When PersonalMetadataProvider.forSchema() is called, Then it returns PersonalMetadata with identified PII fields
- **AC-003**: Given an adapter configuration, When both schema and metadata providers are configured, Then they work together to process PII data correctly
- **AC-004**: Given a Kafka consumer scenario, When schema ID is provided, Then schema provider retrieves correct schema from registry
- **AC-005**: Given a Kafka producer scenario, When business object is provided, Then schema provider discovers appropriate schema definition

## 6. Test Automation Strategy

### Unit Testing
- **SchemaProvider implementations**: Test schema discovery for various business object types
- **PersonalMetadataProvider implementations**: Test PII analysis with provided schema definitions
- **Mock schema definitions**: Test metadata providers with controlled schema inputs

### Integration Testing
- **Adapter orchestration**: Test complete flow from business object to encrypted data
- **Schema registry integration**: Test Kafka scenarios with actual schema registry
- **Cross-provider compatibility**: Test schema definitions work across different metadata providers

### Performance Testing
- **Schema caching**: Verify schema discovery performance with caching enabled
- **Memory allocation**: Monitor object creation during schema and metadata operations
- **Concurrent access**: Test thread safety of provider implementations

## 7. Rationale & Context

### Design Decisions

**Separation of Schema Discovery and PII Analysis**: This separation allows for better testability, reusability, and maintainability. Schema definitions can be shared and reused across multiple PII operations.

**Generic SchemaProvider Interface**: Using generics allows type-safe implementations while maintaining a common contract across different schema formats.

**Adapter Orchestration**: Moving orchestration logic to adapters keeps providers focused on their specific responsibilities.

### Migration Strategy

The transition will be clean and direct:
1. Implement new schema provider interfaces for each technology (Protobuf, Avro, JSON Schema)
2. Add `forSchema()` methods to metadata providers as the sole metadata creation method
3. Update adapters to use the new orchestration pattern
4. Update documentation and examples to reflect the clean separation of concerns

## 8. Dependencies & External Integrations

### Infrastructure Dependencies
- **INF-001**: pi2schema schema-spi - Core SPI interfaces and abstractions
- **INF-002**: pi2schema crypto-spi - Cryptographic operations interface
- **INF-003**: Schema format libraries - Protobuf, Avro, Jackson for schema handling

### External Systems
- **EXT-001**: Kafka Schema Registry - Schema storage and versioning for Kafka adapters
- **EXT-002**: Serialization frameworks - Integration points for various data formats

### Technology Platform Dependencies
- **PLT-001**: Java 17+ - Platform requirement for all implementations
- **PLT-002**: Apache Kafka - For serialization adapter implementations

## 9. Examples & Edge Cases

### Basic Usage Example
```java
// Schema discovery
ProtobufSchemaProvider schemaProvider = new ProtobufSchemaProvider();
Descriptor schema = schemaProvider.schemaFor(businessObject);

// PII analysis
ProtobufPersonalMetadataProvider<MyMessage> metadataProvider = 
    new ProtobufPersonalMetadataProvider<>();
PersonalMetadata<MyMessage> metadata = metadataProvider.forSchema(schema);

// Encryption
MyMessage encrypted = metadata.swapToEncrypted(encryptor, businessObject);
```

### Kafka Adapter Example
```java
public class KafkaAdapter {
    private final JsonSchemaProvider schemaProvider;
    private final JsonSchemaPersonalMetadataProvider<Map> metadataProvider;
    
    public Object process(Object businessObject) {
        JsonNode schema = schemaProvider.schemaFor(businessObject);
        PersonalMetadata<Map> metadata = metadataProvider.forSchema(schema);
        return metadata.swapToEncrypted(encryptor, businessObject);
    }
}
```

### Edge Cases
- **Unknown schema format**: SchemaProvider should throw SchemaNotFoundException
- **Unsupported business object**: Provider should fail fast with clear error message
- **Null schema definition**: PersonalMetadataProvider should handle gracefully
- **Schema registry unavailable**: Kafka providers should implement appropriate fallback behavior

## 10. Validation Criteria

- All new schema provider implementations implement `SchemaProvider<S>` interface
- All metadata providers support `forSchema(S schema)` method
- Existing functionality remains intact through deprecated method support
- Performance metrics show no regression in schema discovery or PII processing
- Adapters successfully orchestrate schema and metadata provider interactions
- Documentation clearly explains the new architecture and migration path

## 11. Related Specifications / Further Reading

- [Schema Definition Provider - Protobuf Implementation](spec-schema-definition-provider-protobuf.md)
- [Schema Definition Provider - Avro Implementation](spec-schema-definition-provider-avro.md)
- [Schema Definition Provider - JSON Schema Implementation](spec-schema-definition-provider-jsonschema.md)
- [Pi2Schema SPI Architecture Documentation](../schema-spi/README.md)
- [Kafka Serialization Adapters Integration](../serialization-adapters-kafka/README.md)
