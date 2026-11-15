package com.tharunbirla.librecuts.ai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GatewaySafetyUnitTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun cleanupTargetsOnlyOldGatewayFiles() {
        val cache = folder.newFolder("cache")
        val old = File(cache, "ai-upload-a.tmp").apply { writeText("test media") }
        val empty = File(cache, "ai-upload-b.tmp").apply { createNewFile() }
        val unrelated = File(cache, "other.tmp").apply { writeText("keep") }
        val otherSuffix = File(cache, "ai-upload-a.txt").apply { writeText("keep") }
        val directory = File(cache, "ai-upload-dir.tmp").apply { mkdir() }
        val nested = File(directory, "ai-upload-nested.tmp").apply { writeText("keep") }
        cleanupLegacyUploads(cache)
        assertFalse(old.exists())
        assertFalse(empty.exists())
        assertTrue(unrelated.exists())
        assertTrue(otherSuffix.exists())
        assertTrue(nested.exists())
        cleanupLegacyUploads(cache) // idempotent
        assertTrue(nested.exists())
    }

    @Test fun missingCacheDoesNotThrow() {
        cleanupLegacyUploads(File(folder.root, "absent"))
    }

    @Test fun allAtomicFileVariantsBlockKeyReplacement() {
        for (name in listOf("GROQ.bin", "GROQ.bin.bak", "GROQ.bin.new")) {
            assertTrue(name, isCredentialArtifact(name))
        }
        for (name in listOf("GROQ", "GROQ.bak", "GROQ.bin.bak.other", "other.txt")) {
            assertFalse(name, isCredentialArtifact(name))
        }
    }
}
