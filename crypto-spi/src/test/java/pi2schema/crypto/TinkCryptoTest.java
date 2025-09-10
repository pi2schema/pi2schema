package pi2schema.crypto;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadFactory;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import pi2schema.crypto.tink.TinkDecryptor;
import pi2schema.crypto.tink.TinkEncryptor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TinkCryptoTest {

    @BeforeAll
    static void setUp() throws GeneralSecurityException {
        AeadConfig.register();
    }

    static class RandomMultipleSizeStringArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream
                .of(18, 24, 56, 120, 248, 1_116, 50_000)
                .map(stringSize -> Arguments.of(RandomStringUtils.random(stringSize, true, true)));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(RandomMultipleSizeStringArgumentsProvider.class)
    void encrypt(String text) throws GeneralSecurityException, ExecutionException, InterruptedException {
        //given
        var toBeEncrypted = StandardCharsets.UTF_8.encode(text).asReadOnlyBuffer();

        var keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM);
        Aead aead = AeadFactory.getPrimitive(keysetHandle);
        var subjectId = "test-subject";

        var encryptor = new TinkEncryptor(aead);
        var decryptor = new TinkDecryptor(aead, subjectId);

        //When
        var encrypted = encryptor.encrypt(subjectId, toBeEncrypted).get();
        var decrypted = decryptor.decrypt(encrypted).get();

        //Then
        assertThat(toBeEncrypted.rewind()).isNotEqualTo(encrypted.data());
        String decryptedText = StandardCharsets.UTF_8.decode(decrypted).toString();

        assertEquals(text, decryptedText);

        assertThat(text).isEqualTo(decryptedText);
    }
}
