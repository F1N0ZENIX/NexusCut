package io.github.artcraftandroid.capcraft.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ToolSearchTextTest {
    private fun matches(label: String, query: String): Boolean =
        normalizeToolSearch(label).contains(normalizeToolSearch(query.trim()))

    @Test fun matchesPortugueseWithoutAccentsAndCase() {
        assertTrue(matches("Áudio", "audio"))
        assertTrue(matches("Transcrição", "TRANSCRICAO"))
        assertTrue(matches("Efeitos de vídeo", " VIDEO "))
        assertTrue(matches("Reduzir ruído", "ruído"))
        assertTrue(matches("Legendas automáticas", "AUTOMATICAS"))
        assertFalse(matches("Áudio", "video"))
    }

    @Test fun matchesBothUnicodeRepresentationsAndBothDirections() {
        assertTrue(matches("Mascara", "Máscara"))
        assertTrue(matches("Marção", "marcao"))
        assertTrue(matches("Marcação", "çã"))
        assertTrue(matches("Flash", "flash"))
    }

    @Test fun independentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(matches("INTENSIDADE", "intensidade"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
