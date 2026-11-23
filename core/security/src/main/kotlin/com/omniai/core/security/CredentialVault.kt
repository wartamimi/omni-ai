package com.omniai.core.security

import android.content.Context
import com.omniai.core.common.Dispatcher
import com.omniai.core.common.OmniDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted secret storage. Ciphertext files live in `noBackupFilesDir`, which Android excludes from
 * cloud backup and device transfer (the Keystore key could not be restored anyway). Plaintext is
 * never written to disk, Room or DataStore.
 */
@Singleton
class CredentialVault @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: KeystoreCipher,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val dir = File(context.noBackupFilesDir, "credentials").apply { mkdirs() }

    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid credential id" }
        return File(dir, "$id.bin")
    }

    suspend fun write(id: String, secret: String) = withContext(io) {
        val blob = cipher.encrypt(secret.trim().toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "$id.tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(file(id))) {
            file(id).writeBytes(blob)
            tmp.delete()
        }
    }

    suspend fun read(id: String): String? = withContext(io) {
        val f = file(id)
        if (!f.exists()) return@withContext null
        runCatching { String(cipher.decrypt(f.readBytes()), Charsets.UTF_8) }.getOrNull()
    }

    suspend fun delete(id: String) = withContext(io) { file(id).delete(); Unit }

    suspend fun deleteAll() = withContext(io) {
        dir.listFiles()?.forEach { it.delete() }
        cipher.destroyKey()
    }
}
