package pi2schema.crypto.providers.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
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
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var mockEncryptedDek = "encrypted-dek-data".getBytes();

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), isNull()))
            .thenReturn(CompletableFuture.completedFuture(mockEncryptedDek));

        provider = createProviderWithMockedClient();

        // When
        var future = provider.encryptionKeysFor(subjectId);
        var result = future.join();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.dataEncryptionKey()).isNotNull();
        assertThat(result.encryptedDataKey()).isEqualTo(mockEncryptedDek);
        assertThat(result.encryptionContext()).isNull(); // Encryption context removed from MVP

        // Verify DEK is functional
        var aead = result.dataEncryptionKey();
        assertThat(aead).isNotNull();

        // Verify Vault client interactions
        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).encrypt(eq(expectedKeyName), any(byte[].class), isNull());
    }

    @Test
    void testNullSubjectIdThrowsException() {
        // Given
        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.encryptionKeysFor(null);

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
        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.encryptionKeysFor("   ");

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Subject ID cannot be null or empty");

        verifyNoInteractions(mockVaultClient);
    }

    @Test
    void testVaultConnectivityFailure() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var vaultException = new VaultConnectivityException("Vault is unreachable");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), isNull()))
            .thenReturn(CompletableFuture.failedFuture(vaultException));

        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.encryptionKeysFor(subjectId);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(VaultConnectivityException.class)
            .hasMessage("Vault is unreachable");

        verify(mockVaultClient).generateKeyName(subjectId);
        verify(mockVaultClient).encrypt(eq(expectedKeyName), any(byte[].class), isNull());
    }

    @Test
    void testVaultAuthenticationFailure() {
        // Given
        var subjectId = "user-12345";
        var expectedKeyName = "pi2schema/subject/user-12345";
        var authException = new VaultAuthenticationException("Invalid token");

        when(mockVaultClient.generateKeyName(subjectId)).thenReturn(expectedKeyName);
        when(mockVaultClient.encrypt(eq(expectedKeyName), any(byte[].class), isNull()))
            .thenReturn(CompletableFuture.failedFuture(authException));

        provider = createProviderWithMockedClient();

        // When & Then
        var future = provider.encryptionKeysFor(subjectId);

        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .cause()
            .isInstanceOf(VaultAuthenticationException.class)
            .hasMessage("Invalid token");
    }

    @Test
    void testConcurrentOperations() throws InterruptedException {
        // Given
        var numberOfThreads = 10;
        var operationsPerThread = 5;
        var executor = Executors.newFixedThreadPool(numberOfThreads);
        var latch = new CountDownLatch(numberOfThreads * operationsPerThread);
        var successCount = new AtomicInteger(0);
        var firstException = new AtomicReference<Exception>();

        // Mock successful responses for all operations
        when(mockVaultClient.generateKeyName(anyString()))
            .thenAnswer(invocation -> "pi2schema/subject/" + invocation.getArgument(0));
        when(mockVaultClient.encrypt(anyString(), any(byte[].class), isNull()))
            .thenReturn(CompletableFuture.completedFuture("encrypted-data".getBytes()));

        provider = createProviderWithMockedClient();

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        var subjectId = "user-" + threadId + "-" + j;
                        var future = provider.encryptionKeysFor(subjectId);
                        var result = future.get(5, TimeUnit.SECONDS);

                        assertThat(result).isNotNull();
                        assertThat(result.dataEncryptionKey()).isNotNull();
                        assertThat(result.encryptedDataKey()).isNotNull();
                        assertThat(result.encryptionContext()).isNull(); // Encryption context removed from MVP

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
            .encrypt(anyString(), any(byte[].class), isNull());

        executor.shutdown();
    }

    // testEncryptionContextFormat removed - encryption context not used in MVP

    @Test
    void testDifferentSubjectsGetDifferentKeys() {
        // Given
        var subjectId1 = "user-1";
        var subjectId2 = "user-2";
        var encryptedDek1 = "encrypted-dek-1".getBytes();
        var encryptedDek2 = "encrypted-dek-2".getBytes();

        when(mockVaultClient.generateKeyName("user-1")).thenReturn("pi2schema/subject/user-1");
        when(mockVaultClient.generateKeyName("user-2")).thenReturn("pi2schema/subject/user-2");
        when(mockVaultClient.encrypt(eq("pi2schema/subject/user-1"), any(byte[].class), isNull()))
            .thenReturn(CompletableFuture.completedFuture(encryptedDek1));
        when(mockVaultClient.encrypt(eq("pi2schema/subject/user-2"), any(byte[].class), isNull()))
            .thenReturn(CompletableFuture.completedFuture(encryptedDek2));

        provider = createProviderWithMockedClient();

        // When
        var result1 = provider.encryptionKeysFor(subjectId1).join();
        var result2 = provider.encryptionKeysFor(subjectId2).join();

        // Then
        assertThat(result1.dataEncryptionKey()).isNotEqualTo(result2.dataEncryptionKey());
        assertThat(result1.encryptedDataKey()).isNotEqualTo(result2.encryptedDataKey());
        // Encryption context comparison removed - both are null in MVP

        // Verify different vault keys were used
        verify(mockVaultClient).encrypt(eq("pi2schema/subject/user-1"), any(byte[].class), isNull());
        verify(mockVaultClient).encrypt(eq("pi2schema/subject/user-2"), any(byte[].class), isNull());
    }

    @Test
    void testProviderClose() {
        // Given
        provider = createProviderWithMockedClient();

        // When
        assertThatCode(provider::close).doesNotThrowAnyException();

        // Then
        verify(mockVaultClient).close();
    }

    @Test
    void testProviderCloseWithException() {
        // Given
        doThrow(new RuntimeException("Close failed")).when(mockVaultClient).close();
        provider = createProviderWithMockedClient();

        // When & Then
        assertThatCode(provider::close).doesNotThrowAnyException(); // Should not propagate exception

        verify(mockVaultClient).close();
    }

    @Test
    void testNullConfigurationThrowsException() {
        // When & Then
        assertThatThrownBy(() -> new VaultEncryptingMaterialsProvider(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Configuration cannot be null");
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
        assertThatCode(() -> new VaultEncryptingMaterialsProvider(validConfig)).doesNotThrowAnyException();
    }

    @Test
    void testResourceLifecycle_tryWithResources() {
        // Test that the provider works properly with try-with-resources
        assertThatCode(() -> {
                try (var testProvider = new VaultEncryptingMaterialsProvider(config)) {
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
        var testProvider = new VaultEncryptingMaterialsProvider(config);

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
    private VaultEncryptingMaterialsProvider createProviderWithMockedClient() {
        var provider = new VaultEncryptingMaterialsProvider(config);

        // Use reflection to replace the vaultClient field with our mock
        try {
            var vaultClientField = VaultEncryptingMaterialsProvider.class.getDeclaredField("vaultClient");
            vaultClientField.setAccessible(true);
            vaultClientField.set(provider, mockVaultClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock VaultTransitClient", e);
        }

        return provider;
    }
}
