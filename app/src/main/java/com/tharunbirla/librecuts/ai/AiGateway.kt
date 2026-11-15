package com.tharunbirla.librecuts.ai

import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToLong

enum class AiProvider { GROQ, NVIDIA, ZAI }
internal class GatewayException(message: String) : Exception(message)

// Offline converter retained for future work. No upload entry point is enabled.
internal object SubtitleSrt {
    fun fromGroqJson(body: String): String {
        val segments = JSONObject(body).optJSONArray("segments") ?: throw GatewayException("A resposta não contém segmentos com tempo.")
        val result = StringBuilder()
        var index = 0
        for (i in 0 until segments.length()) {
            val item = segments.getJSONObject(i)
            val start = item.getDouble("start")
            val end = item.getDouble("end")
            val text = item.getString("text").trim().replace(Regex("[\\r\\n]+"), " ")
            if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start || end > 360000 || text.isEmpty())
                throw GatewayException("A resposta contém um segmento inválido.")
            index++
            result.append(index).append('\n').append(time(start)).append(" --> ").append(time(end))
                .append('\n').append(text).append("\n\n")
        }
        if (index == 0) throw GatewayException("Nenhuma fala foi encontrada.")
        return result.toString()
    }

    private fun time(seconds: Double): String {
        val ms = (seconds * 1000).roundToLong()
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)
    }
}
