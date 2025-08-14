---
title: JSON Schema Personal Metadata Provider Implementation for PII Data Handling
version: 2.0
date_created: 2025-07-20
last_updated: 2025-08-08
owner: pi2schema
tags: [schema, json-schema, pii, encryption, metadata, provider]
---

# JSON Schema Personal Metadata Provider Implementation for PII Data Handling

A specification for implementing JSON Schema personal metadata analysis within the pi2schema framework, enabling PII data identification and encryption capabilities by analyzing provided JSON Schema definitions. This specification focuses on PII metadata extraction and is designed to work with separate Schema Definition Providers.

## 1. Purpose & Scope

This specification defines the requirements for implementing a JSON Schema personal metadata provider that analyzes JSON Schema definitions to identify PII fields and provide encryption/decryption capabilities. The implementation must work with schemas provided by JsonSchemaProvider implementations and maintain compatibility with the existing SPI interfaces.

**Intended Audience**: Java developers implementing personal metadata providers for the pi2schema framework.

**Assumptions**: 
- Familiarity with JSON Schema Draft 7 or later
- Understanding of the pi2schema SPI architecture and separation between schema discovery and metadata analysis
- Knowledge of Java cryptographic operations
- Understanding of Kafka serialization patterns

## Terminology

The following terms are used throughout this specification:

- **JSON Schema**: A vocabulary that allows you to annotate and validate JSON documents
- **pi2schema-personal-data**: Custom extension to mark fields containing personal/sensitive data
- **pi2schema-subject-identifier**: Custom extension to identify the subject of personal data
- **PersonalDataFieldDefinition**: Core interface for defining personal data fields in schemas

## Scope and Limitations

**Current Implementation Scope:**
- Top-level field encryption only
- Simple field-level PII encryption using `PersonalDataFieldDefinition`
- Direct annotation-based PII marking

**Explicitly Unsupported Features:**
- oneOf/anyOf patterns for encrypted/plaintext field variants
- Union types
- Nested object field encryption
- Array element encryption
- Complex schema validation patterns

## 3. Requirements, Constraints & Guidelines

### Core Requirements

- **REQ-001**: The provider SHALL implement `PersonalMetadataProvider<T>` interface with `forSchema(JsonNode)` method
- **REQ-002**: The provider SHALL implement `PersonalDataFieldDefinition<T>` interface  
- **REQ-003**: The provider SHALL implement `SubjectIdentifierFinder<T>` interface
- **REQ-004**: The provider SHALL implement `SubjectIdentifierFieldDefinition<T>` interface
- **REQ-005**: The provider SHALL accept JSON Schema definitions as input parameter
- **REQ-006**: The provider SHALL identify PII fields through custom JSON Schema extensions
- **REQ-007**: The provider SHALL identify subject identifier fields through custom JSON Schema extensions
- **REQ-008**: The provider SHALL support encryption/decryption operations on identified PII fields
- **REQ-009**: The provider SHALL maintain compatibility with Kafka serialization adapters
- **REQ-010**: The provider SHALL work with schemas provided by JsonSchemaProvider implementations
- **REQ-011**: The provider SHALL support any business object type that corresponds to the provided schema

### Security Requirements

- **SEC-001**: PII data SHALL be encrypted before serialization
- **SEC-002**: Subject identifiers SHALL be preserved in plaintext for key derivation
- **SEC-003**: Encrypted data SHALL include initialization vectors and transformation metadata
- **SEC-004**: The provider SHALL use the existing crypto SPI for all cryptographic operations

### Performance Requirements

- **PER-001**: Schema analysis SHALL be optimized for runtime performance
- **PER-002**: Field lookups SHALL be optimized for runtime performance
- **PER-003**: Memory allocation during encryption/decryption SHALL be minimized

### Compatibility Constraints

- **CON-001**: The implementation SHALL NOT break existing Avro/Protobuf functionality
- **CON-002**: The implementation SHALL use the same encrypted data format as other providers
- **CON-003**: The implementation SHALL follow the same error handling patterns as existing providers
- **CON-004**: The implementation SHALL be compatible with Java 17+

### Design Guidelines

- **GUD-001**: Focus on PII metadata analysis rather than schema discovery
- **GUD-002**: Follow the existing package structure pattern: `pi2schema.schema.providers.jsonschema.personaldata`
- **GUD-003**: Implement caching for parsed schema metadata
- **GUD-004**: Use Jackson for business object to JSON conversion and field access
- **GUD-005**: Provide clear error messages for schema parsing failures
- **GUD-006**: Accept JsonNode schema definitions as primary input method

### Implementation Patterns

- **PAT-001**: Use direct annotation with `pi2schema-personal-data` extension to mark PII fields
- **PAT-002**: Follow the sibling subject identifier finder pattern from Avro implementation
- **PAT-003**: Implement deep copying for business object mutation during encryption/decryption
- **PAT-004**: Use Jackson ObjectMapper for business object to JSON conversion and field access
- **PAT-005**: Implement `forSchema(JsonNode schema)` as the primary method for metadata creation

## 4. Interfaces & Data Contracts

### JSON Schema Extensions

The provider SHALL use custom JSON Schema extensions to identify PII and subject identifier fields:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "userId": {
      "type": "string",
      "pi2schema-subject-identifier": true
    },
    "email": {
      "type": "string",
      "format": "email",
      "pi2schema-personal-data": true
    },
    "name": {
      "type": "string"
    }
  }
}
```

**Note**: This simplified implementation supports only direct field annotation. Complex patterns like oneOf/anyOf for encrypted/plaintext variants are not supported.
```

### SPI Interface Implementations

#### Enhanced PersonalMetadataProvider Implementation
```java
public class JsonSchemaPersonalMetadataProvider<T> implements PersonalMetadataProvider<T> {
    /**
     * Creates PersonalMetadata for a given JSON Schema definition.
     * This is the primary method for JSON Schema-based PII analysis.
     * @param schema The JSON Schema definition to analyze for PII fields
     * @return PersonalMetadata containing identified PII field definitions
     */
    PersonalMetadata<T> forSchema(JsonNode schema);
}
```

#### PersonalDataFieldDefinition Implementation
```java
public class JsonSchemaPersonalDataFieldDefinition<T> implements PersonalDataFieldDefinition<T> {
    CompletableFuture<Void> swapToEncrypted(Encryptor encryptor, T buildingInstance);
    CompletableFuture<Void> swapToDecrypted(Decryptor decryptor, T decryptingInstance);
    ByteBuffer valueFrom(T instance);
}
```

### Data Structure Contracts

#### Input Object Format
- SHALL accept any business object type as input
- SHALL support conversion to/from JSON representation internally
- SHALL support simple field access (top-level fields only in initial version)
- SHALL preserve non-PII fields unchanged
- **Note**: While internally JSON objects may be represented as `Map<String, Object>`, the provider accepts any business object type

#### Encrypted Data Format
- SHALL use the same `EncryptedPersonalData` structure as Avro/Protobuf providers
- SHALL encode binary data as Base64 strings
- SHALL maintain field mapping through `personalDataFieldNumber`

## 5. Acceptance Criteria

- **AC-001**: Given a JSON object with PII fields, When processed by the provider, Then PII fields are correctly identified and marked for encryption
- **AC-002**: Given a JSON object with a subject identifier field, When processed by the provider, Then the subject identifier is correctly extracted
- **AC-003**: Given a JSON object with PII data, When encryption is performed, Then the PII field is replaced with an EncryptedPersonalData structure
- **AC-004**: Given a JSON object with encrypted PII data, When decryption is performed, Then the original plaintext value is restored
- **AC-005**: Given multiple PII fields in a single object, When processed, Then all PII fields are handled independently
- **AC-006**: Given a JSON Schema without PII annotations, When processed, Then no encryption operations are performed
- **AC-007**: Given malformed encrypted data, When decryption is attempted, Then appropriate exceptions are thrown
- **AC-008**: Given a missing subject identifier, When PII processing is attempted, Then a SubjectIdentifierNotFoundException is thrown
- **AC-009**: Given multiple subject identifiers, When PII processing is attempted, Then a TooManySubjectIdentifiersException is thrown

## 6. Test Automation Strategy

### Test Levels
- **Unit Tests**: Test individual components (field finders, data transformers, schema parsers)
- **Integration Tests**: Test end-to-end encryption/decryption workflows
- **Performance Tests**: Validate caching and runtime performance requirements

### Testing Frameworks
- **JUnit 5**: Primary testing framework
- **AssertJ**: Fluent assertions for better readability
- **Mockito**: Mocking framework for isolated unit tests
- **Testcontainers**: Integration testing with Kafka

### Test Data Management
- Use JSON Schema fixtures for various field configurations
- Create test objects with different PII field patterns
- Include edge cases: empty objects, null values, nested structures

### CI/CD Integration
- Tests SHALL be executed on every pull request
- Code coverage SHALL meet minimum 80% threshold
- Integration tests SHALL run against multiple JDK versions

### Coverage Requirements
- Minimum 90% line coverage for core provider classes
- 100% coverage for error handling paths
- Performance tests for schema parsing and field lookup operations

## 7. Rationale & Context

### Design Decisions

**Separation of Schema Discovery and PII Analysis**: This architectural separation allows better testability, maintainability, and reusability. Schema definitions can be shared and reused across multiple PII operations, and different schema discovery strategies can be plugged in without affecting PII analysis logic.

**JSON Schema Extensions**: Custom extensions provide explicit metadata without altering the core JSON Schema specification. This approach mirrors the annotation-based strategies used in Avro and Protobuf implementations.

**Direct Field Annotation**: Using direct `pi2schema-personal-data` annotation provides a simple and clear way to mark PII fields without complex schema patterns. This simplified approach focuses on essential functionality.

**Top-level Field Encryption**: The current implementation focuses on top-level field encryption only, providing a solid foundation that can be extended in future versions.

**Business Object Flexibility**: Using any business object type provides maximum flexibility while internally converting to JSON representation for processing. This maintains compatibility with JSON processing libraries and allows for type-safe business object usage.

**forSchema() Primary Method**: Making `forSchema(JsonNode)` the primary method enforces the architectural separation and provides better control over schema caching and validation.

### Migration Strategy

The transition from the current combined approach to separated concerns will involve:
1. Implement `forSchema(JsonNode)` method as the sole method for metadata creation
2. Update adapters to use JsonSchemaProvider + PersonalMetadataProvider pattern
3. Remove any existing `forType()` implementations in favor of the new architecture
4. Update documentation and examples to reflect the clean separation of concerns

### Context for Requirements

The JSON Schema provider fills a critical gap in the pi2schema ecosystem, enabling PII protection for JSON-based data formats commonly used in microservices and web applications. The requirements ensure feature parity with existing providers while accommodating JSON Schema's unique characteristics.

## 8. Dependencies & External Integrations

### Infrastructure Dependencies
- **INF-001**: pi2schema crypto-spi - Cryptographic operations interface
- **INF-002**: pi2schema schema-spi - Core PersonalMetadataProvider interface
- **INF-003**: pi2schema schema-providers-jsonschema - JsonSchemaProvider for schema discovery

### Third-Party Services  
- **SVC-001**: Jackson JSON Library - JSON processing and object mapping capabilities

### Data Dependencies
- **DAT-001**: JSON Schema definitions - Provided by JsonSchemaProvider implementations
- **DAT-002**: Encrypted data format - Compatibility with existing encrypted data structures

### Technology Platform Dependencies
- **PLT-001**: Java 17+ - Runtime platform requirement for compatibility
- **PLT-002**: Jackson 2.x - JSON processing library with wide ecosystem support

### Compliance Dependencies
- **COM-001**: GDPR compliance - PII data protection and subject rights
- **COM-002**: pi2schema encryption standards - Consistent encryption approach across providers

## 9. Examples & Edge Cases

### Basic Usage Example
```java
// Step 1: Schema discovery (handled by JsonSchemaProvider)
JsonSchemaProvider schemaProvider = new JsonSchemaProvider(discoveryStrategies);
JsonNode schema = schemaProvider.schemaFor(businessObject);

// Step 2: PII metadata analysis (handled by this provider)
JsonSchemaPersonalMetadataProvider<UserData> metadataProvider = 
    new JsonSchemaPersonalMetadataProvider<>();
PersonalMetadata<UserData> metadata = metadataProvider.forSchema(schema);

// Step 3: Encryption
UserData userData = new UserData("user-123", "john@example.com");
UserData encryptedData = metadata.swapToEncrypted(encryptor, userData);
```

### Schema-Based Example
```java
// JSON Schema with PII annotations
JsonNode schema = objectMapper.readTree("""
{
  "type": "object",
  "properties": {
    "userId": {
      "type": "string",
      "pi2schema-subject-identifier": true
    },
    "email": {
      "type": "string",
      "format": "email",
      "pi2schema-personal-data": true
    },
    "name": {
      "type": "string"
    }
  }
}
""");

// Direct schema-based usage
JsonSchemaPersonalMetadataProvider<Map<String, Object>> metadataProvider = 
    new JsonSchemaPersonalMetadataProvider<>();
PersonalMetadata<Map<String, Object>> metadata = metadataProvider.forSchema(schema);

// Process dynamic data
Map<String, Object> userData = Map.of(
    "userId", "user-123",
    "email", "john@example.com",
    "name", "John Doe"
);
Map<String, Object> encryptedData = metadata.swapToEncrypted(encryptor, userData);
```

### Adapter Integration Example
```java
// Kafka adapter using separated concerns
public class KafkaGdprAwareProducerInterceptor<K, V> {
    private JsonSchemaProvider schemaProvider;
    private JsonSchemaPersonalMetadataProvider<V> metadataProvider;
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (record.value() == null) return record;
        
        // Schema discovery
        JsonNode schema = schemaProvider.schemaFor(record.value());
        
        // PII analysis
        PersonalMetadata<V> metadata = metadataProvider.forSchema(schema);
        
        // Encryption
        V encryptedValue = metadata.swapToEncrypted(encryptor, record.value());
        
        return new ProducerRecord<>(/*...*/);
    }
}
```

### Edge Cases
- **Null Schema**: Provider should handle null schema gracefully with clear error message
- **Invalid Schema Format**: Provider should validate JSON Schema structure
- **Missing PII Extensions**: Provider should handle schemas without pi2schema extensions
- **Complex Data Types**: Provider should handle arrays, objects, and null values in business objects

## 10. Current Limitations and Future Enhancements

### Supported Features (Current Implementation)

- Top-level field encryption with pi2schema extensions
- Subject identifier discovery using sibling field pattern
- Business object flexibility (POJOs, Maps, etc.)
- Jackson-based JSON conversion and field access

### Unsupported Features (Current Implementation)

The current implementation focuses on simplicity and reliability. The following features are explicitly **not supported**:

#### Nested Object Handling
- Nested field encryption (e.g., `user.profile.email`) is not supported
- Only top-level fields can be marked as personal data
- Complex object hierarchies require flattening to top-level fields

#### oneOf/anyOf Patterns  
- Schema patterns using `oneOf` or `anyOf` for encrypted/plaintext variants are not supported
- Union types combining multiple data types are not supported
- Only direct field annotation with `pi2schema-personal-data` is supported

#### Array and Collection Handling
- Array element encryption is not supported
- Collection-level PII handling is not implemented
- Complex data structure encryption requires custom handling

### Supported Patterns (Current Implementation)

#### Direct Field Annotation
```json
{
  "type": "object", 
  "properties": {
    "email": {
      "type": "string",
      "format": "email", 
      "pi2schema-personal-data": true
    },
    "userId": {
      "type": "string",
      "pi2schema-subject-identifier": true
    },
    "name": {
      "type": "string"
    }
  }
}
```

This simplified approach provides a clear foundation for PII field identification and encryption.

#### Multiple Subject Identifiers (Error Case)
```java
// Should throw TooManySubjectIdentifiersException
{
  "userId": {"type": "string", "pi2schema-subject-identifier": true},
  "customerId": {"type": "string", "pi2schema-subject-identifier": true}
}
```

## 10. Validation Criteria

### Functional Validation
- **VAL-001**: All SPI interfaces are correctly implemented
- **VAL-002**: PII fields are properly identified from JSON Schema annotations
- **VAL-003**: Encryption/decryption operations produce expected results
- **VAL-004**: Subject identifier extraction works correctly
- **VAL-005**: Error handling matches existing provider behavior

### Performance Validation  
- **VAL-006**: Schema parsing performance meets benchmark requirements
- **VAL-007**: Memory usage during processing stays within acceptable limits
- **VAL-008**: Caching mechanisms reduce repeated parsing overhead

### Compatibility Validation
- **VAL-009**: Integration with existing Kafka serialization adapters
- **VAL-010**: Encrypted data format compatibility with other providers
- **VAL-011**: Error message consistency across providers

### Security Validation
- **VAL-012**: PII data is never persisted in plaintext
- **VAL-013**: Cryptographic operations use secure implementations
- **VAL-014**: Key material handling follows security best practices

## 12. Related Specifications / Further Reading

- [Schema Definition Provider Architecture](spec-schema-definition-provider.md)
- [JSON Schema Definition Provider](spec-schema-definition-provider-jsonschema.md)
- [Protobuf Schema Definition Provider](spec-schema-definition-provider-protobuf.md)
- [Avro Schema Definition Provider](spec-schema-definition-provider-avro.md)
- [pi2schema Avro Provider Implementation](../schema-providers-avro/README.md)
- [pi2schema Protobuf Provider Implementation](../schema-providers-protobuf/README.md)
- [JSON Schema Specification](https://json-schema.org/specification.html)
- [pi2schema Crypto SPI Documentation](../crypto-spi/README.md)
- [pi2schema Schema SPI Documentation](../schema-spi/README.md)
- [GDPR Personal Data Protection Guidelines](https://gdpr.eu/what-is-personal-data/)
- [Kafka Serialization Best Practices](https://kafka.apache.org/documentation/#serialization)
