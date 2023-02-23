package pi2schema.crypto.providers.tink

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.kms.AWSKMSClient
import com.google.crypto.tink.Aead
import org.assertj.core.api.Assertions.assertThat
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test


@Testcontainers
internal class TinkKeyStoreTest {

    var localstackImage: DockerImageName = DockerImageName.parse("localstack/localstack:0.11.3")

    @Container
    var localstack: LocalStackContainer = LocalStackContainer(localstackImage)
        .withServices(Service.KMS).also { it.start() }

    val kmsClient = AWSKMSClient.builder()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstack.getEndpointOverride(Service.KMS).toString(), localstack.region))
        .withCredentials(
            AWSStaticCredentialsProvider(
                BasicAWSCredentials(localstack.accessKey, localstack.secretKey)
            )
        )
        .build()

    @Test
    fun testKeyCreation() {
        val createKey = kmsClient.createKey()
        val tinkKeyStore = TinkKeyStore(kmsClient, createKey.keyMetadata.arn)

        val encryptionKeys = tinkKeyStore.encryptionKeysFor("josi")
        assertThat(encryptionKeys).isNotNull
        var aead: Aead = encryptionKeys.get().getPrimitive(Aead::class.java)
        val aad = "josi".toByteArray()
        val ciphertext = aead.encrypt("this is a test".toByteArray(), aad)

        val decryptionKeysFor = tinkKeyStore.decryptionKeysFor("josi")
        assertThat(decryptionKeysFor).isNotNull
        aead = decryptionKeysFor.get().getPrimitive(Aead::class.java)
        val decrypt = aead.decrypt(ciphertext, aad)
        assertThat(String(decrypt)).isEqualTo("this is a test")
    }
}
