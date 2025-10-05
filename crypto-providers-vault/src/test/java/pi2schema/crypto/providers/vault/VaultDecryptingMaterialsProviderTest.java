package pi2schema.crypto.providers.vault;

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

import static org.assertj.core.api.Assertions.*;
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

        secureRandom = new SecureRandom();
    }

    @Test
    void testSuccessfulDecryption() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var encryptedDataKey = "vault:v1:encrypted-dek-data".getBytes();

        // Generate a valid 32-byte DEK
        var dekBytes = new byte[32];
        secureRandom.nextBytes(dekBytes);

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, null))
            .thenReturn(CompletableFuture.completedFuture(dekBytes));

        provider = createProviderWithMockedClient();

        // When
        var future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);
        var result = future.join();

        // Then
        assertThat(result).isNotNull();

        // Verify the AEAD primitive is functional by testing encrypt/decrypt
        var testData = "test data".getBytes();
        var associatedData = "associated data".getBytes();

        assertThatCode(() -> {
                var encrypted = result.encrypt(testData, associatedData);
                var decrypted = result.decrypt(encrypted, associatedData);
                assertThat(decrypted).isEqualTo(testData);
            })
            .doesNotThrowAnyException();

        // Verify Vault client interactions
        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).decrypt(expectedKeyName, encryptedDataKey, null);
    }

    @Test
    void testNullSubjectIdThrowsException() {
        // Given
        var encryptedDataKey = "encrypted-dek".getBytes();
        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(null, encryptedDataKey, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Subject ID cannot be null or empty");

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptySubjectIdThrowsException() {
        // Given
        var encryptedDataKey = "encrypted-dek".getBytes();
        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor("   ", encryptedDataKey, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Subject ID cannot be null or empty");

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testNullEncryptedDataKeyThrowsException() {
        // Given
        var subjectId = "user-123";
        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(subjectId, null, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Encrypted data key cannot be null or empty [subjectId=user-123]");

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testEmptyEncryptedDataKeyThrowsException() {
        // Given
        var subjectId = "user-123";
        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(subjectId, new byte[0], null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Encrypted data key cannot be null or empty [subjectId=user-123]");

        verifyNoInteractions(mockVaultClient);
    }

    // Encryption context validation tests removed - MVP relies on Vault access controls

    @Test
    void testSubjectKeyNotFoundThrowsException() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var encryptedDataKey = "encrypted-dek".getBytes();

        var keyNotFoundException = new SubjectKeyNotFoundException(subjectId, "Key not found in Vault");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, null))
            .thenReturn(CompletableFuture.failedFuture(keyNotFoundException));

        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(SubjectKeyNotFoundException.class)
            .satisfies(ex -> {
                var cause = (SubjectKeyNotFoundException) ex.getCause();
                assertThat(cause.getSubjectId()).isEqualTo(subjectId);
            });

        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).decrypt(expectedKeyName, encryptedDataKey, null);
    }

    @Test
    void testVaultConnectivityFailure() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var encryptedDataKey = "encrypted-dek".getBytes();

        var connectivityException = new VaultConnectivityException("Vault is unreachable");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, null))
            .thenReturn(CompletableFuture.failedFuture(connectivityException));

        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(VaultConnectivityException.class)
            .hasMessage("Vault is unreachable");

        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).decrypt(expectedKeyName, encryptedDataKey, null);
    }

    @Test
    void testVaultAuthenticationFailure() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var encryptedDataKey = "encrypted-dek".getBytes();

        var authException = new VaultAuthenticationException("Invalid token");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, null))
            .thenReturn(CompletableFuture.failedFuture(authException));

        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(VaultAuthenticationException.class)
            .hasMessage("Invalid token");
    }

    @Test
    void testInvalidDekSizeThrowsException() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var encryptedDataKey = "encrypted-dek".getBytes();

        // Return truly invalid DEK size (not 16 or 32 bytes)
        var invalidDekBytes = new byte[15]; // Invalid size for AES

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.decrypt(expectedKeyName, encryptedDataKey, null))
            .thenReturn(CompletableFuture.completedFuture(invalidDekBytes));

        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOfAny(VaultCryptoException.class, java.security.GeneralSecurityException.class);
    }

    @Test
    void testConcurrentDecryptionOperations() throws InterruptedException {
        // Given
        var numberOfThreads = 10;
        var operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        var latch = new CountDownLatch(numberOfThreads * operationsPerThread);
        var successCount = new AtomicInteger(0);
        var firstException = new AtomicReference<Exception>();

        // Mock successful responses for all operations
        when(mockVaultClient.generateKeyName(anyString()))
            .thenAnswer(invocation -> "pi2schema/subject/" + invocation.getArgument(0));

        when(mockVaultClient.decrypt(anyString(), any(byte[].class), isNull()))
            .thenAnswer(invocation -> {
                var dekBytes = new byte[32];
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
                        var subjectId = "user-" + threadId + "-" + j;
                        var encryptionContext = String.format(
                            "subjectId=%s;timestamp=1234567890;version=1.0",
                            subjectId
                        );
                        var encryptedDataKey = ("encrypted-dek-" + threadId + "-" + j).getBytes();

                        var future = provider.decryptionKeysFor(subjectId, encryptedDataKey, null);
                        var result = future.get(5, TimeUnit.SECONDS);

                        assertThat(result).isNotNull();
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
        assertThat(latch.await(30, TimeUnit.SECONDS))
            .withFailMessage("Operations did not complete within timeout")
            .isTrue();

        assertThat(firstException.get()).isNull();

        assertThat(successCount.get()).isEqualTo(numberOfThreads * operationsPerThread);

        // Verify all operations called the vault client
        verify(mockVaultClient, times(numberOfThreads * operationsPerThread)).generateKeyName(anyString());
        verify(mockVaultClient, times(numberOfThreads * operationsPerThread))
            .decrypt(anyString(), any(byte[].class), isNull());

        executor.shutdown();
    }

    // testValidEncryptionContextVariations removed - encryption context not used in MVP

    @Test
    void testProviderClose() {
        // Given
        provider = createProviderWithMockedClient();

        // When
        assertThatCode(() -> provider.close()).doesNotThrowAnyException();

        // Then
        verify(mockVaultClient).close();
    }

    @Test
    void testProviderCloseWithException() {
        // Given
        doThrow(new RuntimeException("Close failed")).when(mockVaultClient).close();
        provider = createProviderWithMockedClient();

        // When & Then
        assertThatCode(() -> provider.close()).doesNotThrowAnyException(); // Should not propagate exception

        verify(mockVaultClient).close();
    }

    @Test
    void testNullConfigurationThrowsException() {
        // When & Then
        assertThatThrownBy(() -> new VaultDecryptingMaterialsProvider(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("VaultCryptoConfiguration cannot be null");
    }

    @Test
    void testInvalidVaultUrlThrowsException() {
        // When & Then
        assertThatThrownBy(() ->
                VaultCryptoConfiguration.builder().vaultUrl("invalid-url").vaultToken("test-token").build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Vault URL must start with http:// or https://");
    }

    @Test
    void testVaultTokenWithWhitespaceThrowsException() {
        // When & Then
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken(" test-token ")
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Vault token cannot contain leading or trailing whitespace");
    }

    @Test
    void testInvalidKeyPrefixThrowsException() {
        // When & Then
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .keyPrefix("invalid/prefix")
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Key prefix can only contain alphanumeric characters, underscores, and hyphens");
    }

    @Test
    void testExcessiveConnectionTimeoutThrowsException() {
        // When & Then
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .connectionTimeout(Duration.ofMinutes(10)) // Exceeds 5 minute limit
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Connection timeout cannot exceed 5 minutes");
    }

    @Test
    void testExcessiveRequestTimeoutThrowsException() {
        // When & Then
        assertThatThrownBy(() ->
                VaultCryptoConfiguration
                    .builder()
                    .vaultUrl("https://vault.example.com")
                    .vaultToken("test-token")
                    .requestTimeout(Duration.ofMinutes(15)) // Exceeds 10 minute limit
                    .build()
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Request timeout cannot exceed 10 minutes");
    }

    @Test
    void testValidConfigurationAccepted() {
        // Given
        var validConfig = VaultCryptoConfiguration
            .builder()
            .vaultUrl("https://vault.example.com")
            .vaultToken("valid-token")
            .keyPrefix("valid_prefix-123")
            .connectionTimeout(Duration.ofSeconds(30))
            .requestTimeout(Duration.ofMinutes(2))
            .build();

        // When & Then
        assertThatCode(() -> new VaultDecryptingMaterialsProvider(validConfig)).doesNotThrowAnyException();
    }

    @Test
    void testResourceLifecycle_tryWithResources() {
        // Test that the provider works properly with try-with-resources
        assertThatCode(() -> {
                try (var testProvider = new VaultDecryptingMaterialsProvider(config)) {
                    // Use the provider
                    assertThat(testProvider).isNotNull();
                }
                // close() should be called automatically
            })
            .doesNotThrowAnyException();
    }

    @Test
    void testClose_canBeCalledMultipleTimes() {
        // Given
        var testProvider = new VaultDecryptingMaterialsProvider(config);

        // When & Then - close() should be idempotent
        assertThatCode(() -> {
                testProvider.close();
                testProvider.close();
                testProvider.close();
            })
            .doesNotThrowAnyException();
    }

    /**
     * Creates a provider instance with the mocked VaultTransitClient.
     * This uses reflection to inject the mock since we want to test the provider logic
     * without actually creating a real VaultTransitClient.
     */
    private VaultDecryptingMaterialsProvider createProviderWithMockedClient() {
        var provider = new VaultDecryptingMaterialsProvider(config);

        // Use reflection to replace the vaultClient field with our mock
        try {
            var vaultClientField = VaultDecryptingMaterialsProvider.class.getDeclaredField("vaultClient");
            vaultClientField.setAccessible(true);
            vaultClientField.set(provider, mockVaultClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock VaultTransitClient", e);
        }

        return provider;
    }
}
