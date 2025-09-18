package pi2schema.crypto.providers.vault.examples;

import com.google.crypto.tink.Aead;
import pi2schema.crypto.providers.EncryptionMaterial;
import pi2schema.crypto.providers.vault.VaultCryptoConfiguration;
import pi2schema.crypto.providers.vault.VaultDecryptingMaterialsProvider;
import pi2schema.crypto.providers.vault.VaultEncryptingMaterialsProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating performance optimization and concurrent usage patterns
 * with Vault crypto providers.
 * 
 * <p>This example shows how to optimize performance through proper configuration,
 * concurrent operations, and resource management.</p>
 */
public class PerformanceExample {

    /**
     * Demonstrates concurrent encryption operations.
     */
    public static void demonstrateConcurrentEncryption() throws Exception {
        System.out.println("=== Concurrent Encryption Performance ===");

        // Optimized configuration for high throughput
        VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .connectionTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(15))
            .maxRetries(2)
            .retryBackoffMs(Duration.ofMillis(50))
            .build();

        int numberOfOperations = 100;
        int numberOfThreads = 10;
        
        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            
            // Sequential operations baseline
            Instant start = Instant.now();
            for (int i = 0; i < 10; i++) {
                String subjectId = "user-" + i;
                CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(subjectId);
                future.get(); // Wait for completion
            }
            Duration sequentialTime = Duration.between(start, Instant.now());
            System.out.println("Sequential (10 ops): " + sequentialTime.toMillis() + "ms");

            // Concurrent operations
            start = Instant.now();
            List<CompletableFuture<EncryptionMaterial>> futures = new ArrayList<>();
            
            for (int i = 0; i < numberOfOperations; i++) {
                String subjectId = "user-" + i;
                CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(subjectId);
                futures.add(future);
            }
            
            // Wait for all operations to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            allFutures.get();
            
            Duration concurrentTime = Duration.between(start, Instant.now());
            System.out.println("Concurrent (" + numberOfOperations + " ops): " + concurrentTime.toMillis() + "ms");
            System.out.println("Throughput: " + (numberOfOperations * 1000.0 / concurrentTime.toMillis()) + " ops/sec");
        }
    }

    /**
     * Demonstrates batch processing patterns for high throughput.
     */
    public static void demonstrateBatchProcessing() throws Exception {
        System.out.println("\n=== Batch Processing Patterns ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .build();

        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            userIds.add("batch-user-" + i);
        }

        try (VaultEncryptingMaterialsProvider encProvider = new VaultEncryptingMaterialsProvider(config);
             VaultDecryptingMaterialsProvider decProvider = new VaultDecryptingMaterialsProvider(config)) {

            Instant start = Instant.now();

            // Batch encrypt multiple users' data
            List<CompletableFuture<EncryptionResult>> encryptionFutures = new ArrayList<>();
            
            for (String userId : userIds) {
                CompletableFuture<EncryptionResult> future = encProvider.encryptionKeysFor(userId)
                    .thenApply(material -> {
                        try {
                            String data = "Personal data for " + userId;
                            byte[] encrypted = material.aead().encrypt(data.getBytes(StandardCharsets.UTF_8), null);
                            return new EncryptionResult(userId, encrypted, material.encryptedDataKey(), material.encryptionContext());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                encryptionFutures.add(future);
            }

            // Wait for all encryptions to complete
            List<EncryptionResult> results = new ArrayList<>();
            for (CompletableFuture<EncryptionResult> future : encryptionFutures) {
                results.add(future.get());
            }

            Duration encryptionTime = Duration.between(start, Instant.now());
            System.out.println("Batch encryption (" + userIds.size() + " users): " + encryptionTime.toMillis() + "ms");

            // Batch decrypt (simulate reading from storage)
            start = Instant.now();
            List<CompletableFuture<String>> decryptionFutures = new ArrayList<>();

            for (EncryptionResult result : results) {
                CompletableFuture<String> future = decProvider.decryptionKeysFor(
                    result.userId, result.encryptedDataKey, result.encryptionContext
                ).thenApply(aead -> {
                    try {
                        byte[] decrypted = aead.decrypt(result.encryptedData, null);
                        return new String(decrypted, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                decryptionFutures.add(future);
            }

            // Wait for all decryptions to complete
            for (CompletableFuture<String> future : decryptionFutures) {
                future.get();
            }

            Duration decryptionTime = Duration.between(start, Instant.now());
            System.out.println("Batch decryption (" + results.size() + " users): " + decryptionTime.toMillis() + "ms");
            
            double totalThroughput = (userIds.size() * 2 * 1000.0) / (encryptionTime.toMillis() + decryptionTime.toMillis());
            System.out.println("Total throughput: " + String.format("%.2f", totalThroughput) + " ops/sec");
        }
    }

    /**
     * Demonstrates connection pooling and resource optimization.
     */
    public static void demonstrateConnectionOptimization() throws Exception {
        System.out.println("\n=== Connection Optimization ===");

        // Configuration optimized for connection reuse
        VaultCryptoConfiguration optimizedConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .connectionTimeout(Duration.ofSeconds(10))
            .requestTimeout(Duration.ofSeconds(20))
            .maxRetries(3)
            .retryBackoffMs(Duration.ofMillis(100))
            .build();

        // Test with provider reuse (recommended pattern)
        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(optimizedConfig)) {
            
            Instant start = Instant.now();
            List<CompletableFuture<EncryptionMaterial>> futures = new ArrayList<>();
            
            // Multiple operations with same provider instance
            for (int i = 0; i < 20; i++) {
                CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("perf-user-" + i);
                futures.add(future);
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            Duration reuseTime = Duration.between(start, Instant.now());
            
            System.out.println("Provider reuse (20 ops): " + reuseTime.toMillis() + "ms");
            System.out.println("Average per operation: " + (reuseTime.toMillis() / 20.0) + "ms");
        }

        // Compare with provider-per-operation (anti-pattern)
        Instant start = Instant.now();
        for (int i = 0; i < 5; i++) { // Fewer operations due to overhead
            try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(optimizedConfig)) {
                CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("perf-user-" + i);
                future.get();
            }
        }
        Duration newProviderTime = Duration.between(start, Instant.now());
        
        System.out.println("New provider per op (5 ops): " + newProviderTime.toMillis() + "ms");
        System.out.println("Average per operation: " + (newProviderTime.toMillis() / 5.0) + "ms");
        System.out.println("→ Provider reuse is " + String.format("%.1fx", (newProviderTime.toMillis() / 5.0) / (reuseTime.toMillis() / 20.0)) + " faster");
    }

    /**
     * Demonstrates thread pool optimization for CPU-bound operations.
     */
    public static void demonstrateThreadPoolOptimization() throws Exception {
        System.out.println("\n=== Thread Pool Optimization ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .build();

        int numberOfOperations = 50;
        
        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            
            // Test with different thread pool sizes
            int[] threadPoolSizes = {1, 5, 10, 20};
            
            for (int poolSize : threadPoolSizes) {
                ExecutorService executor = Executors.newFixedThreadPool(poolSize);
                
                Instant start = Instant.now();
                List<CompletableFuture<EncryptionMaterial>> futures = new ArrayList<>();
                
                for (int i = 0; i < numberOfOperations; i++) {
                    final int userId = i;
                    CompletableFuture<EncryptionMaterial> future = CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return provider.encryptionKeysFor("thread-user-" + userId).get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, executor);
                    futures.add(future);
                }
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                Duration duration = Duration.between(start, Instant.now());
                
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
                
                System.out.println("Thread pool size " + poolSize + ": " + duration.toMillis() + "ms");
            }
        }
    }

    /**
     * Demonstrates memory-efficient patterns for large datasets.
     */
    public static void demonstrateMemoryEfficiency() throws Exception {
        System.out.println("\n=== Memory Efficiency Patterns ===");

        VaultCryptoConfiguration config = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com:8200")
            .vaultToken(System.getenv("VAULT_TOKEN"))
            .build();

        try (VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config)) {
            
            // Streaming pattern - process one at a time to minimize memory usage
            System.out.println("Processing 1000 users with streaming pattern...");
            
            Instant start = Instant.now();
            int processedCount = 0;
            
            for (int i = 0; i < 1000; i++) {
                String userId = "stream-user-" + i;
                
                // Process immediately and don't accumulate results
                CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(userId);
                EncryptionMaterial material = future.get();
                
                // Simulate processing (encrypt some data)
                String data = "Data for " + userId;
                byte[] encrypted = material.aead().encrypt(data.getBytes(StandardCharsets.UTF_8), null);
                
                // In practice, you would write to storage here and discard the material
                processedCount++;
                
                if (processedCount % 100 == 0) {
                    System.out.println("Processed " + processedCount + " users...");
                }
            }
            
            Duration streamingTime = Duration.between(start, Instant.now());
            System.out.println("Streaming processing: " + streamingTime.toMillis() + "ms");
            System.out.println("Memory usage: Constant (one operation at a time)");
        }
    }

    /**
     * Helper class for batch processing results.
     */
    private static class EncryptionResult {
        final String userId;
        final byte[] encryptedData;
        final byte[] encryptedDataKey;
        final String encryptionContext;

        EncryptionResult(String userId, byte[] encryptedData, byte[] encryptedDataKey, String encryptionContext) {
            this.userId = userId;
            this.encryptedData = encryptedData;
            this.encryptedDataKey = encryptedDataKey;
            this.encryptionContext = encryptionContext;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Vault Crypto Provider Performance Examples");
        System.out.println("==========================================");
        
        demonstrateConcurrentEncryption();
        demonstrateBatchProcessing();
        demonstrateConnectionOptimization();
        demonstrateThreadPoolOptimization();
        demonstrateMemoryEfficiency();

        System.out.println("\n=== Performance Best Practices ===");
        System.out.println("✓ Reuse provider instances across operations");
        System.out.println("✓ Use asynchronous operations for concurrency");
        System.out.println("✓ Configure appropriate timeouts and retry settings");
        System.out.println("✓ Use streaming patterns for large datasets");
        System.out.println("✓ Monitor and tune thread pool sizes");
        System.out.println("✓ Implement proper resource cleanup");
        System.out.println("✓ Consider connection pooling and keep-alive settings");
    }
}