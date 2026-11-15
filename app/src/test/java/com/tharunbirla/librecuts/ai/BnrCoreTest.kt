package com.tharunbirla.librecuts.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class BnrCoreTest {
    private fun wav(frames: Int = 160, rate: Int = 16000): ByteArray {
        val b = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + frames * 2); b.put("WAVEfmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(rate); b.putInt(rate * 2)
        b.putShort(2); b.putShort(16); b.put("data".toByteArray()); b.putInt(frames * 2)
        return b.array()
    }
    private class Fake(private val response: (ByteArray) -> ByteArray) : BnrTransport {
        var calls = 0
        override fun enhance(input: ByteArray, key: String, cancelled: AtomicBoolean): ByteArray {
            calls++; return response(input)
        }
        override fun cancel() = Unit
    }
    @Test fun fakeRoundTripPreservesBytesRateAndFrameCount() {
        val source = wav()
        val original = source.clone()
        val fake = Fake { it.clone() }
        val result = BnrProcessor(fake).run(source, "fake-only", AtomicBoolean())
        assertEquals(1, fake.calls); assertArrayEquals(original, source); assertArrayEquals(original, result)
        assertEquals(WavInfo(16000, 160), BnrWav.validate(result))
    }
    @Test fun rejectsMalformedInputBeforeTransport() {
        val fake = Fake { it }
        val bad = wav().apply { this[22] = 2 } // stereo
        try { BnrProcessor(fake).run(bad, "fake-only", AtomicBoolean()); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, fake.calls)
        try { BnrWav.validate(wav().copyOf(50)); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectsMismatchedAndOversizedOutput() {
        try { BnrProcessor(Fake { wav(159) }).run(wav(), "fake-only", AtomicBoolean()); fail() } catch (_: IllegalArgumentException) { }
        try { BnrProcessor(Fake { ByteArray(BnrWav.LIMIT + 1) }).run(wav(), "fake-only", AtomicBoolean()); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun acceptsExactLimitAndRejectsOneByteMore() {
        val atLimit = wav((BnrWav.LIMIT - 44) / 2)
        assertEquals(BnrWav.LIMIT, atLimit.size)
        assertEquals(atLimit.size, BnrWav.read(ByteArrayInputStream(atLimit), AtomicBoolean()).size)
        assertEquals(((BnrWav.LIMIT - 44) / 2).toLong(), BnrWav.validate(atLimit).frames)
        try { BnrWav.read(ByteArrayInputStream(ByteArray(BnrWav.LIMIT + 1)), AtomicBoolean()); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun cancellationWinsBeforePublication() {
        val gate = BnrCompletionGate()
        assertTrue(gate.cancel())
        assertTrue(gate.cancelled.get())
        assertFalse(gate.finish())
        assertFalse(gate.finish())
    }
    @Test fun completionWinsBeforeCancellation() {
        val gate = BnrCompletionGate()
        assertTrue(gate.finish())
        assertFalse(gate.cancel())
        assertFalse(gate.cancelled.get())
        assertFalse(gate.finish())
    }
    @Test fun cancellationAndCompletionAreMutuallyExclusive() {
        repeat(100) {
            val gate = BnrCompletionGate()
            val start = java.util.concurrent.CountDownLatch(1)
            val cancelWon = AtomicBoolean()
            val finishWon = AtomicBoolean()
            val a = Thread { start.await(); cancelWon.set(gate.cancel()) }
            val b = Thread { start.await(); finishWon.set(gate.finish()) }
            a.start(); b.start(); start.countDown(); a.join(); b.join()
            assertNotEquals(cancelWon.get(), finishWon.get())
        }
    }
    @Test fun cancelBeforeAndAfterTransport() {
        val cancelled = AtomicBoolean(true); val fake = Fake { it }
        try { BnrProcessor(fake).run(wav(), "fake-only", cancelled); fail() } catch (_: CancellationException) { }
        assertEquals(0, fake.calls)
        val later = AtomicBoolean(false)
        try { BnrProcessor(Fake { later.set(true); it }).run(wav(), "fake-only", later); fail() } catch (_: CancellationException) { }
    }
    @Test fun boundedReadAndCancel() {
        assertArrayEquals(wav(), BnrWav.read(ByteArrayInputStream(wav()), AtomicBoolean(false)))
        try { BnrWav.read(ByteArrayInputStream(wav()), AtomicBoolean(true)); fail() } catch (_: CancellationException) { }
        try { BnrWav.read(ByteArrayInputStream(ByteArray(BnrWav.LIMIT + 1)), AtomicBoolean(false)); fail() } catch (_: IllegalArgumentException) { }
    }
}
