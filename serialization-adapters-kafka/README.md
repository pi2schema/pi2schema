# Kafka Serialization Adapter

The Kafka serialization adapter provides GDPR-compliant encryption and decryption capabilities for Kafka producers and consumers through interceptors. It supports multiple materials providers for different key management systems.

## Overview

The adapter uses Kafka interceptors to automatically encrypt personal data fields during production and decrypt them during consumption. It integrates with the pi2schema schema providers to identify personal data fields and subject identifiers.

## Supported Materials Providers

### In-Memory Materials Provider (Testing)

For testing and development purposes only.

```properties
pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.InMemoryMaterialsProvider
```

### Vault Materials Provider

Uses HashiCorp Vault's transit encryption engine for GDPR-compliant key management.

```properties
pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.VaultMaterialsProvider
```

## Configuration

### Basic Interceptor Configuration

```properties
# Producer interceptor
interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareProducerInterceptor

# Consumer interceptor  
interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareConsumerInterceptor

# Schema definition provider (choose one based on your schema format)
pi2schema.schema.definition.provider=pi2schema.schema.providers.protobuf.LocalProtobufSchemaProvider
pi2schema.schema.definition.provider=pi2schema.schema.providers.avro.LocalAvroSchemaProvider
pi2schema.schema.definition.provider=pi2schema.serialization.kafka.jsonschema.KafkaJsonSchemaProvider

# Personal metadata provider (choose one based on your schema format)
pi2schema.personal.metadata.provider=pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider
pi2schema.personal.metadata.provider=pi2schema.schema.providers.avro.personaldata.AvroPersonalMetadataProvider
pi2schema.personal.metadata.provider=pi2schema.schema.providers.jsonschema.personaldata.JsonSchemaPersonalMetadataProvider
```

### Vault Materials Provider Configuration

The Vault materials provider uses Apache Kafka's standard ConfigDef mechanism for configuration validation and type conversion, following the same patterns as other Kafka components.

#### Required Configuration

```properties
# Materials provider
pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.VaultMaterialsProvider

# Vault connection
pi2schema.vault.url=https://vault.example.com:8200
pi2schema.vault.token=hvs.CAESIJ...

# Provider type - use "encrypting" for producers, "decrypting" for consumers
pi2schema.vault.provider.type=encrypting
```

#### Optional Configuration

```properties
# Transit engine path (default: "transit")
pi2schema.vault.transit.engine.path=transit

# Key prefix for subject-specific keys (default: "pi2schema")
pi2schema.vault.key.prefix=myapp

# Connection timeout in milliseconds (default: 10000)
pi2schema.vault.connection.timeout.ms=10000

# Request timeout in milliseconds (default: 30000)
pi2schema.vault.request.timeout.ms=30000

# Maximum retry attempts (default: 3)
pi2schema.vault.max.retries=3

# Base retry backoff in milliseconds (default: 100)
pi2schema.vault.retry.backoff.ms=100
```

## Usage Examples

### Producer Configuration

```properties
# Kafka producer settings
bootstrap.servers=localhost:9092
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer

# Pi2schema interceptor
interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareProducerInterceptor

# Schema providers
pi2schema.schema.definition.provider=pi2schema.schema.providers.protobuf.LocalProtobufSchemaProvider
pi2schema.personal.metadata.provider=pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider

# Vault materials provider for encryption
pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.VaultMaterialsProvider
pi2schema.vault.url=https://vault.example.com:8200
pi2schema.vault.token=${VAULT_TOKEN}
pi2schema.vault.provider.type=encrypting
pi2schema.vault.key.prefix=myapp
```

### Consumer Configuration

```properties
# Kafka consumer settings
bootstrap.servers=localhost:9092
group.id=my-consumer-group
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.deserializer=io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer

# Pi2schema interceptor
interceptor.classes=pi2schema.serialization.kafka.KafkaGdprAwareConsumerInterceptor

# Schema providers
pi2schema.schema.definition.provider=pi2schema.schema.providers.protobuf.LocalProtobufSchemaProvider
pi2schema.personal.metadata.provider=pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider

# Vault materials provider for decryption
pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.VaultMaterialsProvider
pi2schema.vault.url=https://vault.example.com:8200
pi2schema.vault.token=${VAULT_TOKEN}
pi2schema.vault.provider.type=decrypting
pi2schema.vault.key.prefix=myapp
```

### Java Configuration Example

```java
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import java.util.Properties;

public class ProducerExample {
    public static void main(String[] args) {
        Properties props = new Properties();
        
        // Kafka settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                  "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                  "io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer");
        
        // Pi2schema interceptor
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, 
                  "pi2schema.serialization.kafka.KafkaGdprAwareProducerInterceptor");
        
        // Schema providers
        props.put("pi2schema.schema.definition.provider", 
                  "pi2schema.schema.providers.protobuf.LocalProtobufSchemaProvider");
        props.put("pi2schema.personal.metadata.provider", 
                  "pi2schema.schema.providers.protobuf.personaldata.ProtobufPersonalMetadataProvider");
        
        // Vault materials provider
        props.put("pi2schema.personal.materials.provider", 
                  "pi2schema.serialization.kafka.materials.VaultMaterialsProvider");
        props.put("pi2schema.vault.url", "https://vault.example.com:8200");
        props.put("pi2schema.vault.token", System.getenv("VAULT_TOKEN"));
        props.put("pi2schema.vault.provider.type", "encrypting");
        props.put("pi2schema.vault.key.prefix", "myapp");
        
        KafkaProducer<String, Object> producer = new KafkaProducer<>(props);
        
        // Use producer normally - personal data will be automatically encrypted
    }
}
```

## Vault Setup

### Prerequisites

1. HashiCorp Vault server running and accessible
2. Transit secrets engine enabled
3. Appropriate authentication token with transit permissions

### Enable Transit Engine

```bash
# Enable transit secrets engine
vault secrets enable transit

# Or enable at custom path
vault secrets enable -path=custom-transit transit
```

### Create Vault Policy

```hcl
# vault-pi2schema-policy.hcl
path "transit/encrypt/pi2schema/subject/*" {
  capabilities = ["create", "update"]
}

path "transit/decrypt/pi2schema/subject/*" {
  capabilities = ["create", "update"]
}

path "transit/keys/pi2schema/subject/*" {
  capabilities = ["create", "read", "update"]
}
```

Apply the policy:

```bash
vault policy write pi2schema-policy vault-pi2schema-policy.hcl
```

### Create Token

```bash
# Create token with the policy
vault token create -policy=pi2schema-policy -ttl=24h
```

## GDPR Compliance

### Subject Isolation

Each subject gets a unique encryption key in Vault following the pattern:
```
{keyPrefix}/subject/{subjectId}
```

For example, with default settings:
- Subject "user-12345" → Key "pi2schema/subject/user-12345"
- Subject "customer-abc" → Key "pi2schema/subject/customer-abc"

### Right to be Forgotten

To implement GDPR right-to-be-forgotten:

1. **Identify subject keys** in Vault:
   ```bash
   vault list transit/keys/ | grep "pi2schema/subject/user-12345"
   ```

2. **Delete subject keys**:
   ```bash
   vault delete transit/keys/pi2schema/subject/user-12345
   ```

3. **Verify deletion**:
   ```bash
   vault read transit/keys/pi2schema/subject/user-12345
   # Should return "No value found"
   ```

Once the subject's keys are deleted from Vault, all previously encrypted data for that subject becomes permanently unrecoverable, satisfying GDPR requirements.

## Security Considerations

### Token Management

- Use short-lived tokens with automatic renewal
- Store tokens securely (environment variables, secret management systems)
- Rotate tokens regularly
- Use Vault's auth methods (AWS IAM, Kubernetes, etc.) instead of static tokens in production

### Network Security

- Always use HTTPS for Vault communication
- Implement proper certificate validation
- Use network segmentation and firewalls
- Consider using Vault Agent for token management

### Monitoring and Auditing

- Enable Vault audit logging
- Monitor encryption/decryption operations
- Set up alerts for authentication failures
- Track key usage patterns

## Configuration Validation

The Vault materials provider follows Apache Kafka's standard ConfigDef patterns for configuration validation:

- **Automatic Type Conversion**: Integer and string values are automatically converted and validated
- **Range Validation**: Timeout and retry values are validated against acceptable ranges
- **Format Validation**: URLs, key prefixes, and provider types are validated for correct format
- **Clear Error Messages**: Configuration errors provide specific details about what's wrong

### Configuration Classes

- **VaultKafkaConfig**: Handles all Kafka-specific configuration using ConfigDef standards
- **VaultMaterialsProvider**: Factory that uses VaultKafkaConfig to create materials providers
- **VaultCryptoConfiguration**: Core Vault configuration used by the crypto providers

## Troubleshooting

### Common Issues

1. **Configuration validation errors**:
   - Check configuration property names match exactly
   - Verify required properties are provided
   - Ensure values are within acceptable ranges (e.g., timeouts between 1ms and 5 minutes)

2. **Authentication failures**:
   - Verify token is valid and not expired
   - Check token has required permissions
   - Ensure Vault URL is correct

3. **Key not found errors**:
   - Verify transit engine is enabled
   - Check key naming pattern matches configuration
   - Ensure subject ID is valid

4. **Connection timeouts**:
   - Increase timeout values in configuration
   - Check network connectivity to Vault
   - Verify Vault server is responsive

### Debug Logging

Enable debug logging to troubleshoot issues:

```properties
# Enable debug logging for pi2schema components
logging.level.pi2schema=DEBUG
logging.level.pi2schema.serialization.kafka.materials.VaultMaterialsProvider=DEBUG
logging.level.pi2schema.crypto.providers.vault=DEBUG
```

## Performance Considerations

### Connection Pooling

The Vault materials provider uses connection pooling by default. Configure pool settings based on your load:

```properties
# Adjust timeouts based on your network and load
pi2schema.vault.connection.timeout.ms=5000
pi2schema.vault.request.timeout.ms=15000
```

### Retry Configuration

Configure retry behavior for resilience:

```properties
# Increase retries for high-latency networks
pi2schema.vault.max.retries=5
pi2schema.vault.retry.backoff.ms=200
```

### Monitoring

Monitor key metrics:
- Encryption/decryption latency
- Vault connection pool usage
- Error rates and retry attempts
- Key creation frequency

## Examples

See the [examples](../examples/) directory for complete working examples:

- [Spring Boot with Protobuf](../examples/springboot-protobuf-kafkakms/)
- [Spring Boot with Avro](../examples/springboot-avro-kafkakms/)
- [Spring Boot with JSON Schema](../examples/springboot-jsonschema-kafkakms/)