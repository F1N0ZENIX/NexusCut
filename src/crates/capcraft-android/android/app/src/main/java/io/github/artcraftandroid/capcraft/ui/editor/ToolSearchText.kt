package io.github.artcraftandroid.capcraft.ui.editor

import java.text.Normalizer
import java.util.Locale

/** Comparison key only: never modify the displayed labels or package names. */
internal fun normalizeToolSearch(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
