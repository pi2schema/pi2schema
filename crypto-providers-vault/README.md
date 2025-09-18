# Crypto Providers - Vault

This module provides a HashiCorp Vault-based implementation of the crypto-spi interfaces for GDPR-compliant encryption key management.

## Features

- **VaultEncryptingMaterialsProvider**: Generates Data Encryption Keys (DEKs) and encrypts them using Vault's transit encryption engine
- **VaultDecryptingMaterialsProvider**: Decrypts DEKs using Vault's transit encryption for data access
- **Subject-based Key Isolation**: Each subject gets cryptographically separate encryption materials
- **GDPR Compliance**: Supports right-to-be-forgotten through subject-specific key deletion
- **Asynchronous Operations**: Non-blocking operations using CompletableFuture
- **Connection Pooling**: Optimized performance for concurrent requests

## Dependencies

- HashiCorp Vault with transit encryption engine enabled
- Vault Java Driver for API interactions
- Google Tink for cryptographic primitives
- Jackson for JSON processing

## Usage

See the examples and integration tests for detailed usage patterns.