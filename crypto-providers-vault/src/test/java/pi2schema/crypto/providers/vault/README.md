# Vault Crypto Provider Tests

This directory contains comprehensive tests for the Vault crypto providers, organized into focused test classes that minimize overlap and maintenance overhead.

## Test Structure

### Core Provider Tests
- **`VaultEncryptingMaterialsProviderTest`** - Unit tests for encryption provider with mocked dependencies
- **`VaultDecryptingMaterialsProviderTest`** - Unit tests for decryption provider with mocked dependencies

### Integration Tests
- **`VaultCryptoProviderIntegrationTest`** - End-to-end tests using Testcontainers with real Vault instance

### Configuration Tests
- **`VaultCryptoConfigurationTest`** - Configuration validation, builder pattern, and URL/token format validation

### Error Handling Tests
- **`VaultErrorHandlingAndLoggingTest`** - Network errors, connectivity issues, and logging scenarios

### Exception Tests
- **`VaultCryptoExceptionTest`** - Consolidated tests for all exception types using parameterized tests

## Test Coverage

The test suite comprehensively verifies:

### Core Functionality
- ✅ Encryption material generation and validation
- ✅ Data encryption key (DEK) creation and management
- ✅ Vault key encryption key (KEK) operations
- ✅ Subject isolation and cryptographic separation
- ✅ Encryption context validation and parsing
- ✅ Provider resource lifecycle management

### GDPR Compliance
- ✅ Subject-specific key naming and isolation
- ✅ Key deletion scenarios (right-to-be-forgotten)
- ✅ Data inaccessibility after key deletion
- ✅ Cryptographic isolation between subjects

### Error Handling & Resilience
- ✅ Network connectivity failures (timeouts, connection refused, DNS failures)
- ✅ Authentication and authorization failures
- ✅ Invalid configuration handling
- ✅ Vault server errors and responses
- ✅ Invalid encryption context scenarios
- ✅ Comprehensive logging without sensitive data exposure

### Performance & Concurrency
- ✅ Concurrent operations handling
- ✅ Performance characteristics under load
- ✅ Asynchronous operation handling with CompletableFuture
- ✅ Connection pooling and resource management

### Configuration & Validation
- ✅ Configuration builder pattern validation
- ✅ URL format validation
- ✅ Token format validation
- ✅ Timeout and retry configuration
- ✅ Key prefix and transit engine path validation

## Requirements Coverage

The integration tests verify the following requirements from the specification:

- **Requirement 4.1**: Complete encrypt/decrypt cycle with subject isolation
- **Requirement 4.2**: Concurrent operations and performance characteristics  
- **Requirement 6.1**: Subject isolation and cryptographic separation
- **Requirement 6.4**: GDPR key deletion scenarios
- **Requirement 6.5**: Data inaccessibility after key deletion

## Troubleshooting

### Docker Issues
If you encounter Docker-related issues (certificate expiration, connectivity):
1. Update Docker to the latest version
2. Check system time synchronization
3. Verify Docker daemon is running

### Vault Connection Issues
If tests fail with connectivity errors:
1. Verify Vault is running and accessible
2. Check firewall settings
3. Verify the correct Vault token is being used
4. Ensure the transit engine is enabled

### Performance Issues
If tests timeout or run slowly:
1. Increase timeout values in test configuration
2. Check system resources (CPU, memory)
3. Verify network connectivity to Vault

## Running Tests

**All tests:**
```bash
./gradlew :crypto-providers-vault:test
```

**Specific test classes:**
```bash
# Integration tests only
./gradlew :crypto-providers-vault:test --tests "*VaultCryptoProviderIntegrationTest*"

# Error handling tests only
./gradlew :crypto-providers-vault:test --tests "*VaultErrorHandlingAndLoggingTest*"
```

## Configuration

Test configurations can be adjusted by modifying the setup methods in each test class:

- **Integration Tests**: Use Testcontainers with dynamic Vault URLs
- **Unit Tests**: Use mocked dependencies for fast execution
- **Error Handling Tests**: Use WireMock for controlled error scenarios
- **Timeouts**: Optimized for reliable test execution
- **Retry Logic**: Configured for deterministic test behavior

## CI/CD Integration

For continuous integration environments:

```yaml
# Run all tests
- name: Vault Crypto Provider Tests
  run: ./gradlew :crypto-providers-vault:test
```

**Requirements:**
- Docker must be available for integration tests (Testcontainers)
- Sufficient system resources for concurrent test execution