package pi2schema.crypto.providers.vault;

import com.google.crypto.tink.Aead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
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
class VaultDecryptingMaterialsProviderTest {

    @Mock
    private VaultTransitClient mockVaultClient;

    private VaultCryptoConfiguration config;
    private VaultDecryptingMaterialsProvider provider;
    private SecureRandom secureRandom;

    @BeforeEach
    void setUp() {
        config = VaultCryptoConfiguration
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

        secureRandom = new SecureRandom();
    }

    @Test
    void testSuccessfulDecryption() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        String encryptionContext = "subjectId=user-12345;timestamp=1234567890;version=1.0";
        byte[] encryptedDataKey = "vault:v1:encrypted-dek-data".getBytes();
        
        // Generate a valid 32-byte DEK
        byte[] dekBytes = new byte[32];
        secureRandom.nextBytes(dekBytes);

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, encryptionContext))
            .thenReturn(CompletableFuture.completedFuture(dekBytes));

        provider = createProviderWithMockedClient();

        // When
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, encryptionContext);
        Aead result = future.join();

        // Then
        assertNotNull(result);

        // Verify the AEAD primitive is functional by testing encrypt/decrypt
        byte[] testData = "test data".getBytes();
        byte[] associatedData = "associated data".getBytes();
        
        assertDoesNotThrow(() -> {
            byte[] encrypted = result.encrypt(testData, associatedData);
            byte[] decrypted = result.decrypt(encrypted, associatedData);
            assertArrayEquals(testData, decrypted);
        });

        // Verify Vault client interactions
        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).decrypt(expectedKeyName, encryptedDataKey, encryptionContext);
    }

    @Test
    void testNullSubjectIdThrowsException() {
        // Given
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        String encryptionContext = "subjectId=user-123;timestamp=1234567890;version=1.0";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(null, encryptedDataKey, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Subject ID cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptySubjectIdThrowsException() {
        // Given
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        String encryptionContext = "subjectId=user-123;timestamp=1234567890;version=1.0";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor("   ", encryptedDataKey, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Subject ID cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testNullEncryptedDataKeyThrowsException() {
        // Given
        String subjectId = "user-123";
        String encryptionContext = "subjectId=user-123;timestamp=1234567890;version=1.0";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, null, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Encrypted data key cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptyEncryptedDataKeyThrowsException() {
        // Given
        String subjectId = "user-123";
        String encryptionContext = "subjectId=user-123;timestamp=1234567890;version=1.0";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, new byte[0], encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Encrypted data key cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testNullEncryptionContextThrowsException() {
        // Given
        String subjectId = "user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        assertEquals("Encryption context cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptyEncryptionContextThrowsException() {
        // Given
        String subjectId = "user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, "   ");

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        assertEquals("Encryption context cannot be null or empty", exception.getCause().getMessage());

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testInvalidEncryptionContextFormatThrowsException() {
        // Given
        String subjectId = "user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        String invalidContext = "invalid-format";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, invalidContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        assertTrue(exception.getCause().getMessage().contains("Encryption context format is invalid"));

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testSubjectIdMismatchInContextThrowsException() {
        // Given
        String subjectId = "user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        String contextWithDifferentSubject = "subjectId=user-456;timestamp=1234567890;version=1.0";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, contextWithDifferentSubject);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        assertTrue(exception.getCause().getMessage().contains("Subject ID mismatch"));

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testInvalidTimestampInContextThrowsException() {
        // Given
        String subjectId = "user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        String contextWithInvalidTimestamp = "subjectId=user-123;timestamp=invalid;version=1.0";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, contextWithInvalidTimestamp);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        assertTrue(exception.getCause().getMessage().contains("Invalid timestamp format"));

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptyVersionInContextThrowsException() {
        // Given
        String subjectId = "user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        String contextWithEmptyVersion = "subjectId=user-123;timestamp=1234567890;version=";
        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, contextWithEmptyVersion);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof InvalidEncryptionContextException);
        assertTrue(exception.getCause().getMessage().contains("Version cannot be empty"));

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testSubjectKeyNotFoundThrowsException() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        String encryptionContext = "subjectId=user-12345;timestamp=1234567890;version=1.0";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        
        SubjectKeyNotFoundException keyNotFoundException = new SubjectKeyNotFoundException(
            subjectId, 
            "Key not found in Vault"
        );

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, encryptionContext))
            .thenReturn(CompletableFuture.failedFuture(keyNotFoundException));

        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof SubjectKeyNotFoundException);
        assertEquals(subjectId, ((SubjectKeyNotFoundException) exception.getCause()).getSubjectId());

        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).decrypt(expectedKeyName, encryptedDataKey, encryptionContext);
    }

    @Test
    void testVaultConnectivityFailure() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        String encryptionContext = "subjectId=user-12345;timestamp=1234567890;version=1.0";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        
        VaultConnectivityException connectivityException = new VaultConnectivityException("Vault is unreachable");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, encryptionContext))
            .thenReturn(CompletableFuture.failedFuture(connectivityException));

        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof VaultConnectivityException);
        assertEquals("Vault is unreachable", exception.getCause().getMessage());

        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).decrypt(expectedKeyName, encryptedDataKey, encryptionContext);
    }

    @Test
    void testVaultAuthenticationFailure() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        String encryptionContext = "subjectId=user-12345;timestamp=1234567890;version=1.0";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        
        VaultAuthenticationException authException = new VaultAuthenticationException("Invalid token");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, encryptionContext))
            .thenReturn(CompletableFuture.failedFuture(authException));

        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof VaultAuthenticationException);
        assertEquals("Invalid token", exception.getCause().getMessage());
    }

    @Test
    void testInvalidDekSizeThrowsException() {
        // Given
        String subjectId = "user-12345";
        String expectedKeyName = "pi2schema/subject/user-12345";
        String encryptionContext = "subjectId=user-12345;timestamp=1234567890;version=1.0";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        
        // Return invalid DEK size (not 32 bytes)
        byte[] invalidDekBytes = new byte[16]; // Only 16 bytes instead of 32

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, encryptionContext))
            .thenReturn(CompletableFuture.completedFuture(invalidDekBytes));

        provider = createProviderWithMockedClient();

        // When & Then
        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, encryptionContext);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause() instanceof VaultCryptoException);
        assertTrue(exception.getCause().getMessage().contains("Invalid DEK size"));
    }

    @Test
    void testConcurrentDecryptionOperations() throws InterruptedException {
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
        
        when(mockVaultClient.decrypt(anyString(), any(byte[].class), anyString()))
            .thenAnswer(invocation -> {
                byte[] dekBytes = new byte[32];
                secureRandom.nextBytes(dekBytes);
                return CompletableFuture.completedFuture(dekBytes);
            });

        provider = createProviderWithMockedClient();

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        String subjectId = "user-" + threadId + "-" + j;
                        String encryptionContext = String.format("subjectId=%s;timestamp=1234567890;version=1.0", subjectId);
                        byte[] encryptedDataKey = ("encrypted-dek-" + threadId + "-" + j).getBytes();
                        
                        CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, encryptionContext);
                        Aead result = future.get(5, TimeUnit.SECONDS);

                        assertNotNull(result);
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
            .decrypt(anyString(), any(byte[].class), anyString());

        executor.shutdown();
    }

    @Test
    void testValidEncryptionContextVariations() {
        // Given
        String subjectId = "user-123";
        String expectedKeyName = "pi2schema/subject/user-123";
        byte[] encryptedDataKey = "encrypted-dek".getBytes();
        byte[] dekBytes = new byte[32];
        secureRandom.nextBytes(dekBytes);

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(eq(expectedKeyName), eq(encryptedDataKey), anyString()))
            .thenReturn(CompletableFuture.completedFuture(dekBytes));

        provider = createProviderWithMockedClient();

        // Test different valid context variations
        String[] validContexts = {
            "subjectId=user-123;timestamp=1234567890;version=1.0",
            "subjectId=user-123;timestamp=0;version=2.0",
            "subjectId=user-123;timestamp=9999999999999;version=1.0.0",
            "subjectId=user-123;timestamp=1234567890;version=beta"
        };

        for (String context : validContexts) {
            // When & Then
            CompletableFuture<Aead> future = provider.decryptionKeysFor(subjectId, encryptedDataKey, context);
            Aead result = assertDoesNotThrow(() -> future.join());
            assertNotNull(result);
        }
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
            () -> new VaultDecryptingMaterialsProvider(null)
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
            () -> new VaultDecryptingMaterialsProvider(invalidConfig)
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
            () -> new VaultDecryptingMaterialsProvider(invalidConfig)
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
            () -> new VaultDecryptingMaterialsProvider(invalidConfig)
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
            () -> new VaultDecryptingMaterialsProvider(invalidConfig)
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
            () -> new VaultDecryptingMaterialsProvider(invalidConfig)
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
        assertDoesNotThrow(() -> new VaultDecryptingMaterialsProvider(validConfig));
    }

    @Test
    void testResourceLifecycle_tryWithResources() {
        // Test that the provider works properly with try-with-resources
        assertDoesNotThrow(() -> {
            try (VaultDecryptingMaterialsProvider testProvider = new VaultDecryptingMaterialsProvider(config)) {
                // Use the provider
                assertNotNull(testProvider);
            }
            // close() should be called automatically
        });
    }

    @Test
    void testClose_canBeCalledMultipleTimes() {
        // Given
        VaultDecryptingMaterialsProvider testProvider = new VaultDecryptingMaterialsProvider(config);

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
    private VaultDecryptingMaterialsProvider createProviderWithMockedClient() {
        VaultDecryptingMaterialsProvider provider = new VaultDecryptingMaterialsProvider(config);

        // Use reflection to replace the vaultClient field with our mock
        try {
            java.lang.reflect.Field vaultClientField =
                VaultDecryptingMaterialsProvider.class.getDeclaredField("vaultClient");
            vaultClientField.setAccessible(true);
            vaultClientField.set(provider, mockVaultClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock VaultTransitClient", e);
        }

        return provider;
    }
}