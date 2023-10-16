package pi2schema.crypto.providers.tink

import com.amazonaws.services.kms.AWSKMS
import com.google.crypto.tink.*
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.awskms.AwsKmsAead
import java.io.File
import java.util.concurrent.CompletableFuture
import com.google.crypto.tink.aead.AeadConfig


class TinkKeyStore(kmsClient: AWSKMS, masterKeyUri: String) {
    private val keyStore = mutableMapOf<String, KeysetHandle>()
    val aead: Aead = AwsKmsAead(kmsClient, masterKeyUri)

    init {
        AeadConfig.register()
        keyStore.put("josi", createKeyFor(aead, "josi"))
        keyStore.put("gustavo", createKeyFor(aead, "gustavo"))
    }

    // Following second sample on https://github.com/google/tink/blob/master/docs/JAVA-HOWTO.md#storing-keysets
    private fun createKeyFor(aead: Aead, subjectId: String): KeysetHandle {
        val keysetFilename = "$subjectId.json"
        val keysetHandle = KeysetHandle.generateNew(
            AeadKeyTemplates.AES128_GCM
        )
        keysetHandle.write(JsonKeysetWriter.withFile(File(keysetFilename)), aead)
        return keysetHandle
    }


    fun encryptionKeysFor(subjectId: String): CompletableFuture<KeysetHandle> {
        return CompletableFuture.completedFuture(
            keyStore.getOrPut(subjectId) { createKeyFor(aead, subjectId) }
        )
    }

    fun decryptionKeysFor(subjectId: String): CompletableFuture<KeysetHandle> {
        return CompletableFuture.completedFuture(keyStore.get(subjectId))
    }

}

