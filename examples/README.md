# Pi2Schema Examples

This directory contains example Spring Boot applications demonstrating GDPR-compliant data processing using pi2schema with different serialization formats and HashiCorp Vault for key management.

## Available Examples

### 1. [Protobuf Example](springboot-protobuf-kafkakms/)
Demonstrates pi2schema with Protocol Buffers serialization.
- **Key Prefix**: `pi2schema-example`
- **Schema Format**: Protocol Buffers
- **Features**: Subject identification, personal data encryption

### 2. [Avro Example](springboot-avro-kafkakms/)
Demonstrates pi2schema with Apache Avro serialization.
- **Key Prefix**: `pi2schema-avro-example`
- **Schema Format**: Apache Avro
- **Features**: Subject identification, personal data encryption

### 3. [JSON Schema Example](springboot-jsonschema-kafkakms/)
Demonstrates pi2schema with JSON Schema serialization.
- **Key Prefix**: `pi2schema-jsonschema-example`
- **Schema Format**: JSON Schema
- **Features**: Subject identification, personal data encryption

## Key Management with HashiCorp Vault

All examples now use **HashiCorp Vault** as the key management system for production-ready GDPR compliance:

**📋 Setup Guides:**
- [Podman Setup Guide](PODMAN_SETUP.md) - Podman installation and configuration (recommended)
- [Docker Compatibility](DOCKER_COMPATIBILITY.md) - Using Docker instead of Podman
- [Vault Setup Guide](VAULT_SETUP.md) - Manual Vault setup and production deployment

- ✅ **Subject Isolation**: Each subject gets a unique encryption key
- ✅ **GDPR Compliance**: Right-to-be-forgotten through selective key deletion
- ✅ **Production Ready**: Industry-standard Vault for key management
- ✅ **Automatic Key Creation**: Keys are created automatically when needed

## Quick Start

### 1. Prerequisites

- Java 17+
- Podman and podman-compose

### 2. Start Infrastructure

Start Kafka, Schema Registry, Vault, and all services:
```bash
podman-compose -f examples/docker-compose.yaml up -d
```

This will automatically:
- Start Vault in development mode
- Configure the transit secrets engine
- Create necessary policies
- Test the setup

### 3. Run Examples

Each example has two components:
- **Registration Service** (Producer): Encrypts personal data
- **Newsletter Service** (Consumer): Decrypts personal data

#### Protobuf Example
```bash
# Producer (registration service)
./gradlew examples:springboot-protobuf-kafkakms:bootRun --args='--spring.profiles.active=registration'

# Consumer (newsletter service) - in another terminal
./gradlew examples:springboot-protobuf-kafkakms:bootRun --args='--spring.profiles.active=newsletter --server.port=8180'
```

#### Avro Example
```bash
# Producer
./gradlew examples:springboot-avro-kafkakms:bootRun --args='--spring.profiles.active=registration'

# Consumer - in another terminal
./gradlew examples:springboot-avro-kafkakms:bootRun --args='--spring.profiles.active=newsletter --server.port=8180'
```

#### JSON Schema Example
```bash
# Producer
./gradlew examples:springboot-jsonschema-kafkakms:bootRun --args='--spring.profiles.active=registration'

# Consumer - in another terminal
./gradlew examples:springboot-jsonschema-kafkakms:bootRun --args='--spring.profiles.active=newsletter --server.port=8180'
```

### 5. Test the Examples

Register a farmer with personal data:
```bash
curl -X POST http://localhost:8080/api/v1/farmers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@example.com", 
    "phone": "555-1234"
  }'
```

## Configuration

### Vault Configuration

All examples use the following Vault configuration pattern:

```properties
# Materials provider
spring.kafka.properties.pi2schema.personal.materials.provider=pi2schema.serialization.kafka.materials.VaultMaterialsProvider

# Vault connection with defaults to the docker compose values
spring.kafka.properties.pi2schema.vault.url=${VAULT_URL:https://localhost:8200}
spring.kafka.properties.pi2schema.vault.token=${VAULT_TOKEN:myroot}

# Provider type (encrypting for producers, decrypting for consumers)
spring.kafka.properties.pi2schema.vault.provider.type=encrypting

# Optional configuration with defaults
spring.kafka.properties.pi2schema.vault.transit.engine.path=transit
spring.kafka.properties.pi2schema.vault.key.prefix=pi2schema-example
spring.kafka.properties.pi2schema.vault.connection.timeout.ms=10000
spring.kafka.properties.pi2schema.vault.request.timeout.ms=30000
spring.kafka.properties.pi2schema.vault.max.retries=3
spring.kafka.properties.pi2schema.vault.retry.backoff.ms=100
```


## GDPR Compliance

### Subject Isolation

Each subject (user) gets a unique encryption key in Vault:
- Subject "user-123" → Key "pi2schema-example/subject/user-123"
- Subject "user-456" → Key "pi2schema-avro-example/subject/user-456"

### Right to be Forgotten

To implement GDPR right-to-be-forgotten for a subject:

1. **Find the subject's key**:
   ```bash
   vault list transit/keys/ | grep "subject/user-123"
   ```

2. **Delete the key**:
   ```bash
   vault delete transit/keys/pi2schema-example/subject/user-123
   ```

3. **Verify deletion**:
   ```bash
   vault read transit/keys/pi2schema-example/subject/user-123
   # Should return "No value found"
   ```

Once deleted, all encrypted data for that subject becomes permanently unrecoverable, satisfying GDPR requirements.

## Architecture

### Data Flow

1. **Producer** (Registration Service):
   - Receives farmer registration via REST API
   - Identifies subject (farmer UUID) and personal data (contact info)
   - Uses VaultEncryptingMaterialsProvider to encrypt personal data
   - Publishes encrypted event to Kafka

2. **Consumer** (Newsletter Service):
   - Consumes encrypted events from Kafka
   - Uses VaultDecryptingMaterialsProvider to decrypt personal data
   - Processes decrypted data for newsletter sending

### Key Components

- **Schema Providers**: Extract subject identifiers and personal data metadata
- **Vault Materials Provider**: Manages encryption/decryption keys via Vault
- **Kafka Interceptors**: Automatically encrypt/decrypt during serialization
- **Vault Transit Engine**: Provides subject-specific encryption keys

## Troubleshooting

### Common Issues

1. **Vault connection errors**:
   - Check `VAULT_URL` and `VAULT_TOKEN` environment variables
   - Verify Vault is running and accessible
   - Check network connectivity

2. **Authentication failures**:
   - Verify Vault token is valid and not expired
   - Check token has required permissions for transit operations

3. **Key not found errors**:
   - Ensure transit secrets engine is enabled: `vault secrets enable transit`
   - Check subject ID is correctly identified in the schema

4. **Serialization errors**:
   - Verify schema registry is running and accessible
   - Check schema definitions include proper pi2schema annotations

### Debug Logging

Enable debug logging:
```properties
logging.level.pi2schema=DEBUG
logging.level.pi2schema.crypto.providers.vault=DEBUG
logging.level.pi2schema.serialization.kafka.materials=DEBUG
```

## Security Considerations

### Development vs Production

- **Development**: Uses Vault dev mode with static token
- **Production**: Use proper Vault deployment with:
  - TLS encryption
  - Proper authentication (AWS IAM, Kubernetes, etc.)
  - Audit logging
  - High availability setup

### Best Practices

1. **Never use dev mode in production**
2. **Use environment variables for sensitive configuration**
3. **Rotate Vault tokens regularly**
4. **Monitor Vault operations and audit logs**
5. **Use network segmentation and firewalls**
6. **Implement proper backup and disaster recovery**

## Next Steps

1. **Production Deployment**: Follow [Vault Setup Guide](VAULT_SETUP.md) for production configuration
2. **Custom Schema**: Adapt examples to your own data models and schemas
3. **Integration**: Integrate pi2schema into your existing Kafka applications
4. **Monitoring**: Set up monitoring and alerting for Vault operations
5. **Compliance**: Implement data governance processes around key management

## Resources

- [Vault Setup Guide](VAULT_SETUP.md) - Comprehensive Vault configuration
- [Pi2Schema Documentation](../README.md) - Main project documentation
- [Kafka Adapter Documentation](../serialization-adapters-kafka/README.md) - Kafka integration details
- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs) - Official Vault docs