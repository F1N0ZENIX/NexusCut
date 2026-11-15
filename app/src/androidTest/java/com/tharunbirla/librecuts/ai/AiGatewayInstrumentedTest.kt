package com.tharunbirla.librecuts.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AiGatewayInstrumentedTest {
    // Use the test APK context, not the user's application files.
    private val context get() = InstrumentationRegistry.getInstrumentation().context

    @Test fun vaultWritesCiphertextReplacesAndDeletes() {
        val vault = KeyVault(context)
        val provider = AiProvider.GROQ
        try {
            vault.save(provider, "test-only-key-one")
            assertEquals("test-only-key-one", vault.read(provider))
            val bytes = File(context.noBackupFilesDir, "ai-credentials/GROQ.bin").readBytes()
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains("test-only-key-one"))
            vault.save(provider, "test-only-key-two")
            assertEquals("test-only-key-two", vault.read(provider))
            vault.delete(provider)
            assertNull(vault.read(provider))
        } finally { vault.delete(provider) }
    }

    @Test fun legacyBackupIsVisibleAndRecoveredByAtomicFile() {
        val vault = KeyVault(context)
        val path = File(context.noBackupFilesDir, "ai-credentials/GROQ.bin")
        val backup = File(path.path + ".bak")
        try {
            vault.delete(AiProvider.GROQ)
            vault.save(AiProvider.GROQ, "backup-test-key")
            assertTrue(path.renameTo(backup))
            assertFalse(path.exists())
            assertTrue(vault.exists(AiProvider.GROQ))
            assertEquals("backup-test-key", vault.read(AiProvider.GROQ))
            assertTrue(path.exists())
            assertFalse(backup.exists())
        } finally { vault.delete(AiProvider.GROQ) }
    }

    @Test fun deletingBackupAndNewArtifactsRemovesThem() {
        val vault = KeyVault(context)
        val path = File(context.noBackupFilesDir, "ai-credentials/NVIDIA.bin")
        try {
            vault.delete(AiProvider.NVIDIA)
            assertTrue(path.parentFile!!.isDirectory || path.parentFile!!.mkdirs())
            val backup = File(path.path + ".bak")
            val new = File(path.path + ".new")
            backup.writeText("test-only-ciphertext")
            new.writeText("test-only-ciphertext")
            assertTrue(vault.exists(AiProvider.NVIDIA))
            vault.delete(AiProvider.NVIDIA)
            assertFalse(backup.exists())
            assertFalse(new.exists())
        } finally { vault.delete(AiProvider.NVIDIA) }
    }

    @Test fun credentialArtifactsBlockReplacementKey() {
        for (name in listOf("GROQ.bin", "GROQ.bin.bak", "GROQ.bin.new")) {
            assertTrue(isCredentialArtifact(name))
        }
        assertFalse(isCredentialArtifact("other.txt"))
    }

    @Test fun startupCleanupDeletesOnlyLegacyUploadCopies() {
        val dir = createTempDir(prefix = "cleanup-test-", directory = context.cacheDir)
        try {
            val stale = File(dir, "ai-upload-test.tmp").apply { writeText("test-only") }
            val unrelated = File(dir, "other.tmp").apply { writeText("keep") }
            val wrongSuffix = File(dir, "ai-upload-test.txt").apply { writeText("keep") }
            val nested = File(dir, "ai-upload-dir.tmp").apply { mkdir() }
            val nestedFile = File(nested, "keep").apply { writeText("keep") }
            cleanupLegacyUploads(dir)
            assertFalse(stale.exists())
            assertTrue(unrelated.exists())
            assertTrue(wrongSuffix.exists())
            assertTrue(nestedFile.exists())
            cleanupLegacyUploads(dir)
        } finally { dir.deleteRecursively() }
    }

    @Test fun formatsSegmentsIntoSrtAndRejectsMissingOrInvalidTimes() {
        val json = """{"segments":[{"start":0.125,"end":1.55,"text":"Olá"},{"start":61.0,"end":62.5,"text":"mundo"}]}"""
        assertEquals("1\n00:00:00,125 --> 00:00:01,550\nOlá\n\n2\n00:01:01,000 --> 00:01:02,500\nmundo\n\n", SubtitleSrt.fromGroqJson(json))
        for (bad in listOf("{}", "{\"segments\":[]}", "{\"segments\":[{\"start\":1,\"end\":0,\"text\":\"x\"}]}")) {
            try { SubtitleSrt.fromGroqJson(bad); fail("invalid response") }
            catch (_: GatewayException) { }
        }
    }
}
