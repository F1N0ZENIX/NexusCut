package com.tharunbirla.librecuts.ai

// All AtomicFile variants must block silently generating a replacement master key.
internal fun isCredentialArtifact(name: String): Boolean =
    name.endsWith(".bin") || name.endsWith(".bin.bak") || name.endsWith(".bin.new")
