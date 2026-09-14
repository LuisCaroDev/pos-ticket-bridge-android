package com.luiscarodev.posticketbridge.bridge.https

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json

interface HttpsStore {
    fun read(): HttpsRecord
    fun write(record: HttpsRecord)
}

/** Only ciphertext is persisted; the wrapping key is non-exportable and excluded from backup. */
class AndroidHttpsStore(context: Context, private val name: String = "local-https") : HttpsStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "$name.enc"))
    private val alias = "pos-ticket-bridge-https-mobile-$name"
    private val json = Json { encodeDefaults = true }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "https_decryption_failed" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun read(): HttpsRecord {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return HttpsRecord()
        try {
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size > 28 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            val record = json.decodeFromString<HttpsRecord>(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).decodeToString())
            require(record.version == 1 && (!record.enabled || (record.selection != null && record.material != null)))
            return record
        } catch (_: Exception) { error("https_store_invalid") }
    }

    override fun write(record: HttpsRecord) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.encodeToString(record).toByteArray())
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}
