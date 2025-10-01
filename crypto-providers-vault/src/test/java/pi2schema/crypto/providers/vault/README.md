# Vault Crypto Provider Integration Tests

This directory contains integration tests for the Vault crypto providers that verify real-world functionality with actual Vault instances.

## Test Types

### Integration Tests (`VaultCryptoProviderIntegrationTest`)

These tests use Testcontainers to spin up a real Vault instance and test all functionality including:
- Complete encrypt/decrypt cycles with subject isolation
- GDPR compliance scenarios (key deletion and data inaccessibility)
- Concurrent operations and performance characteristics
- Real Vault API interactions
- Error handling with invalid configurations
- Encryption context validation
- Provider resource lifecycle management

**Requirements:**
- Docker must be installed and running
- Sufficient system resources to run containers

**Running:**
```bash
./gradlew :crypto-providers-vault:test --tests "*VaultCryptoProviderIntegrationTest*"
```

## Test Coverage

The integration test suite verifies:

### Core Functionality
- ✅ Basic encrypt/decrypt cycle
- ✅ Subject isolation (different subjects get different keys)
- ✅ Encryption context validation
- ✅ Provider resource lifecycle management

### GDPR Compliance
- ✅ Key deletion scenarios
- ✅ Data inaccessibility after key deletion
- ✅ Subject-specific key management

### Performance & Concurrency
- ✅ Concurrent operations handling
- ✅ Performance characteristics under load
- ✅ Asynchronous operation handling

### Error Handling
- ✅ Invalid configuration handling
- ✅ Network connectivity issues
- ✅ Authentication failures
- ✅ Invalid encryption context handling

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

## Configuration

Test configurations can be adjusted by modifying the setup methods in the test class:

- **Vault URL**: Container URL provided by Testcontainers
- **Vault Token**: Default `test-token`
- **Transit Engine Path**: Default `transit`
- **Key Prefix**: Test-specific prefixes to avoid conflicts
- **Timeouts**: Connection and request timeouts
- **Retry Logic**: Maximum retries and backoff settings

## CI/CD Integration

For continuous integration environments, the tests use Testcontainers with Docker:

```yaml
# Run integration tests
- name: Integration Tests
  run: ./gradlew :crypto-providers-vault:test
```

The test suite requires Docker to be available in the CI environment.