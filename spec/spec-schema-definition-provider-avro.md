---
title: Local Avro Schema Definition Provider Implementation
version: 1.0
date_created: 2025-08-08
last_updated: 2025-08-13
owner: pi2schema
tags: [schema, avro, provider, spi, implementation, local]
---

# Local Avro Schema Definition Provider Implementation

A specification for implementing a local-only Avro schema definition provider within the pi2schema framework, focusing on extracting Avro Schema information from SpecificRecord and GenericRecord objects using only local reflection capabilities while maintaining compatibility with existing implementations.

## 1. Purpose & Scope

This specification defines the requirements for implementing a local-only Avro schema provider that extracts Schema information from Avro record objects using the built-in `getSchema()` method. The provider maintains backward compatibility with existing Avro implementations by operating without external dependencies like Schema Registry.

**Intended Audience**: Java developers implementing Avro schema providers for the pi2schema framework.

**Assumptions**: 
- Familiarity with Apache Avro and Schema API
- Understanding of SpecificRecord interface and Avro code generation
- Knowledge of the pi2schema SPI architecture
- Preference for local-only operation without external system dependencies

## 2. Definitions

- **Avro Schema**: Metadata object describing the structure of an Avro record type
- **SpecificRecord**: Avro interface for generated record classes with compile-time schema access
- **GenericRecord**: Avro interface for dynamic records with runtime schema access
- **Field**: Avro schema element describing individual record fields
- **Union Type**: Avro schema construct representing multiple possible types for a field

## 3. Requirements, Constraints & Guidelines

### Core Requirements

- **REQ-001**: The provider SHALL implement `SchemaProvider<org.apache.avro.Schema>` interface
- **REQ-002**: The provider SHALL extract Schema from Avro SpecificRecord objects using `getSchema()`
- **REQ-003**: The provider SHALL support GenericRecord objects with runtime schema access
- **REQ-004**: The provider SHALL operate in local-only mode without external dependencies
- **REQ-005**: The provider SHALL ignore schema ID supplier parameters to maintain local behavior
- **REQ-006**: The provider SHALL handle unknown record types gracefully
- **REQ-007**: The provider SHALL be thread-safe for concurrent access

### Schema Discovery Requirements

- **REQ-009**: The provider SHALL use `record.getSchema()` for SpecificRecord objects
- **REQ-010**: The provider SHALL use `record.getSchema()` for GenericRecord objects
- **REQ-011**: The provider SHALL validate that business objects are Avro record instances
- **REQ-012**: The provider SHALL maintain compatibility with existing Avro implementations
- **REQ-013**: The provider SHALL not require external schema registry configuration

### Performance Requirements

- **PER-001**: Schema extraction SHALL be optimized for runtime performance
- **PER-002**: Provider initialization SHALL be lightweight and fast

### Compatibility Constraints

- **CON-001**: The provider SHALL be compatible with Apache Avro 1.8+ 
- **CON-002**: The provider SHALL maintain compatibility with existing Avro serializers
- **CON-003**: The provider SHALL support both SpecificRecord and GenericRecord implementations
- **CON-004**: The provider SHALL work without Schema Registry dependencies

### Error Handling Guidelines

- **GUD-001**: Throw `SchemaNotFoundException` for non-Avro business objects
- **GUD-002**: Provide clear error messages for invalid record types
- **GUD-003**: Handle null business objects gracefully
- **GUD-004**: Log appropriate debugging information for troubleshooting

## 4. Interfaces & Data Contracts

### Core Interface Implementation

```java
package pi2schema.schema.providers.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificRecord;
import pi2schema.schema.SchemaProvider;
import pi2schema.schema.SchemaNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Local Avro schema provider that extracts Schema information
 * from Avro SpecificRecord and GenericRecord objects using only local capabilities.
 * This provider maintains compatibility with existing Avro implementations
 * by avoiding Schema Registry dependencies.
 */
public class LocalAvroSchemaProvider implements SchemaProvider<Schema> {
    
    @Override
    public Schema schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier) {
        // Local-only implementation - schema ID supplier is ignored
        return extractSchemaFromRecord(businessObject);
    }
    
    private Schema extractSchemaFromRecord(Object businessObject) {
        if (businessObject == null) {
            throw new SchemaNotFoundException("Business object cannot be null");
        }
        
        if (businessObject instanceof SpecificRecord) {
            return ((SpecificRecord) businessObject).getSchema();
        } else if (businessObject instanceof GenericRecord) {
            return ((GenericRecord) businessObject).getSchema();
        } else {
            throw new SchemaNotFoundException(
                "Object is not an Avro record (SpecificRecord or GenericRecord): " + 
                businessObject.getClass().getName()
            );
        }
    }
}
```

### Schema Utility Methods

```java
/**
 * Utility methods for Avro schema operations.
 */
public class AvroSchemaUtils {
    
    /**
     * Validates that the schema is suitable for PII analysis.
     */
    public static void validateSchemaForPiiAnalysis(Schema schema) {
        if (schema.getType() != Schema.Type.RECORD) {
            throw new IllegalArgumentException("Schema must be a RECORD type for PII analysis");
        }
    }
    
    /**
     * Extracts field schemas for union type analysis.
     */
    public static List<Schema> extractUnionSchemas(Schema.Field field) {
        if (field.schema().getType() == Schema.Type.UNION) {
            return field.schema().getTypes();
        }
        return Collections.singletonList(field.schema());
    }
}
```

### Data Contracts

#### Input Requirements
- Business object MUST be an instance of `SpecificRecord` or `GenericRecord`
- Business object MUST NOT be null
- Schema ID supplier parameter is ignored (local-only operation)

#### Output Requirements
- Returns `org.apache.avro.Schema` instance
- Schema MUST contain complete field metadata
- Schema MUST be suitable for PII analysis by PersonalMetadataProvider
- Schema MUST include union type information where applicable

## 5. Acceptance Criteria

- **AC-001**: Given an Avro SpecificRecord object, When schemaFor() is called, Then it returns the correct Schema from getSchema()
- **AC-002**: Given an Avro GenericRecord object, When schemaFor() is called, Then it returns the correct Schema from getSchema()
- **AC-003**: Given a schema ID supplier parameter, When schemaFor() is called, Then it ignores the schema ID and uses local extraction
- **AC-004**: Given a non-Avro object, When schemaFor() is called, Then it throws SchemaNotFoundException with clear error message
- **AC-005**: Given a null business object, When schemaFor() is called, Then it throws SchemaNotFoundException gracefully
- **AC-006**: Given an AvroPersonalMetadataProvider, When it receives a Schema from this provider, Then it successfully analyzes PII fields

## 6. Test Automation Strategy

### Unit Testing
- Test Schema extraction from various SpecificRecord implementations
- Test Schema extraction from GenericRecord instances
- Test error handling for invalid business objects
- Test union type handling
- Test null object handling

### Integration Testing
- Test with real Avro generated classes
- Test compatibility with PersonalMetadataProvider implementations
- Test thread safety under concurrent access
- Test with complex Avro schemas including unions and nested records

### Performance Testing
- Benchmark Schema extraction performance
- Monitor memory usage for Schema objects
- Test performance with large Avro schemas

## 7. Rationale & Context

### Design Decisions

**Local-Only Operation**: Maintaining compatibility with existing Avro implementations by avoiding external dependencies like Schema Registry, keeping the provider simple and focused.

**Support for Both SpecificRecord and GenericRecord**: This provides maximum flexibility for different Avro usage patterns - compiled classes and dynamic records.

**Ignoring Schema ID Supplier**: To maintain backward compatibility, the provider accepts the schema ID supplier parameter but ignores it, always using local schema extraction.

**Union Type Awareness**: Avro's union types are common for optional fields and versioning, so the provider must handle them properly for PII analysis.

### Migration from Current Implementation

The current AvroPersonalMetadataProvider directly calls `record.getSchema()`. This will be refactored to:
1. Use the new LocalAvroSchemaProvider for schema discovery
2. Accept Schema objects in the `forSchema()` method
3. Maintain backward compatibility through deprecated `forType()` method

## 8. Dependencies & External Integrations

### Infrastructure Dependencies
- **INF-001**: pi2schema schema-spi - Core SchemaProvider interface
- **INF-002**: Apache Avro - Schema and record interfaces

### External Systems
- **None**: This provider operates locally without external system dependencies

### Technology Platform Dependencies
- **PLT-001**: Java 17+ - Platform requirement
- **PLT-002**: Apache Avro 1.8+ - Core dependency for Avro support

## 9. Examples & Edge Cases

### Basic Usage Example
```java
// Create provider
LocalAvroSchemaProvider provider = new LocalAvroSchemaProvider();

// Extract schema from SpecificRecord (schema ID supplier is ignored)
User user = User.newBuilder()
    .setUserId("user-123")
    .setEmail("user@example.com")
    .build();

Schema schema = provider.schemaFor(user, null); // Schema ID supplier ignored

// Use with PersonalMetadataProvider
AvroPersonalMetadataProvider<User> metadataProvider = 
    new AvroPersonalMetadataProvider<>();
PersonalMetadata<User> metadata = metadataProvider.forSchema(schema);
```

### GenericRecord Example
```java
// GenericRecord scenario
GenericRecord genericUser = new GenericData.Record(userSchema);
genericUser.put("userId", "user-123");
genericUser.put("email", "user@example.com");

Schema schema = provider.schemaFor(genericUser, null); // Schema ID supplier ignored
```

### Compatibility Example
```java
// Demonstrates that schema ID supplier is ignored
Supplier<Optional<Integer>> schemaIdSupplier = () -> Optional.of(123);
Schema schema1 = provider.schemaFor(record, schemaIdSupplier);
Schema schema2 = provider.schemaFor(record, null);
// Both calls return the same schema from getSchema()
assert schema1 == schema2;
```

### Edge Cases
- **Union Types**: Provider handles nullable fields (union with null)
- **Nested Records**: Provider returns complete Schema including nested record types
- **Array and Map Fields**: Provider includes collection type schemas
- **Schema Evolution**: Provider handles backward compatible schema changes
- **Logical Types**: Provider preserves logical type information (timestamps, decimals, etc.)
- **Schema ID Supplied**: Provider ignores schema ID and always uses local extraction

## 10. Validation Criteria

- Provider successfully extracts Schemas from all standard Avro record types
- Schema ID supplier parameter is properly ignored while maintaining interface compatibility
- Union type handling preserves all necessary metadata for PII analysis
- Error handling provides clear guidance for common configuration mistakes
- Performance metrics show acceptable overhead for Schema extraction
- Thread safety is maintained under concurrent access
- Compatibility is maintained with existing Avro PersonalMetadataProvider

## 11. Related Specifications / Further Reading

- [Schema Definition Provider Architecture](spec-schema-definition-provider.md)
- [Avro Personal Metadata Provider](../schema-providers-avro/README.md)
- [Apache Avro Documentation](https://avro.apache.org/docs/current/)
