package com.tharunbirla.librecuts.ai

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import nvidia.ai4m.bnr.v1.BNRGrpc
import nvidia.ai4m.bnr.v1.Bnr
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Preview endpoint only: default TLS trust, no custom server or plaintext option. */
internal class NvidiaBnrTransport : BnrTransport {
    @Volatile private var channel: ManagedChannel? = null
    override fun cancel() { channel?.shutdownNow() }

    override fun enhance(input: ByteArray, key: String, cancelled: AtomicBoolean): ByteArray {
        val ch = OkHttpChannelBuilder.forAddress("grpc.nvcf.nvidia.com", 443)
            .useTransportSecurity().build()
        channel = ch
        try {
            if (cancelled.get()) throw CancellationException()
            val headers = Metadata().apply {
                put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $key")
                put(Metadata.Key.of("function-id", Metadata.ASCII_STRING_MARSHALLER), "c95bfe76-c553-4975-bc18-8ef9d6e2666b")
            }
            val done = CountDownLatch(1)
            val output = ByteArrayOutputStream()
            var failed = false
            val observer = object : StreamObserver<Bnr.EnhanceAudioResponse> {
                override fun onNext(value: Bnr.EnhanceAudioResponse) {
                    if (value.hasAudioStreamData()) {
                        val part = value.audioStreamData
                        if (output.size().toLong() + part.size() > BnrWav.LIMIT) {
                            failed = true
                            ch.shutdownNow()
                        } else part.writeTo(output)
                    }
                }
                override fun onError(t: Throwable) { failed = true; done.countDown() }
                override fun onCompleted() { done.countDown() }
            }
            val stub = BNRGrpc.newStub(ch).withDeadlineAfter(180, TimeUnit.SECONDS)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
            val requests = stub.enhanceAudio(observer)
            var offset = 0
            while (offset < input.size) {
                if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException()
                val end = minOf(offset + 64 * 1024, input.size)
                requests.onNext(Bnr.EnhanceAudioRequest.newBuilder()
                    .setAudioStreamData(ByteString.copyFrom(input, offset, end - offset)).build())
                offset = end
            }
            requests.onCompleted()
            while (!done.await(200, TimeUnit.MILLISECONDS)) {
                if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException()
            }
            if (cancelled.get()) throw CancellationException()
            check(!failed) { "Falha no serviço NVIDIA ou na conexão" }
            return output.toByteArray()
        } finally {
            ch.shutdownNow()
            channel = null
        }
    }
}
