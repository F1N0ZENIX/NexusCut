package com.tharunbirla.librecuts.ai

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Narrow transactional-preview contract. This is not the editor timeline. */
internal data class WavInfo(val rate: Int, val frames: Long)
internal object BnrWav {
    // Temporary in-memory prototype cap (including WAV header), not a streaming solution.
    const val LIMIT = 512 * 1024
    fun read(input: InputStream, cancelled: AtomicBoolean): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException()
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            require(out.size().toLong() + n <= LIMIT) { "WAV maior que 512 KiB (limite temporário)" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
    fun validate(b: ByteArray): WavInfo {
        fun u16(p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
        fun u32(p: Int): Long = (u16(p).toLong() or (u16(p + 2).toLong() shl 16))
        fun id(p: Int, s: String) = s.indices.all { b[p + it].toInt() == s[it].code }
        require(b.size in 44..LIMIT && id(0, "RIFF") && id(8, "WAVE") && u32(4) + 8 == b.size.toLong()) { "WAV inválido" }
        var pos = 12
        var rate = 0
        var frames = -1L
        var foundFmt = false
        while (pos + 8 <= b.size) {
            val size = u32(pos + 4)
            val start = pos + 8
            val end = start.toLong() + size
            val next = end + (size and 1)
            require(end <= b.size && next <= b.size) { "WAV truncado" }
            if (id(pos, "fmt ")) {
                require(!foundFmt && size >= 16 && u16(start) == 1 && u16(start + 2) == 1 && u16(start + 14) == 16) { "É necessário PCM 16-bit mono" }
                val r = u32(start + 4)
                require(r == 16000L || r == 48000L) { "Use WAV de 16 kHz ou 48 kHz" }
                require(u16(start + 12) == 2 && u32(start + 8) == r * 2) { "Cabeçalho WAV inválido" }
                rate = r.toInt()
                foundFmt = true
            } else if (id(pos, "data")) {
                require(frames < 0 && size > 0 && size % 2 == 0L) { "Dados WAV inválidos" }
                frames = size / 2
            }
            pos = next.toInt()
        }
        require(pos == b.size && foundFmt && frames > 0 && frames <= rate.toLong() * 360) { "WAV inválido ou acima de 6 minutos" }
        return WavInfo(rate, frames)
    }
}

/** Linearizes cancellation vs. publication. A completed operation is no longer cancellable. */
internal class BnrCompletionGate {
    val cancelled = AtomicBoolean(false)
    private var finished = false
    @Synchronized fun cancel(): Boolean {
        if (finished) return false
        cancelled.set(true)
        return true
    }
    @Synchronized fun finish(): Boolean {
        if (finished || cancelled.get()) return false
        finished = true
        return true
    }
}

internal interface BnrTransport {
    fun enhance(input: ByteArray, key: String, cancelled: AtomicBoolean): ByteArray
    fun cancel()
}

/** Enforce both input and output, irrespective of transport implementation. */
internal class BnrProcessor(private val transport: BnrTransport) {
    fun run(input: ByteArray, key: String, cancelled: AtomicBoolean): ByteArray {
        val expected = BnrWav.validate(input)
        if (cancelled.get()) throw CancellationException()
        val result = transport.enhance(input, key, cancelled)
        if (cancelled.get()) throw CancellationException()
        require(BnrWav.validate(result) == expected) { "A saída não preserva duração e taxa do original" }
        return result
    }
}
