//package pi2schema.crypto.providers.kafkakms;
//
//import com.google.protobuf.ByteString;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import pi2schema.crypto.materials.MissingCryptoMaterialsException;
//import pi2schema.kms.KafkaProvider;
//import pi2schema.kms.KafkaProvider.SubjectCryptographicMaterialAggregate;
//
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.CompletionException;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class MostRecentMaterialsProviderTest {
//
//    @Mock
//    private KafkaSecretKeyStore kafkaSecretKeyStore;
//
//    @InjectMocks
//    private MostRecentMaterialsProvider materialsProvider;
//
//
//    @Test
//    void decryptionKeysForSubjectNotFoundShouldThrowException() {
//        //simulate a missing key
//        when(kafkaSecretKeyStore.existentMaterialsFor("subjectX"))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        var expected = assertThrows(CompletionException.class, () ->
//                materialsProvider.decryptionKeysFor("subjectX").join());
//
//        assertThat(expected.getCause()).isInstanceOf(MissingCryptoMaterialsException.class);
//
//        assertThat(expected).hasMessageContaining("subjectX");
//    }
//
//    @Test
//    void decryptionKeys() {
//        var materials = SubjectCryptographicMaterialAggregate.newBuilder()
//                .addMaterials(KafkaProvider.SubjectCryptographicMaterial.newBuilder()
//                        .setAlgorithm("AES")
//                        .setId("1")
//                        .setSymmetricKey(ByteString.copyFrom("aKeyValue".getBytes()))
//                        .build())
//                .build();
//        when(kafkaSecretKeyStore.existentMaterialsFor("existentSubject"))
//                .thenReturn(CompletableFuture.completedFuture(materials));
//
//        var existentSubjectKeys = materialsProvider.decryptionKeysFor("existentSubject").join();
//
//        assertThat(existentSubjectKeys).isNotNull();
//    }
//}