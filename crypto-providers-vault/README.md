# Crypto Providers - Vault

This module provides a HashiCorp Vault-based implementation of the crypto-spi interfaces for GDPR-compliant encryption key management using Vault's transit encryption engine.

## Overview

The Vault crypto provider implements a two-tier key hierarchy where Vault manages Key Encryption Keys (KEKs) per subject, and these KEKs encrypt Data Encryption Keys (DEKs) that are used for actual data encryption. This approach ensures cryptographic isolation between subjects and enables GDPR right-to-be-forgotten compliance.

### Key Management Architecture

```
Subject Data → DEK (Tink AEAD) → Encrypted with KEK → KEK managed by Vault Transit Engine
```

## Features

- **VaultEncryptingMaterialsProvider**: Generates Data Encryption Keys (DEKs) and encrypts them using Vault's transit encryption engine
- **VaultDecryptingMaterialsProvider**: Decrypts DEKs using Vault's transit encryption for data access
- **Subject-based Key Isolation**: Each subject gets cryptographically separate encryption materials following the pattern `{keyPrefix}/subject/{subjectId}`
- **GDPR Compliance**: Supports right-to-be-forgotten through subject-specific key deletion in Vault
- **Asynchronous Operations**: Non-blocking operations using CompletableFuture for high performance
- **Connection Pooling**: HTTP connection pooling and keep-alive for optimal performance
- **Comprehensive Error Handling**: Specific exceptions for different failure scenarios
- **Retry Logic**: Exponential backoff retry for transient failures
- **Security**: No sensitive data exposure in logs, encryption context validation

## Dependencies

- HashiCorp Vault with transit encryption engine enabled
- Google Tink for cryptographic primitives (AES-GCM)
- Jackson for JSON processing
- SLF4J for logging

## Quick Start

### 1. Configuration

```java
VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
    .vaultUrl("https://vault.example.com:8200")
    .vaultToken(System.getenv("VAULT_TOKEN"))
    .transitEnginePath("transit")
    .keyPrefix("myapp")
    .build();
```

### 2. Encryption

```java
try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
    CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("user-12345");
    EncryptionMaterial material = future.get();
    
    // Encrypt your data
    byte[] encryptedData = material.aead().encrypt(plaintext, null);
    
    // Store these with your encrypted data
    byte[] encryptedDataKey = material.encryptedDataKey();
    String encryptionContext = material.encryptionContext();
}
```

### 3. Decryption

```java
try (VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config)) {
    CompletableFuture<Aead> future = provider.decryptionKeysFor(
        "user-12345", 
        encryptedDataKey, 
        encryptionContext
    );
    Aead aead = future.get();
    
    // Decrypt your data
    byte[] plaintext = aead.decrypt(encryptedData, null);
}
```

## Configuration Options

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `vaultUrl` | Vault server URL | - | Yes |
| `vaultToken` | Authentication token | - | Yes |
| `transitEnginePath` | Path to transit engine | "transit" | No |
| `keyPrefix` | Prefix for subject keys | "pi2schema" | No |
| `connectionTimeout` | HTTP connection timeout | 10 seconds | No |
| `requestTimeout` | Request timeout | 30 seconds | No |
| `maxRetries` | Maximum retry attempts | 3 | No |
| `retryBackoffMs` | Base retry backoff | 100ms | No |

## Examples

The module includes comprehensive examples demonstrating various usage patterns:

- **[BasicUsageExample](src/main/java/pi2schema/crypto/providers/vault/examples/BasicUsageExample.java)**: Simple encryption/decryption workflow
- **[ConfigurationExamples](src/main/java/pi2schema/crypto/providers/vault/examples/ConfigurationExamples.java)**: Different configuration scenarios (dev, prod, microservices, etc.)
- **[GdprComplianceExample](src/main/java/pi2schema/crypto/providers/vault/examples/GdprComplianceExample.java)**: GDPR right-to-be-forgotten implementation
- **[ErrorHandlingExample](src/main/java/pi2schema/crypto/providers/vault/examples/ErrorHandlingExample.java)**: Comprehensive error handling patterns
- **[PerformanceExample](src/main/java/pi2schema/crypto/providers/vault/examples/PerformanceExample.java)**: Performance optimization and concurrent usage

## GDPR Compliance

The provider supports GDPR right-to-be-forgotten through subject-specific key management:

1. Each subject gets a unique key in Vault: `{keyPrefix}/subject/{subjectId}`
2. When a subject requests data deletion, delete their key from Vault
3. Previously encrypted data becomes permanently inaccessible
4. Other subjects' data remains unaffected (cryptographic isolation)

```java
// To implement right-to-be-forgotten:
// 1. Delete the subject's key from Vault via API
// DELETE /v1/transit/keys/{keyPrefix}/subject/{subjectId}

// 2. Attempts to decrypt the subject's data will fail
try {
    provider.decryptionKeysFor(subjectId, encryptedKey, context).get();
} catch (Exception e) {
    // Will throw SubjectKeyNotFoundException
    // Data is now permanently inaccessible
}
```

## Error Handling

The provider includes specific exceptions for different failure scenarios:

- **VaultCryptoException**: Base exception for all Vault-related errors
- **VaultAuthenticationException**: Invalid or expired Vault token
- **VaultConnectivityException**: Network or connectivity issues
- **SubjectKeyNotFoundException**: Subject's key not found (e.g., after GDPR deletion)
- **InvalidEncryptionContextException**: Invalid or malformed encryption context

## Performance Considerations

### Best Practices

- **Reuse provider instances** across operations to benefit from connection pooling
- **Use asynchronous operations** with CompletableFuture for concurrency
- **Configure appropriate timeouts** based on your environment
- **Implement proper retry logic** for transient failures
- **Monitor Vault performance** and scale accordingly

### Concurrent Operations

```java
// Good: Concurrent operations with single provider
try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
    List<CompletableFuture<EncryptionMaterial>> futures = new ArrayList<>();
    
    for (String subjectId : subjectIds) {
        futures.add(provider.encryptionKeysFor(subjectId));
    }
    
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
}
```

## Security Considerations

- **Token Management**: Store Vault tokens securely (environment variables, secret management)
- **Network Security**: Use HTTPS for all Vault communication
- **Logging**: No sensitive data (keys, tokens) is logged
- **Encryption Context**: Includes subject ID validation to prevent key confusion attacks
- **Key Isolation**: Subject IDs are sanitized to prevent path traversal

## Vault Setup

### Prerequisites

1. Vault server with transit encryption engine enabled:
   ```bash
   vault secrets enable transit
   ```

2. Vault policy allowing key operations:
   ```hcl
   path "transit/encrypt/*" {
     capabilities = ["create", "update"]
   }
   
   path "transit/decrypt/*" {
     capabilities = ["create", "update"]
   }
   
   path "transit/keys/*" {
     capabilities = ["create", "read", "update", "delete"]
   }
   ```

3. Authentication token with appropriate permissions

### Development Setup with Docker

```bash
# Start Vault in dev mode
docker run --cap-add=IPC_LOCK -d --name=dev-vault -p 8200:8200 vault:latest

# Enable transit engine
docker exec dev-vault vault secrets enable transit

# Get root token
docker logs dev-vault | grep "Root Token"
```

## Testing

The module includes comprehensive tests:

- **Unit Tests**: Mock-based testing of individual components
- **Integration Tests**: Real Vault instance testing using Testcontainers
- **Performance Tests**: Concurrent operation and throughput testing
- **GDPR Tests**: Subject isolation and key deletion scenarios

Run tests with:
```bash
./gradlew :crypto-providers-vault:test
```

## Monitoring and Observability

The provider includes structured logging for monitoring:

- Request correlation IDs for tracing
- Performance metrics (operation timing)
- Error categorization and context
- Vault operation success/failure rates

Example log output:
```
INFO  VaultEncryptingMaterialsProvider - VaultEncryptingMaterialsProvider initialized [vaultUrl=https://vault.example.com:8200, transitEngine=transit, keyPrefix=myapp]
DEBUG VaultTransitClient - Starting encryption operation [requestId=123, keyName=myapp/subject/user-12345, plaintextSize=32, contextLength=45]
DEBUG VaultTransitClient - Encryption operation completed successfully [requestId=123, keyName=myapp/subject/user-12345, ciphertextSize=64]
```

## Migration and Compatibility

When upgrading or migrating:

1. **Version Compatibility**: Check encryption context version field
2. **Key Migration**: Existing keys in Vault remain compatible
3. **Configuration Changes**: Review new configuration options
4. **Testing**: Verify encrypt/decrypt cycle with existing data

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Verify Vault token is valid and not expired
   - Check token permissions for transit operations

2. **Connectivity Issues**
   - Verify Vault URL is accessible
   - Check network connectivity and firewall rules
   - Adjust connection and request timeouts

3. **Key Not Found Errors**
   - Verify subject ID format and sanitization
   - Check if key was deleted (GDPR scenario)
   - Ensure transit engine is enabled and accessible

4. **Performance Issues**
   - Monitor Vault server performance
   - Adjust retry and timeout settings
   - Consider Vault scaling and load balancing

### Debug Logging

Enable debug logging for detailed operation information:

```properties
logging.level.pi2schema.crypto.providers.vault=DEBUG
```

## Contributing

When contributing to this module:

1. Follow existing code patterns and documentation standards
2. Add comprehensive tests for new functionality
3. Update examples and documentation
4. Ensure security best practices are followed
5. Test with real Vault instances using integration tests

## License

This module is part of the pi2schema project and follows the same licensing terms.