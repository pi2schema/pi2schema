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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalCryptoTest {

    static class RandomMultipleSizeStringArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(18, 24, 56, 120, 248, 1_116, 50_000)
                    .map(stringSize ->
                            Arguments.of(
                                    RandomStringUtils.random(stringSize, true, true)));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(RandomMultipleSizeStringArgumentsProvider.class)
    void encrypt(String text) throws ExecutionException, InterruptedException {

        //given
        var toBeEncrypted = StandardCharsets.UTF_8.encode(text).asReadOnlyBuffer();


        var secretKey = KeyGen.aes256().generateKey();

        EncryptingMaterialsProvider encProvider = s ->
                CompletableFuture.completedFuture(new SymmetricMaterial(secretKey));
        DecryptingMaterialsProvider decProvider = s ->
                CompletableFuture.completedFuture(new SymmetricMaterial(secretKey));

        var encryptor = new LocalEncryptor(encProvider);
        var decryptor = new LocalDecryptor(decProvider);

        //When
        var encrypted = encryptor.encrypt("", toBeEncrypted).get();
        var decrypted = decryptor.decrypt("", encrypted).get();

        //Then
        assertThat(toBeEncrypted.rewind()).isNotEqualTo(encrypted.data());
        String decryptedText = StandardCharsets.UTF_8.decode(decrypted).toString();

        assertEquals(text, decryptedText);

        assertThat(text).isEqualTo(decryptedText);
    }
}