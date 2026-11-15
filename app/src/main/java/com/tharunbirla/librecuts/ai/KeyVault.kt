package com.tharunbirla.librecuts.ai

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

/** Only ciphertext is stored on disk; noBackupFilesDir is excluded from Android backup. */
internal class KeyVault(context: Context) {
    private val directory = File(context.noBackupFilesDir, "ai-credentials")
    private val alias = "nexuscut.ai.keys.v1"
    private val store: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun masterKey(create: Boolean): SecretKey? {
        val ks = store
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        // Never silently create a replacement master key over unrecoverable ciphertext.
        check(checkNotNull(directory.listFiles()).none { isCredentialArtifact(it.name) })
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    private fun file(provider: AiProvider) = File(directory, provider.name + ".bin")
    // Android 8 AtomicFile uses .bak; openRead restores it even when the base is missing.
    @Synchronized fun exists(provider: AiProvider): Boolean {
        val path = file(provider)
        return path.exists() || File(path.path + ".bak").exists()
    }

    @Synchronized fun save(provider: AiProvider, value: String) {
        require(value.isNotEmpty() && value.length <= 4096 && value.all { it.code in 33..126 })
        check(directory.isDirectory || directory.mkdirs())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, requireNotNull(masterKey(true)))
        cipher.updateAAD(provider.name.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val atomic = AtomicFile(file(provider))
        val out = atomic.startWrite()
        try {
            out.write(cipher.iv.size)
            out.write(cipher.iv)
            out.write(encrypted)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    @Synchronized fun read(provider: AiProvider): String? {
        val path = file(provider)
        if (!exists(provider)) return null
        val bytes = AtomicFile(path).readFully()
        require(bytes.size in 30..8192)
        val ivSize = bytes[0].toInt() and 255
        require(ivSize == 12 && bytes.size > 1 + ivSize + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, requireNotNull(masterKey(false)), GCMParameterSpec(128, bytes, 1, ivSize))
        cipher.updateAAD(provider.name.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize), Charsets.UTF_8)
    }

    @Synchronized fun delete(provider: AiProvider) {
        val path = file(provider)
        AtomicFile(path).delete()
        // Delete both AtomicFile implementation variants, including on older Android.
        File(path.path + ".bak").delete()
        File(path.path + ".new").delete()
        check(!path.exists() && !File(path.path + ".bak").exists() && !File(path.path + ".new").exists())
    }
}
