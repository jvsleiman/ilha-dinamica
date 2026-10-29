package com.jvsleiman.ilha

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Campo de resposta que aparece colado no topo, no formato da ilha.
 * Envia pelo próprio botão "Responder" do app (RemoteInput), sem abrir o app.
 */
class ReplyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val req = IslandBus.reply
        if (req == null) {
            finish()
            return
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Resposta(req, onEnviar = { enviar(req, it) }, onFechar = { finish() })
            }
        }
    }

    private fun enviar(req: ReplyRequest, texto: String) {
        val inputs = req.action.remoteInputs ?: return
        val dados = Bundle()
        inputs.forEach { dados.putCharSequence(it.resultKey, texto) }
        val intent = Intent()
        RemoteInput.addResultsToIntent(inputs, intent, dados)
        try {
            req.action.actionIntent.send(this, 0, intent)
            IslandBus.silenciar(req.notif.key)
            IslandBus.log("Resposta enviada para ${req.notif.title}")
        } catch (e: Exception) {
            IslandBus.log("Resposta falhou: ${e.message}")
        }
        IslandBus.reply = null
        finish()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun Resposta(req: ReplyRequest, onEnviar: (String) -> Unit, onFechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    // Toque fora do cartão fecha
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onFechar() } },
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { } } // toques no cartão não fecham
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(18.dp)) // faixa da câmera
            Text(
                "Responder a ${req.notif.title}", color = Color.White,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (req.notif.text.isNotBlank()) {
                Text(
                    req.notif.text, color = Color(0xFF9A9AA2), fontSize = 14.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Mensagem") },
                    modifier = Modifier.weight(1f).focusRequester(foco),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (texto.isNotBlank()) onEnviar(texto.trim()) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1C1C1E),
                        unfocusedContainerColor = Color(0xFF1C1C1E),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (texto.isBlank()) Color(0xFF2C2C2E) else Color(0xFF0A84FF))
                        .pointerInput(texto) {
                            detectTapGestures { if (texto.isNotBlank()) onEnviar(texto.trim()) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("➤", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}
