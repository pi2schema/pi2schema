---
title: Local Protobuf Schema Definition Provider Implementation
version: 1.0
date_created: 2025-08-08
last_updated: 2025-08-13
owner: pi2schema
tags: [schema, protobuf, provider, spi, implementation, local]
---

# Local Protobuf Schema Definition Provider Implementation

A specification for implementing a local-only Protobuf schema definition provider within the pi2schema framework, focusing on extracting Protobuf Descriptor information from Message objects using only local reflection capabilities while maintaining compatibility with existing implementations.

## 1. Purpose & Scope

This specification defines the requirements for implementing a local-only Protobuf schema provider that extracts Descriptor information from Protobuf Message objects using the built-in `getDescriptorForType()` method. The provider maintains backward compatibility with existing protobuf implementations by operating without external dependencies like Schema Registry.

**Intended Audience**: Java developers implementing Protobuf schema providers for the pi2schema framework.

**Assumptions**: 
- Familiarity with Google Protocol Buffers and Descriptor API
- Understanding of Protobuf Message interface and reflection capabilities
- Knowledge of the pi2schema SPI architecture
- Preference for local-only operation without external system dependencies

## 2. Definitions

- **Protobuf Descriptor**: Metadata object describing the structure of a Protobuf message type
- **Message**: Google Protobuf base interface for all generated message classes
- **Field Descriptor**: Metadata describing individual fields within a Protobuf message
- **OneOf Descriptor**: Metadata describing oneOf field groups in Protobuf messages

## 3. Requirements, Constraints & Guidelines

### Core Requirements

- **REQ-001**: The provider SHALL implement `SchemaProvider<Descriptor>` interface
- **REQ-002**: The provider SHALL extract Descriptor from Protobuf Message objects using `getDescriptorForType()`
- **REQ-003**: The provider SHALL operate in local-only mode without external dependencies
- **REQ-004**: The provider SHALL ignore schema ID supplier parameters to maintain local behavior
- **REQ-005**: The provider SHALL handle unknown message types gracefully
- **REQ-006**: The provider SHALL be thread-safe for concurrent access

### Schema Discovery Requirements

- **REQ-008**: The provider SHALL use `message.getDescriptorForType()` for all schema discovery
- **REQ-009**: The provider SHALL validate that business objects are Protobuf Message instances
- **REQ-010**: The provider SHALL maintain compatibility with existing protobuf implementations
- **REQ-011**: The provider SHALL not require external schema registry configuration

### Performance Requirements

- **PER-001**: Descriptor extraction SHALL be optimized for runtime performance
- **PER-002**: Provider initialization SHALL be lightweight and fast

### Compatibility Constraints

- **CON-001**: The provider SHALL be compatible with Google Protobuf 3.x and later
- **CON-002**: The provider SHALL maintain compatibility with existing Protobuf serializers
- **CON-003**: The provider SHALL support generated and dynamic Protobuf messages
- **CON-004**: The provider SHALL work without Schema Registry dependencies

### Error Handling Guidelines

- **GUD-001**: Throw `SchemaNotFoundException` for non-Protobuf business objects
- **GUD-002**: Provide clear error messages for invalid message types
- **GUD-003**: Handle null business objects gracefully
- **GUD-004**: Log appropriate debugging information for troubleshooting

## 4. Interfaces & Data Contracts

### Core Interface Implementation

```java
package pi2schema.schema.providers.protobuf;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import pi2schema.schema.SchemaProvider;
import pi2schema.schema.SchemaNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Local Protobuf schema provider that extracts Descriptor information
 * from Protobuf Message objects using only local reflection capabilities.
 * This provider maintains compatibility with existing protobuf implementations
 * by avoiding Schema Registry dependencies.
 */
public class LocalProtobufSchemaProvider implements SchemaProvider<Descriptor> {
    
    @Override
    public Descriptor schemaFor(Object businessObject, Supplier<Optional<Integer>> schemaIdSupplier) {
        // Local-only implementation - schema ID supplier is ignored
        return extractDescriptorFromMessage(businessObject);
    }
    
    private Descriptor extractDescriptorFromMessage(Object businessObject) {
        if (businessObject == null) {
            throw new SchemaNotFoundException("Business object cannot be null");
        }
        
        if (!(businessObject instanceof Message)) {
            throw new SchemaNotFoundException(
                "Object is not a Protobuf Message: " + businessObject.getClass().getName()
            );
        }
        
        Message message = (Message) businessObject;
        return message.getDescriptorForType();
    }
}
```

### Data Contracts

#### Input Requirements
- Business object MUST be an instance of `com.google.protobuf.Message`
- Business object MUST NOT be null
- Schema ID supplier parameter is ignored (local-only operation)

#### Output Requirements
- Returns `com.google.protobuf.Descriptors.Descriptor` instance
- Descriptor MUST contain complete field metadata
- Descriptor MUST be suitable for PII analysis by PersonalMetadataProvider

## 5. Acceptance Criteria

- **AC-001**: Given a Protobuf Message object, When schemaFor() is called, Then it returns the correct Descriptor from getDescriptorForType()
- **AC-002**: Given a schema ID supplier parameter, When schemaFor() is called, Then it ignores the schema ID and uses local extraction
- **AC-003**: Given a non-Protobuf object, When schemaFor() is called, Then it throws SchemaNotFoundException with clear error message
- **AC-004**: Given a null business object, When schemaFor() is called, Then it throws SchemaNotFoundException gracefully
- **AC-005**: Given a ProtobufPersonalMetadataProvider, When it receives a Descriptor from this provider, Then it successfully analyzes PII fields

## 6. Test Automation Strategy

### Unit Testing
- Test Descriptor extraction from various Protobuf Message types
- Test error handling for invalid business objects
- Test null object handling

### Integration Testing
- Test with real Protobuf generated classes
- Test compatibility with PersonalMetadataProvider implementations
- Test thread safety under concurrent access

### Performance Testing
- Benchmark Descriptor extraction performance
- Monitor memory usage for Descriptor objects

## 7. Rationale & Context

### Design Decisions

**Local-Only Operation**: Maintaining compatibility with existing protobuf implementations by avoiding external dependencies like Schema Registry, keeping the provider simple and focused.

**Direct Message Interface Usage**: Using the standard Protobuf Message interface ensures compatibility with all generated Protobuf classes and provides access to the standard `getDescriptorForType()` method.

**Ignoring Schema ID Supplier**: To maintain backward compatibility, the provider accepts the schema ID supplier parameter but ignores it, always using local descriptor extraction.

**Error Handling**: Providing specific exceptions and error messages helps developers quickly identify and resolve configuration issues.

## 8. Dependencies & External Integrations

### Infrastructure Dependencies
- **INF-001**: pi2schema schema-spi - Core SchemaProvider interface
- **INF-002**: Google Protocol Buffers - Message interface and Descriptor API

### External Systems
- **None**: This provider operates locally without external system dependencies

### Technology Platform Dependencies
- **PLT-001**: Java 17+ - Platform requirement
- **PLT-002**: Google Protobuf 3.x+ - Core dependency for Protobuf support

## 9. Examples & Edge Cases

### Basic Usage Example
```java
// Create provider
LocalProtobufSchemaProvider provider = new LocalProtobufSchemaProvider();

// Extract descriptor from business object (schema ID supplier is ignored)
MyProtobufMessage message = MyProtobufMessage.newBuilder()
    .setUserId("user-123")
    .setEmail("user@example.com")
    .build();

Descriptor schema = provider.schemaFor(message, null); // Schema ID supplier ignored

// Use with PersonalMetadataProvider
ProtobufPersonalMetadataProvider<MyProtobufMessage> metadataProvider = 
    new ProtobufPersonalMetadataProvider<>();
PersonalMetadata<MyProtobufMessage> metadata = metadataProvider.forSchema(schema);
```

### Compatibility Example
```java
// Demonstrates that schema ID supplier is ignored
Supplier<Optional<Integer>> schemaIdSupplier = () -> Optional.of(123);
Descriptor schema1 = provider.schemaFor(message, schemaIdSupplier);
Descriptor schema2 = provider.schemaFor(message, null);
// Both calls return the same descriptor from getDescriptorForType()
assert schema1 == schema2;
```

### Edge Cases
- **Dynamic Messages**: Provider handles dynamically created Protobuf messages
- **Empty Messages**: Provider handles messages with no fields gracefully
- **Deprecated Fields**: Provider includes deprecated fields in the Descriptor
- **Nested Messages**: Provider returns complete Descriptor including nested types
- **Schema ID Supplied**: Provider ignores schema ID and always uses local extraction

## 10. Validation Criteria

- Provider successfully extracts Descriptors from all standard Protobuf Message types
- Schema ID supplier parameter is properly ignored while maintaining interface compatibility
- Error handling provides clear guidance for common configuration mistakes
- Performance metrics show acceptable overhead for Descriptor extraction
- Thread safety is maintained under concurrent access
- Compatibility is maintained with existing Protobuf PersonalMetadataProvider

## 11. Related Specifications / Further Reading

- [Schema Definition Provider Architecture](spec-schema-definition-provider.md)
- [Protobuf Personal Metadata Provider](../schema-providers-protobuf/README.md)
- [Google Protocol Buffers Documentation](https://developers.google.com/protocol-buffers)
