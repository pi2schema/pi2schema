---
title: JSON Schema Definition Provider Implementation
version: 1.0
date_created: 2025-08-08
last_updated: 2025-08-08
owner: pi2schema
tags: [schema, json-schema, provider, spi, implementation]
---

# JSON Schema Definition Provider Implementation

A specification for implementing JSON Schema definition discovery within the pi2schema framework, focusing on extracting JSON Schema information from business objects while maintaining separation from PII metadata analysis.

## 1. Purpose & Scope

This specification defines the requirements for implementing a JSON Schema-specific provider that discovers schema definitions for business objects. Unlike Protobuf and Avro which have built-in schema methods, JSON Schema requires more sophisticated discovery mechanisms including annotation-based detection, Schema Registry integration, and object introspection.

**Intended Audience**: Java developers implementing JSON Schema providers for the pi2schema framework.

**Assumptions**: 
- Familiarity with JSON Schema specification (Draft 7 or later)
- Understanding of Jackson JSON processing and object mapping
- Knowledge of the pi2schema SPI architecture
- Understanding of Kafka serialization patterns with JSON Schema

## 2. Definitions

- **JSON Schema**: JSON document describing the structure, constraints, and metadata of JSON data
- **Business Object**: Any Java object that can be serialized to JSON (POJO, Map, etc.)
- **Schema Registry**: External system storing and versioning JSON schemas
- **JsonNode**: Jackson representation of parsed JSON Schema documents
- **Schema Annotation**: Java annotations indicating schema location or metadata
- **Schema Inference**: Process of deriving schema from object structure

## 3. Requirements, Constraints & Guidelines

### Core Requirements

- **REQ-001**: The provider SHALL implement `SchemaProvider<JsonNode>` interface  
- **REQ-002**: The provider SHALL support schema discovery from Schema Registry
- **REQ-003**: The provider SHALL integrate with Kafka Schema Registry when schema ID is provided
- **REQ-004**: The provider SHALL support basic schema discovery for business objects
- **REQ-005**: The provider SHALL handle any serializable Java object type
- **REQ-006**: The provider SHALL be thread-safe for concurrent access
- **REQ-007**: The provider SHALL provide clear error messages when schema discovery fails

### Schema Discovery Strategies

- **REQ-009**: Consumer scenario SHALL retrieve schema from Schema Registry using provided schema ID
- **REQ-010**: Producer scenario SHALL lookup schema in Schema Registry  
- **REQ-011**: The provider SHALL support Confluent Schema Registry JSON Schema format
- **REQ-012**: The provider SHALL handle schema evolution and compatibility rules

### Schema Registry Integration

- **REQ-013**: The provider SHALL integrate with Kafka Schema Registry when schema ID is provided
- **REQ-014**: The provider SHALL support basic schema discovery for producer scenarios
- **REQ-015**: The provider SHALL handle schema registry connectivity issues gracefully

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
- **GUD-005**: Support fallback to schema inference as last resort

## 4. Interfaces & Data Contracts

### Core Interface Implementation

```java
package pi2schema.schema.providers.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pi2schema.schema.SchemaProvider;
import pi2schema.schema.SchemaNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * JSON Schema-specific provider that discovers schema definitions
 * for business objects, primarily through Schema Registry integration.
 */
public class JsonSchemaProvider implements SchemaProvider<JsonNode> {
    
    private final ObjectMapper objectMapper;
    
    public JsonSchemaProvider() {
        this.objectMapper = new ObjectMapper();
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
        
        // Producer case: basic schema discovery
        return discoverSchemaForProducer(businessObject);
    }
    
    private JsonNode getSchemaById(Integer schemaId) {
        // Implementation for Schema Registry integration
        throw new UnsupportedOperationException("Schema Registry integration not yet implemented");
    }
    
    private JsonNode discoverSchemaForProducer(Object businessObject) {
        // Basic implementation - to be extended with discovery strategies in future versions
        throw new SchemaNotFoundException(
            "Producer schema discovery not yet implemented for object type: " + 
            businessObject.getClass().getName()
        );
    }
}
```

### Acceptance Criteria

- **AC-01**: JsonSchemaProvider implements SchemaProvider&lt;JsonNode&gt; interface
- **AC-02**: Schema Registry integration returns schemas by ID for consumer use cases
- **AC-03**: Provider returns JsonNode schemas compatible with PersonalMetadataProvider.forSchema()
- **AC-04**: Jackson ObjectMapper is used for JSON processing
- **AC-05**: SchemaNotFoundException is thrown when schemas cannot be resolved
- **AC-06**: Provider implements fallback logic when schema ID is not available

## Future Enhancements

The following features may be considered for future versions but are not part of the initial implementation:

### Advanced Discovery Strategies
- **Annotation-based Strategy**: Discovery through schema annotations on classes
- **Convention-based Strategy**: Discovery using naming conventions and file system paths  
- **Inference Strategy**: Dynamic schema generation from object structure
- **Multiple Strategy Support**: Configurable discovery strategy chains with fallback mechanisms

These enhancements would extend the basic Schema Registry integration with more sophisticated discovery mechanisms for producer scenarios where schema IDs are not available.

## 5. Implementation Considerations

### Dependencies

- Jackson 2.x for JSON processing
- Confluent Schema Registry client
- SLF4J for logging

### Configuration

```java
// Basic configuration example
JsonSchemaProvider provider = new JsonSchemaProvider();
PersonalMetadataProvider<JsonNode> metadataProvider = 
    PersonalMetadataProvider.forSchema(provider);
```

### Integration with Kafka

```java
// Example Kafka integration
SchemaRegistryClient registryClient = new CachedSchemaRegistryClient(registryUrl, 100);
JsonSchemaProvider schemaProvider = new JsonSchemaProvider(registryClient);

// In consumer
Integer schemaId = getSchemaIdFromKafkaHeaders(consumerRecord);
JsonNode schema = schemaProvider.schemaFor(businessObject, () -> Optional.of(schemaId));
```

## 6. Testing Considerations

### Unit Testing Requirements

- Test schema resolution with valid Schema Registry IDs
- Test error handling for invalid schema IDs  
- Test fallback behavior when schema ID is not provided
- Test JSON parsing and validation
- Test integration with PersonalMetadataProvider

### Integration Testing

- Test with actual Schema Registry instance
- Test Kafka consumer/producer integration
- Test schema evolution scenarios
- Verify compatibility with different JSON Schema versions

### Performance Testing

- Benchmark schema lookup performance
- Test memory usage patterns
- Test concurrent access scenarios

## 7. Dependencies & External Integrations

### Infrastructure Dependencies
- **INF-001**: pi2schema schema-spi - Core SchemaProvider interface
- **INF-002**: Jackson Core - JSON processing and JsonNode representation
- **INF-003**: Jackson Databind - Object mapping and introspection

### External Systems
- **EXT-001**: Confluent Schema Registry - Schema storage and versioning (optional)

### Technology Platform Dependencies
- **PLT-001**: Java 17+ - Platform requirement
- **PLT-002**: Jackson 2.x - JSON processing library
- **PLT-003**: Confluent Schema Registry Client - For Kafka integration (optional)

## 8. Examples & Edge Cases

### Basic Usage Example
```java
// Basic provider setup
JsonSchemaProvider schemaProvider = new JsonSchemaProvider();
PersonalMetadataProvider<JsonNode> metadataProvider = 
    PersonalMetadataProvider.forSchema(schemaProvider);

// Consumer scenario with schema ID
Integer schemaId = getSchemaIdFromKafkaHeaders(consumerRecord);
JsonNode schema = schemaProvider.schemaFor(businessObject, () -> Optional.of(schemaId));

// Analyze PII metadata
Set<String> piiFields = metadataProvider.personalDataFields(businessObject);
```

### Error Handling Examples

```java
try {
    JsonNode schema = schemaProvider.schemaFor(businessObject, () -> Optional.empty());
} catch (SchemaNotFoundException e) {
    log.warn("Schema not found for object type: {}", businessObject.getClass().getName());
    // Handle missing schema scenario
}
```

### Edge Cases

- **Null Objects**: Provider handles null business objects gracefully
- **Missing Schema ID**: Falls back to producer discovery when schema ID is not available
- **Schema Registry Unavailable**: Throws appropriate exceptions with clear error messages
- **Invalid Schema Format**: Validates schema content before returning JsonNode
- **Concurrent Access**: Thread-safe operations for high-throughput scenarios

## 9. Migration & Compatibility

### From Existing Implementation

The current JsonSchemaPersonalMetadataProvider will be updated to:
1. Accept schemas from JsonSchemaProvider
2. Remove internal schema discovery logic
3. Maintain backward compatibility through deprecated methods
4. Provide migration path for existing users

### Backward Compatibility

- Existing PersonalMetadataProvider.forSchema() methods remain functional
- Deprecated methods provide bridge to new architecture
- Clear migration documentation and examples provided

### Kafka Consumer Example
```java
// Consumer scenario - schema ID from Kafka headers
Supplier<Optional<Integer>> schemaIdSupplier = () -> Optional.of(schemaId);
JsonNode schema = provider.schemaFor(deserializedObject, schemaIdSupplier);
```

### Edge Cases
- **Circular References**: Provider should handle objects with circular references gracefully
- **Generic Collections**: Provider should infer appropriate array/object schemas for collections
- **Null Values**: Provider should handle objects with null fields appropriately
- **Complex Inheritance**: Provider should handle polymorphic object hierarchies
- **Missing Resources**: Provider should provide clear error messages for missing schema files
- **Invalid JSON Schema**: Provider should validate loaded schemas and report format errors

## 10. Validation Criteria

- Provider successfully discovers schemas using all configured strategies
- Schema Registry integration works with both producer and consumer scenarios
- Annotation-based discovery loads schemas from classpath resources correctly
- Convention-based discovery follows expected naming patterns
- Schema inference generates valid JSON Schema documents
- Error handling provides clear guidance for schema discovery failures
- Performance metrics show acceptable overhead for schema discovery
- Thread safety is maintained under concurrent access
- Compatibility is maintained with existing JSON Schema PersonalMetadataProvider

## 11. Related Specifications / Further Reading

- [Schema Definition Provider Architecture](spec-schema-definition-provider.md)
- [JSON Schema Personal Metadata Provider](spec-schema-jsonschema-provider.md)
- [Kafka Serialization Adapters](../serialization-adapters-kafka/README.md)
- [JSON Schema Specification](https://json-schema.org/)
- [Confluent Schema Registry JSON Schema Support](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-json.html)
