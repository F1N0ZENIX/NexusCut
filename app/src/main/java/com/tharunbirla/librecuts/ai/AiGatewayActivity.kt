package com.tharunbirla.librecuts.ai

import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Deliberately minimal utility screen, not the approved design concept. */
class AiGatewayActivity : AppCompatActivity() {
    private lateinit var vault: KeyVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        vault = KeyVault(applicationContext)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        fun label(text: String) { content.addView(TextView(this).apply { this.text = text; textSize = 16f }) }
        fun button(text: String, onClick: () -> Unit): Button = Button(this).also {
            it.text = text; it.setOnClickListener { onClick() }; content.addView(it)
        }
        label("Gateway de IA — tela simples de teste")
        label("As chaves ficam criptografadas neste dispositivo. NVIDIA e Z.ai: somente cadastro, sem integração de tarefas.")
        for (provider in AiProvider.values()) button("Chave: ${providerLabel(provider)}") { manageKey(provider) }
        label("Transcrição e salvamento de SRT estão desativados nesta versão experimental por segurança. Nenhum arquivo será enviado.")
    }

    private fun providerLabel(provider: AiProvider): String = when (provider) {
        AiProvider.GROQ -> "Groq"
        AiProvider.NVIDIA -> "NVIDIA"
        AiProvider.ZAI -> "Z.ai"
    }

    private fun manageKey(provider: AiProvider) {
        val state = if (vault.exists(provider)) "Chave cadastrada" else "Sem chave cadastrada"
        AlertDialog.Builder(this).setTitle(providerLabel(provider)).setMessage(state)
            .setPositiveButton("Cadastrar / substituir") { _, _ -> editKey(provider) }
            .setNegativeButton("Excluir") { _, _ ->
                AlertDialog.Builder(this).setMessage("Excluir a chave de ${providerLabel(provider)} deste dispositivo?")
                    .setPositiveButton("Excluir") { _, _ ->
                        try { vault.delete(provider); message("Chave excluída.") }
                        catch (_: Exception) { message("Não foi possível excluir a chave.") }
                    }.setNegativeButton("Cancelar", null).show()
            }.setNeutralButton("Voltar", null).show()
    }

    private fun editKey(provider: AiProvider) {
        val input = EditText(this).apply {
            hint = "Chave de API"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSaveEnabled = false
        }
        val dialog = AlertDialog.Builder(this).setTitle("Chave: ${providerLabel(provider)}")
            .setView(input).setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
        // Dialogs have their own window: set the flag before it can be displayed.
        requireNotNull(dialog.window).addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener { input.text.clear() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    vault.save(provider, input.text.toString().trim())
                    input.text.clear()
                    dialog.dismiss()
                    message("Chave salva neste dispositivo.")
                } catch (_: Exception) { message("Não foi possível salvar. Confira a chave e o Keystore.") }
            }
        }
        dialog.show()
    }

    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
