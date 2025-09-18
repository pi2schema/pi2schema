package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pi2schema.crypto.providers.EncryptionMaterial;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultEncryptingMaterialsProviderTest {

    @Mock
    private VaultTransitClient mockVaultClient;

    private VaultCryptoConfiguration config;
    private VaultEncryptingMaterialsProvider provider;

    @BeforeEach
    void setUp() {
        config =
            VaultCryptoConfiguration
                .builder()
                .vaultUrl("https://vault.example.com")
                .vaultToken("test-token")
                .transitEnginePath("transit")
                .keyPrefix("pi2schema")
                .connectionTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .retryBackoffMs(Duration.ofMillis(100))
                .build();
    }

    @Test
    void testSuccessfulEncryptionMaterialGeneration() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        byte[] mockEncryptedDek = "encrypted-dek-data".getBytes();

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEncryptedDek));

        provider = createProviderWithMockedClient();

        // When
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(subjectId);
        EncryptionMaterial result = future.join();

        // Then
        assertNotNull(result);
        assertNotNull(result.dataEncryptionKey());
        assertArrayEquals(mockEncryptedDek, result.encryptedDataKey());
        assertNotNull(result.encryptionContext());

        // Verify encryption context format
        String context = result.encryptionContext();
        assertTrue(context.contains("subjectId=" + subjectId));
        assertTrue(context.contains("version=1.0"));
        assertTrue(context.contains("timestamp="));

        // Verify DEK is functional
        Aead aead = result.dataEncryptionKey();
        assertNotNull(aead);

        // Verify Vault client interactions
        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).encrypt(eq(expectedKeyName), any(byte[].class), anyString());
    }

    @Test
    void testNullSubjectIdThrowsException() {
        // Given
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(null);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Subject ID cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptySubjectIdThrowsException() {
        // Given
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor("   ");

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Subject ID cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testVaultConnectivityFailure() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        VaultConnectivityException vaultException = new VaultConnectivityException("Vault is unreachable");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.failedFuture(vaultException));

        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(subjectId);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof VaultConnectivityException);
        assertEquals("Vault is unreachable", exception.getCause().getMessage());

        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).encrypt(eq(expectedKeyName), any(byte[].class), anyString());
    }

    @Test
    void testVaultAuthenticationFailure() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        VaultAuthenticationException authException = new VaultAuthenticationException("Invalid token");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.failedFuture(authException));

        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(subjectId);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof VaultAuthenticationException);
        assertEquals("Invalid token", exception.getCause().getMessage());
    }

    @Test
    void testConcurrentOperations() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads * operationsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Exception> firstException = new AtomicReference<>();

        // Mock successful responses for all operations
        when(mockVaultClient.generateKeyName(anyString()))
            .thenAnswer(invocation -> "pi2schema/subject/" + invocation.getArgument(0));
        when(mockVaultClient.encrypt(anyString(), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.completedFuture("encrypted-data".getBytes()));

        provider = createProviderWithMockedClient();

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        String subjectId = "user-" + threadId + "-" + j;
                        CompletableFuture<EncryptionMaterial> future = provider.encryptionKeysFor(subjectId);
                        EncryptionMaterial result = future.get(5, TimeUnit.SECONDS);

                        assertNotNull(result);
                        assertNotNull(result.dataEncryptionKey());
                        assertNotNull(result.encryptedDataKey());
                        assertNotNull(result.encryptionContext());

                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        firstException.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Then
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Operations did not complete within timeout");

        if (firstException.get() != null) {
            fail("Concurrent operation failed: " + firstException.get().getMessage(), firstException.get());
        }

        assertEquals(numberOfThreads * operationsPerThread, successCount.get());

        // Verify all operations called the vault client
        verify(mockVaultClient, times(numberOfThreads * operationsPerThread)).generateKeyName(anyString());
        verify(mockVaultClient, times(numberOfThreads * operationsPerThread))
            .encrypt(anyString(), any(byte[].class), anyString());

        executor.shutdown();
    }

    @Test
    void testEncryptionContextFormat() {
        // Given
        String subjectId = "test-user-123";
        String expectedKeyName = "pi2schema/subject/test-user-123";
        byte[] mockEncryptedDek = "encrypted-dek".getBytes();

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEncryptedDek));

        provider = createProviderWithMockedClient();

        // When
        EncryptionMaterial result = provider.encryptionKeysFor(subjectId).join();

        // Then
        String context = result.encryptionContext();
        String[] parts = context.split(";");

        assertEquals(3, parts.length);

        // Verify subject ID
        assertTrue(parts[0].startsWith("subjectId="));
        assertEquals(subjectId, parts[0].substring("subjectId=".length()));

        // Verify timestamp is a valid number
        assertTrue(parts[1].startsWith("timestamp="));
        String timestampStr = parts[1].substring("timestamp=".length());
        assertDoesNotThrow(() -> Long.parseLong(timestampStr));

        // Verify version
        assertTrue(parts[2].startsWith("version="));
        assertEquals("1.0", parts[2].substring("version=".length()));
    }

    @Test
    void testDifferentSubjectsGetDifferentKeys() {
        // Given
        String subjectId1 = "user-1";
        String subjectId2 = "user-2";
        byte[] encryptedDek1 = "encrypted-dek-1".getBytes();
        byte[] encryptedDek2 = "encrypted-dek-2".getBytes();

        when(mockVaultClient.generateKeyName("user-1")).thenReturn("pi2schema/subject/user-1");
        when(mockVaultClient.generateKeyName("user-2")).thenReturn("pi2schema/subject/user-2");
        when(mockVaultClient.encrypt(eq("pi2schema/subject/user-1"), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.completedFuture(encryptedDek1));
        when(mockVaultClient.encrypt(eq("pi2schema/subject/user-2"), any(byte[].class), anyString()))
            .thenReturn(CompletableFuture.completedFuture(encryptedDek2));

        provider = createProviderWithMockedClient();

        // When
        EncryptionMaterial result1 = provider.encryptionKeysFor(subjectId1).join();
        EncryptionMaterial result2 = provider.encryptionKeysFor(subjectId2).join();

        // Then
        assertNotEquals(result1.dataEncryptionKey(), result2.dataEncryptionKey());
        assertFalse(java.util.Arrays.equals(result1.encryptedDataKey(), result2.encryptedDataKey()));
        assertNotEquals(result1.encryptionContext(), result2.encryptionContext());

        // Verify different vault keys were used
        verify(mockVaultClient).encrypt(eq("pi2schema/subject/user-1"), any(byte[].class), anyString());
        verify(mockVaultClient).encrypt(eq("pi2schema/subject/user-2"), any(byte[].class), anyString());
    }

    @Test
    void testProviderClose() {
        // Given
        provider = createProviderWithMockedClient();

        // When
        assertDoesNotThrow(() -> provider.close());

        // Then
        verify(mockVaultClient).close();
    }

    @Test
    void testProviderCloseWithException() {
        // Given
        doThrow(new RuntimeException("Close failed")).when(mockVaultClient).close();
        provider = createProviderWithMockedClient();

        // When & Then
        assertDoesNotThrow(() -> provider.close()); // Should not propagate exception

        verify(mockVaultClient).close();
    }

    @Test
    void testNullConfigurationThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new VaultEncryptingMaterialsProvider(null)
        );

        assertEquals("Configuration cannot be null", exception.getMessage());
    }

    @Test
    void testInvalidVaultUrlThrowsException() {
        // Given
        VaultCryptoConfiguration invalidConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("invalid-url")
            .vaultToken("test-token")
            .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new VaultEncryptingMaterialsProvider(invalidConfig)
        );

        assertEquals("Vault URL must start with http:// or https://", exception.getMessage());
    }

    @Test
    void testVaultTokenWithWhitespaceThrowsException() {
        // Given
        VaultCryptoConfiguration invalidConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken(" test-token ")
            .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new VaultEncryptingMaterialsProvider(invalidConfig)
        );

        assertEquals("Vault token cannot contain leading or trailing whitespace", exception.getMessage());
    }

    @Test
    void testInvalidKeyPrefixThrowsException() {
        // Given
        VaultCryptoConfiguration invalidConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .keyPrefix("invalid/prefix")
            .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new VaultEncryptingMaterialsProvider(invalidConfig)
        );

        assertEquals("Key prefix can only contain alphanumeric characters, underscores, and hyphens", exception.getMessage());
    }

    @Test
    void testExcessiveConnectionTimeoutThrowsException() {
        // Given
        VaultCryptoConfiguration invalidConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .connectionTimeout(Duration.ofMinutes(10)) // Exceeds 5 minute limit
            .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new VaultEncryptingMaterialsProvider(invalidConfig)
        );

        assertEquals("Connection timeout cannot exceed 5 minutes", exception.getMessage());
    }

    @Test
    void testExcessiveRequestTimeoutThrowsException() {
        // Given
        VaultCryptoConfiguration invalidConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("test-token")
            .requestTimeout(Duration.ofMinutes(15)) // Exceeds 10 minute limit
            .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new VaultEncryptingMaterialsProvider(invalidConfig)
        );

        assertEquals("Request timeout cannot exceed 10 minutes", exception.getMessage());
    }

    @Test
    void testValidConfigurationAccepted() {
        // Given
        VaultCryptoConfiguration validConfig = VaultCryptoConfiguration.builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("valid-token")
            .keyPrefix("valid_prefix-123")
            .connectionTimeout(Duration.ofSeconds(30))
            .requestTimeout(Duration.ofMinutes(2))
            .build();

        // When & Then
        assertDoesNotThrow(() -> new VaultEncryptingMaterialsProvider(validConfig));
    }

    @Test
    void testResourceLifecycle_tryWithResources() {
        // Test that the provider works properly with try-with-resources
        assertDoesNotThrow(() -> {
            try (VaultEncryptingMaterialsProvider testProvider = new VaultEncryptingMaterialsProvider(config)) {
                // Use the provider
                assertNotNull(testProvider);
            }
            // close() should be called automatically
        });
    }

    @Test
    void testClose_canBeCalledMultipleTimes() {
        // Given
        VaultEncryptingMaterialsProvider testProvider = new VaultEncryptingMaterialsProvider(config);

        // When & Then - close() should be idempotent
        assertDoesNotThrow(() -> {
            testProvider.close();
            testProvider.close();
            testProvider.close();
        });
    }

    /**
     * Creates a provider instance with the mocked VaultTransitClient.
     * This uses reflection to inject the mock since we want to test the provider logic
     * without actually creating a real VaultTransitClient.
     */
    private VaultEncryptingMaterialsProvider createProviderWithMockedClient() {
        VaultEncryptingMaterialsProvider provider = new VaultEncryptingMaterialsProvider(config);

        // Use reflection to replace the vaultClient field with our mock
        try {
            java.lang.reflect.Field vaultClientField =
                VaultEncryptingMaterialsProvider.class.getDeclaredField("vaultClient");
            vaultClientField.setAccessible(true);
            vaultClientField.set(provider, mockVaultClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock VaultTransitClient", e);
        }

        return provider;
    }
}
