package com.tharunbirla.librecuts.ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated WAV preview experiment; never writes into a project or source URI. */
class BnrPrototypeActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val cancelled = AtomicBoolean(false)
    private var transport: NvidiaBnrTransport? = null
    private var running = false
    private var result: File? = null
    private lateinit var status: TextView
    private lateinit var select: Button
    private lateinit var stop: Button
    private lateinit var share: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        fun text(s: String) = TextView(this).apply { text = s; textSize = 16f; layout.addView(this) }
        fun button(s: String, f: () -> Unit) = Button(this).apply { text = s; setOnClickListener { f() }; layout.addView(this) }
        text("BNR NVIDIA — protótipo separado do editor. Apenas WAV PCM 16-bit mono, 16 ou 48 kHz, até 35 MB e 6 minutos. Sem conversão automática.")
        status = text("Selecione um WAV de teste. Cadastre a chave NVIDIA no gateway antes de enviar.")
        select = button("Selecionar WAV") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            try { startActivityForResult(intent, 401) } catch (_: Exception) { status.text = "Não foi possível abrir o seletor." }
        }
        stop = button("Cancelar operação") { cancelOperation(); status.text = "Cancelando… bytes já enviados não podem ser recolhidos." }
        share = button("Compartilhar resultado") { shareResult() }
        val saved = savedInstanceState?.getString("result")
        if (saved != null && saved.matches(Regex("bnr-[A-Za-z0-9-]+\\.wav"))) {
            val f = File(File(cacheDir, "bnr-results"), saved)
            if (f.isFile) result = f
        }
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        result?.let { outState.putString("result", it.name) }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Android activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 401 || resultCode != Activity.RESULT_OK || running) return
        val uri = data?.data ?: return
        AlertDialog.Builder(this).setTitle("Enviar áudio para NVIDIA?")
            .setMessage("O WAV selecionado inteiro e sua chave serão enviados pela internet diretamente à NVIDIA para redução de ruído. O serviço é preview/trial: disponibilidade, limites e custos podem mudar; confira os termos da NVIDIA. Não é offline. O original não será alterado. Cancelar não recolhe dados já enviados. Enviar este arquivo agora?")
            .setPositiveButton("Concordo e envio") { _, _ -> begin(uri) }
            .setNegativeButton("Não enviar", null).show()
    }

    private fun begin(uri: Uri) {
        if (running) return
        running = true
        result = null // do not present an older result as if it belonged to this request
        cancelled.set(false)
        status.text = "Validando e enviando…"
        refresh()
        executor.execute {
            val client = NvidiaBnrTransport()
            synchronized(lock) { transport = client; if (cancelled.get()) client.cancel() }
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { BnrWav.read(it, cancelled) }
                    ?: error("Não foi possível abrir o arquivo")
                BnrWav.validate(bytes)
                if (cancelled.get()) throw java.util.concurrent.CancellationException()
                val key = KeyVault(applicationContext).read(AiProvider.NVIDIA)
                    ?: error("Cadastre uma chave NVIDIA no gateway")
                val output = BnrProcessor(client).run(bytes, key, cancelled)
                val dir = File(cacheDir, "bnr-results")
                check(dir.isDirectory || dir.mkdirs())
                var created: File? = null
                try {
                    created = File.createTempFile("bnr-", ".wav", dir)
                    created.outputStream().use { it.write(output); it.flush(); it.fd.sync() }
                    val completed = created
                    synchronized(lock) {
                        if (cancelled.get()) throw java.util.concurrent.CancellationException()
                        result = completed
                    }
                    created = null
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) status.text = "Resultado validado e guardado temporariamente no cache. Compartilhe para salvar fora do app. O original não foi modificado."
                    }
                } finally { created?.delete() }
            } catch (_: java.util.concurrent.CancellationException) {
                runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Cancelado. A NVIDIA ainda pode ter recebido parte do áudio." }
            } catch (_: Exception) {
                runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Falha. Confira WAV, chave, conexão e disponibilidade da NVIDIA. Nenhuma alteração no original." }
            } finally {
                synchronized(lock) { transport = null }
                runOnUiThread { if (!isFinishing && !isDestroyed) { running = false; refresh() } }
            }
        }
    }

    private fun cancelOperation() {
        synchronized(lock) { cancelled.set(true); transport?.cancel() }
    }
    private fun refresh() { select.isEnabled = !running; stop.isEnabled = running; share.isEnabled = !running && result?.isFile == true }
    private fun shareResult() {
        val file = result?.takeIf { it.isFile } ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.bnrfiles", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar WAV processado"))
        } catch (_: Exception) { status.text = "Não foi possível compartilhar o resultado." }
    }
    override fun onDestroy() { cancelOperation(); executor.shutdownNow(); super.onDestroy() }
}
