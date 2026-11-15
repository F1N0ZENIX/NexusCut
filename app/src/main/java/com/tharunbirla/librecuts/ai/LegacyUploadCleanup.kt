package com.tharunbirla.librecuts.ai

import java.io.File

/** Only removes the previous gateway's temporary copies. No paths or content are logged. */
internal fun cleanupLegacyUploads(cacheDir: File) {
    try {
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("ai-upload-") && file.name.endsWith(".tmp") && file.isFile) {
                file.delete() // Best effort; retried at the next process start.
            }
        }
    } catch (_: SecurityException) {
        // Do not expose file paths or content through the global crash logger.
    }
}
