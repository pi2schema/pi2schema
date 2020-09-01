package pi2schema.crypto.providers.kafkakms;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pi2schema.crypto.materials.DecryptingMaterial;
import pi2schema.crypto.materials.DecryptingMaterialNotFoundException;
import pi2schema.kms.KafkaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MostRecentMaterialsProviderTest {

    @Mock
    private KafkaSecretKeyStore kafkaSecretKeyStore;

    @InjectMocks
    private MostRecentMaterialsProvider materialsProvider;


    @Test
    void decryptionKeysForSubjectNotFoundShouldThrowException() {

        DecryptingMaterialNotFoundException expected = assertThrows(DecryptingMaterialNotFoundException.class, () ->
                materialsProvider.decryptionKeysFor("subjectX"));

        assertThat(expected).hasMessageContaining("subjectX");
    }

    @Test
    void decryptionKeys() {

        when(kafkaSecretKeyStore.existentMaterialsFor("existentSubject"))
                .thenReturn(KafkaProvider.SubjectCryptographicMaterialAggregate.newBuilder()
                        .addMaterials(KafkaProvider.SubjectCryptographicMaterial.newBuilder()
                                .setAlgorithm("AES")
                                .setVersion(1)
                                .setSymmetricKey(ByteString.copyFrom("aKeyValue".getBytes()))
                                .build())
                        .build());

        DecryptingMaterial existentSubjectKeys = materialsProvider.decryptionKeysFor("existentSubject");

        assertThat(existentSubjectKeys).isNotNull();
    }
}