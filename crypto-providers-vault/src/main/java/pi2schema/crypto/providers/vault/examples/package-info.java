/**
 * Comprehensive examples demonstrating usage patterns for the Vault crypto provider.
 * 
 * <p>This package contains practical examples showing how to use the Vault crypto provider
 * in various scenarios, from basic usage to advanced patterns like GDPR compliance,
 * error handling, and performance optimization.</p>
 * 
 * <h2>Available Examples</h2>
 * 
 * <h3>{@link pi2schema.crypto.providers.vault.examples.BasicUsageExample}</h3>
 * <p>Demonstrates the fundamental encrypt/decrypt workflow with the Vault crypto provider.
 * Shows how to configure the provider, encrypt data for a subject, and decrypt it later.</p>
 * 
 * <h3>{@link pi2schema.crypto.providers.vault.examples.ConfigurationExamples}</h3>
 * <p>Shows different configuration patterns for various deployment scenarios:</p>
 * <ul>
 *   <li>Development configuration with minimal settings</li>
 *   <li>Production configuration with custom timeouts and retry settings</li>
 *   <li>Microservices configuration optimized for low latency</li>
 *   <li>Batch processing configuration with longer timeouts</li>
 *   <li>Multi-tenant configuration with tenant-specific key prefixes</li>
 *   <li>Environment-based configuration using environment variables</li>
 *   <li>Test configuration for integration testing</li>
 * </ul>
 * 
 * <h3>{@link pi2schema.crypto.providers.vault.examples.GdprComplianceExample}</h3>
 * <p>Demonstrates GDPR right-to-be-forgotten implementation using subject-specific key deletion.
 * Shows how to:</p>
 * <ul>
 *   <li>Store encrypted data with subject isolation</li>
 *   <li>Retrieve and decrypt subject data</li>
 *   <li>Implement right-to-be-forgotten through key deletion</li>
 *   <li>Verify subject isolation (one subject's deletion doesn't affect others)</li>
 * </ul>
 * 
 * <h3>{@link pi2schema.crypto.providers.vault.examples.ErrorHandlingExample}</h3>
 * <p>Comprehensive error handling patterns covering:</p>
 * <ul>
 *   <li>Configuration validation errors</li>
 *   <li>Authentication failures</li>
 *   <li>Connectivity issues</li>
 *   <li>Subject key not found errors</li>
 *   <li>Invalid encryption context errors</li>
 *   <li>Retry patterns and strategies</li>
 *   <li>Graceful degradation approaches</li>
 * </ul>
 * 
 * <h3>{@link pi2schema.crypto.providers.vault.examples.PerformanceExample}</h3>
 * <p>Performance optimization and concurrent usage patterns including:</p>
 * <ul>
 *   <li>Concurrent encryption operations</li>
 *   <li>Batch processing patterns for high throughput</li>
 *   <li>Connection pooling and resource optimization</li>
 *   <li>Thread pool optimization</li>
 *   <li>Memory-efficient patterns for large datasets</li>
 * </ul>
 * 
 * <h2>Running the Examples</h2>
 * 
 * <p>To run the examples, you'll need:</p>
 * <ol>
 *   <li>A running Vault instance with transit encryption engine enabled</li>
 *   <li>A valid Vault token with appropriate permissions</li>
 *   <li>Set the {@code VAULT_TOKEN} environment variable</li>
 * </ol>
 * 
 * <p>For development, you can use Vault in dev mode:</p>
 * <pre>{@code
 * # Start Vault in dev mode
 * docker run --cap-add=IPC_LOCK -d --name=dev-vault -p 8200:8200 vault:latest
 * 
 * # Enable transit engine
 * docker exec dev-vault vault secrets enable transit
 * 
 * # Get root token
 * docker logs dev-vault | grep "Root Token"
 * 
 * # Set environment variable
 * export VAULT_TOKEN=<root-token>
 * }</pre>
 * 
 * <p>Then run any example:</p>
 * <pre>{@code
 * java pi2schema.crypto.providers.vault.examples.BasicUsageExample
 * }</pre>
 * 
 * <h2>Integration with Applications</h2>
 * 
 * <p>These examples can be adapted for integration with various application frameworks:</p>
 * <ul>
 *   <li><strong>Spring Boot</strong>: Use configuration properties and dependency injection</li>
 *   <li><strong>Micronaut</strong>: Leverage configuration and bean management</li>
 *   <li><strong>Quarkus</strong>: Use CDI and configuration annotations</li>
 *   <li><strong>Plain Java</strong>: Direct instantiation as shown in examples</li>
 * </ul>
 * 
 * <h2>Best Practices Demonstrated</h2>
 * 
 * <p>The examples demonstrate several best practices:</p>
 * <ul>
 *   <li><strong>Resource Management</strong>: Proper use of try-with-resources for provider cleanup</li>
 *   <li><strong>Asynchronous Operations</strong>: Using CompletableFuture for non-blocking operations</li>
 *   <li><strong>Error Handling</strong>: Specific exception handling for different failure scenarios</li>
 *   <li><strong>Configuration</strong>: Environment-based configuration for different deployment scenarios</li>
 *   <li><strong>Security</strong>: Secure token management and no sensitive data in logs</li>
 *   <li><strong>Performance</strong>: Provider reuse and concurrent operation patterns</li>
 * </ul>
 * 
 * @since 1.0
 */
package pi2schema.crypto.providers.vault.examples;