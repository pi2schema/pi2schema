package pi2schema.crypto;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import pi2schema.crypto.materials.SymmetricMaterial;
import pi2schema.crypto.providers.DecryptingMaterialsProvider;
import pi2schema.crypto.providers.EncryptingMaterialsProvider;
import pi2schema.crypto.support.KeyGen;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCryptoTest {

    static class RandomMultipleSizeStringArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(
                    Arguments.of(RandomStringUtils.random(8)),
                    Arguments.of(RandomStringUtils.random(24)),
                    Arguments.of(RandomStringUtils.random(56)),
                    Arguments.of(RandomStringUtils.random(120)),
                    Arguments.of(RandomStringUtils.random(248)),
                    Arguments.of(RandomStringUtils.random(1_116)),
                    Arguments.of(RandomStringUtils.random(50_000))
            );
        }

    }

    @ParameterizedTest
    @ArgumentsSource(RandomMultipleSizeStringArgumentsProvider.class)
    void encrypt(String text) throws NoSuchAlgorithmException, ExecutionException, InterruptedException {

        //given
        byte[] toBeEncrypted = text.getBytes();

        SecretKey secretKey = KeyGen.aes256().generateKey();

        EncryptingMaterialsProvider encProvider = (s) ->
                CompletableFuture.completedFuture(new SymmetricMaterial(secretKey));
        DecryptingMaterialsProvider decProvider = (s) ->
                CompletableFuture.completedFuture(new SymmetricMaterial(secretKey));

        Encryptor encryptor = new LocalEncryptor(encProvider);
        Decryptor decryptor = new LocalDecryptor(decProvider);

        //When
        EncryptedData encrypted = encryptor.encrypt("", toBeEncrypted).get();
        byte[] decrypted = decryptor.decrypt("", encrypted).get();

        //Then
        assertThat(toBeEncrypted).isNotEqualTo(encrypted.data());

        assertThat(decrypted).isEqualTo(toBeEncrypted);
    }
}