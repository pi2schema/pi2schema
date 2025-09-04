# Sample application - JSON Schema Support

This example demonstrates how to use pi2schema with JSON Schema for Personal Identifiable Information (PII) data protection in a Spring Boot application with Apache Kafka.

## Overview

This sample application shows how to integrate pi2schema with JSON Schema to automatically encrypt/decrypt PII data in Kafka messages. It demonstrates the same farmer registration use case as the Avro and Protobuf examples, but using JSON Schema for data serialization.

## Components

The project contains two sub-components within the same codebase, toggled by Spring profiles for simplicity:

* **Onboarding**: REST API that publishes FarmerRegistered events (profiles: `registration`)
* **Newsletter**: Listens to farmer registered events and sends welcome messages (profiles: `newsletter`)

## Adding pi2schema

### Gradle Dependencies

The JSON Schema support is provided by adding the pi2schema JSON Schema dependencies:

```groovy
dependencies {
    implementation(project(":schema-providers-jsonschema"))
    implementation(project(":serialization-adapters-kafka"))
    
    implementation libs.confluent.kafka.json.schema.serializer
    implementation libs.bundles.jackson
    // ... other dependencies
}
```

### Schema Definition - PII Metadata Annotation

> :warning: **Draft API**: The following definitions may change until version 1.0
> :ear: **Feedback Welcome**: Please provide feedback [here](https://github.com/pi2schema/pi2schema/issues/new)

The JSON Schema must define the Subject Identifier and personal data fields using pi2schema custom extensions.

#### Subject Identifier

To identify the Subject Identifier, add the `pi2schema-subject-identifier: true` extension to the field:

```json
{
  "uuid": {
    "type": "string",
    "pi2schema-subject-identifier": true
  }
}
```

#### Personal Data Fields

For defining personal data fields, add the `pi2schema-personal-data: true` extension:

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

#### Complete JSON Schema Example

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://acme.com/schemas/farmer-registered-event.json",
  "title": "FarmerRegisteredEvent",
  "type": "object",
  "properties": {
    "uuid": {
      "type": "string",
      "description": "Unique identifier for the farmer",
      "pi2schema-subject-identifier": true
    },
    "name": {
      "type": "string",
      "description": "Farmer's full name"
    },
    "phone": {
      "type": "string",
      "description": "Farmer's phone number",
      "pi2schema-personal-data": true
    },
    "email": {
      "type": "string",
      "format": "email", 
      "description": "Farmer's email address",
      "pi2schema-personal-data": true
    }
  },
  "required": ["uuid", "name", "phone", "email"],
  "additionalProperties": false
}
```

### Key Management

> :warning: **Not safe for production**: Please consider integrating a third-party KMS such as AWS KMS, GCP KMS, or HashiCorp Vault for production use.

The key management system uses a simple JCE-based AES-256 local encryptor and decryptor by default. The secret key is stored in a Kafka topic for durability.

### Configuration

The application uses Confluent's JSON Schema serializers with pi2schema interceptors:

```properties
# Producer configuration
spring.kafka.producer.value-serializer=io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer
spring.kafka.producer.properties.interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareJsonSchemaProducerInterceptor

# Consumer configuration  
spring.kafka.consumer.value-deserializer=io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
spring.kafka.consumer.properties.interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareJsonSchemaConsumerInterceptor

# Schema Registry configuration
spring.kafka.properties.schema.registry.url=http://localhost:8081/
spring.kafka.properties.json.value.type=com.acme.model.FarmerRegisteredEvent
```

## Running the Services

### Prerequisites

1. Start Kafka and Schema Registry:
   ```shell
   docker-compose -f ../docker-compose.yaml up -d
   ```

2. Build the project:
   ```shell
   ./gradlew build
   ```

### Producer (Registration Service)

Start the registration service that exposes the REST API:

```shell
./gradlew examples:springboot-jsonschema-kafkakms:bootRun --args='--spring.profiles.active=registration'
```

### Consumer (Newsletter Service)

Start the newsletter service that listens to farmer registration events:

```shell
./gradlew examples:springboot-jsonschema-kafkakms:bootRun --args='--spring.profiles.active=newsletter --server.port=8180'
```

## Testing the Application

### Register the JSON Schema

Before sending any messages, you need to register the JSON Schema with the Schema Registry. This step is required for the Confluent JSON Schema serializer to work properly.

```bash
# One-liner using jq to read and escape the schema file
curl -X POST http://localhost:8081/subjects/farmer-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d "{\"schema\": $(cat examples/springboot-jsonschema-kafkakms/src/main/resources/jsonschema/farmer-registered-event.json | jq -c . | jq -R .), \"schemaType\": \"JSON\"}"
```

### Verify Schema Registration

You can verify the schema was registered correctly:

```bash
# List all subjects
curl -X GET http://localhost:8081/subjects

# Get the latest version of the farmer schema
curl -X GET http://localhost:8081/subjects/farmer-value/versions/latest
```

### Register a Farmer

Now you can simulate a farmer registration with personal data:

```bash
curl -X POST http://localhost:8080/api/v1/farmers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "1-555-123-4567"
  }'
```

### Verify PII Encryption

You can verify that PII data is encrypted by:

1. Checking the Kafka topic directly - personal data fields should be encrypted
2. Observing the newsletter service logs - personal data should be decrypted automatically

### Expected Behavior

- **Producer**: The email and phone fields are automatically encrypted before being sent to Kafka
- **Consumer**: The encrypted fields are automatically decrypted when consumed by the newsletter service
- **Logs**: You should see decrypted personal data in the newsletter service logs

## JSON Schema vs Avro/Protobuf

This JSON Schema implementation provides the same PII protection capabilities as the Avro and Protobuf examples:

- **Subject Identification**: Uses `pi2schema-subject-identifier` extension
- **PII Field Marking**: Uses `pi2schema-personal-data` extension  
- **Encryption Format**: Compatible with the same encrypted data format
- **Key Management**: Uses the same KMS integration
- **Performance**: Similar encryption/decryption performance

## Security Considerations

- The current implementation uses local key storage for demonstration purposes
- For production use, integrate with enterprise key management systems
- Ensure proper access controls for Schema Registry and Kafka topics
- Consider key rotation policies and compliance requirements
