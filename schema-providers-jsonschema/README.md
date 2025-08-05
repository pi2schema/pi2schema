# Pi2schema JSON Schema Support

This module provides JSON Schema support for the pi2schema framework, enabling PII data identification, encryption, and handling capabilities compatible with existing Avro and Protobuf implementations.

## Overview

The JSON Schema provider implements the pi2schema SPI interfaces to handle Personal Identifiable Information (PII) data encryption and decryption for JSON-based data formats. It uses custom JSON Schema extensions to identify PII fields and subject identifiers.

## Features

- **PII Field Detection**: Identifies PII fields through custom JSON Schema extensions
- **Subject Identifier Support**: Extracts subject identifiers for key derivation
- **Encryption/Decryption**: Seamless encryption and decryption of PII data
- **Schema Caching**: Optimized performance through schema analysis caching
- **Compatible Format**: Uses the same encrypted data format as Avro/Protobuf providers

## JSON Schema Extensions

The provider uses custom extensions to annotate JSON Schema fields:

- `pi2schema-subject-identifier`: Marks a field as a subject identifier
- `pi2schema-personal-data`: Marks a field as containing PII data

## Example Usage

### JSON Schema Definition

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
    "phone": {
      "type": "string",
      "pi2schema-personal-data": true
    },
    "name": {
      "type": "string"
    }
  }
}
```

### Java Code

```java
// Create provider
var provider = new JsonSchemaPersonalMetadataProvider();

// Analyze schema
String schemaContent = "..."; // Your JSON Schema
var metadata = provider.forSchema(schemaContent);

// JSON data
Map<String, Object> userData = Map.of(
    "userId", "user-123",
    "email", "john@example.com",
    "name", "John Doe"
);

// Encrypt PII data
var encryptedData = metadata.swapToEncrypted(encryptor, userData);

// Decrypt PII data
var decryptedData = metadata.swapToDecrypted(decryptor, encryptedData);
```

## Architecture

The JSON Schema provider follows the same architectural patterns as existing providers:

- `JsonSchemaPersonalMetadataProvider`: Main entry point implementing `PersonalMetadataProvider`
- `JsonPersonalDataFieldDefinition`: Handles field-level encryption/decryption
- `JsonSiblingSubjectIdentifierFinder`: Locates subject identifier fields
- `JsonSchemaAnalyzer`: Parses and analyzes JSON schemas for PII annotations

## Patterns Supported

## Current Implementation Scope

This implementation focuses on simple, reliable PII field encryption with the following features:

### Direct Field Annotation
Fields are marked directly with the `pi2schema-personal-data` extension:

```json
{
  "email": {
    "type": "string",
    "format": "email",
    "pi2schema-personal-data": true
  },
  "phone": {
    "type": "string", 
    "pi2schema-personal-data": true
  }
}
```

### Limitations

The current implementation has the following explicit limitations:

- **Top-level fields only**: Nested object field encryption is not supported
- **No oneOf/anyOf patterns**: Complex schema patterns for encrypted/plaintext variants are not supported  
- **No array encryption**: Array element encryption is not implemented
- **No union types**: Union type handling is not supported

This simplified approach provides a solid foundation focused on essential PII protection capabilities.

## Error Handling

The provider implements consistent error handling patterns:

- `SubjectIdentifierNotFoundException`: When no subject identifier is found
- `TooManySubjectIdentifiersException`: When multiple subject identifiers are found
- `UnsupportedEncryptedFieldFormatException`: When encrypted data format is invalid
- `JsonSchemaAnalysisException`: When schema analysis fails
- `JsonSchemaValidationException`: When JSON validation fails

## Performance

- **Schema Caching**: Analyzed schemas are cached to avoid repeated parsing
- **Parallel Processing**: Field encryption/decryption operations run in parallel
- **Memory Optimization**: Deep copying minimizes memory allocation during operations

## Dependencies

- Jackson: JSON processing and object mapping
- Everit JSON Schema: JSON Schema validation
- pi2schema crypto-spi: Cryptographic operations
- pi2schema schema-spi: Core schema provider interfaces

## Compatibility

- Compatible with existing Kafka serialization adapters
- Uses the same encrypted data format as Avro/Protobuf providers
- Maintains error handling consistency across providers
- Requires Java 17+

## Limitations

- Requires explicit schema definition (cannot infer schema from JSON objects alone)
- Currently supports string-type PII fields (extensible for other types)
- Array field support is planned for future versions

## Contributing

This implementation follows the pi2schema coding standards and architectural patterns. When contributing:

1. Follow the existing package structure
2. Implement comprehensive tests for new features
3. Maintain compatibility with existing providers
4. Follow Java best practices and coding guidelines
