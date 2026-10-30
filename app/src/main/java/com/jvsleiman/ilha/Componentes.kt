package com.jvsleiman.ilha

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Tema escuro por padrão, com o laranja do app. */
@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    val cores = darkColorScheme(
        primary = Color(0xFFF5A524),
        onPrimary = Color(0xFF1A1100),
        secondaryContainer = Color(0xFF2A2418),
        onSecondaryContainer = Color(0xFFF2E6CF),
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceContainer = Color(0xFF151517),
        surfaceContainerHigh = Color(0xFF1E1E21),
    )
    MaterialTheme(colorScheme = cores, content = conteudo)
}

/** Lê e grava os ajustes; qualquer mudança chega na hora à ilha (o serviço escuta as preferências). */
class Ajustes(private val prefs: SharedPreferences) {
    var cfg by mutableStateOf(Prefs.read(prefs))
        private set

    fun salvar(novo: Config) {
        cfg = novo
        Prefs.write(prefs, novo)
    }

    fun recarregar() { cfg = Prefs.read(prefs) }
}

@Composable
fun rememberAjustes(): Ajustes {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val a = remember { Ajustes(prefs) }
    // Outras telas (ou o arrasto no modo edição) podem mudar os ajustes: mantém sincronizado
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == "config") a.recarregar() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return a
}

fun prefs(ctx: Context) = Prefs.get(ctx)

@Composable
fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                titulo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            conteudo()
        }
    }
}

@Composable
fun Permissao(titulo: String, desc: String, porQue: String, ok: Boolean, acao: String, abrir: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (ok) Color(0xFF34C759) else MaterialTheme.colorScheme.outline),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(titulo, fontWeight = FontWeight.Medium)
                Text(
                    if (ok) "Ativa" else desc, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ok) Button(onClick = abrir) { Text(acao) }
        }
        Text(
            porQue, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, top = 4.dp),
        )
    }
}

@Composable
fun Ajuste(
    nome: String,
    valor: Float,
    faixa: ClosedFloatingPointRange<Float>,
    texto: (Float) -> String = { "${it.roundToInt()} dp" },
    passo: Float = 1f,
    mudar: (Float) -> Unit,
) {
    Column {
        Row {
            Text(nome, Modifier.weight(1f))
            Text(texto(valor), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = valor.coerceIn(faixa.start, faixa.endInclusive),
            onValueChange = { mudar(((it / passo).roundToInt() * passo)) },
            valueRange = faixa,
        )
    }
}

fun pct(v: Float) = "${(v * 100).roundToInt()}%"
fun seg(v: Float) = if (v == v.roundToInt().toFloat()) "${v.roundToInt()} s" else "%.1f s".format(v)

@Composable
fun Chave(nome: String, desc: String? = null, valor: Boolean, mudar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(nome)
            if (desc != null) Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = valor, onCheckedChange = mudar)
    }
}

@Composable
fun Linha(nome: String, valor: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (ok) Color(0xFF34C759) else MaterialTheme.colorScheme.outline),
        )
        Text(nome, Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp)
        Text(valor, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun Subtitulo(t: String) {
    Text(
        t, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Pré-visualização em escala: o topo da tela do celular com a ilha nas medidas atuais.
 * Mostra a forma parada e a forma com atividade lado a lado no tempo (alterna sozinha).
 */
@Composable
fun PreviaIlha(cfg: Config, comAtividade: Boolean) {
    val telaDp = LocalConfiguration.current.screenWidthDp.toFloat()
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2B2B30)),
    ) {
        val escala = maxWidth.value / telaDp
        val w = (if (comAtividade) cfg.width + cfg.musicaExtra else cfg.width) * escala
        val h = cfg.height * escala
        val r = (cfg.height / 2f) * cfg.cantos.coerceIn(0.15f, 1f) * escala
        val x = (maxWidth.value - w) / 2f + cfg.offsetX * escala
        val y = 10f + cfg.offsetY * escala
        Box(
            Modifier
                .offset(x.dp, y.coerceAtLeast(0f).dp)
                .size(w.dp, h.dp)
                .clip(RoundedCornerShape(r.dp))
                .background(Color.Black.copy(alpha = cfg.opacidade)),
        )
        Text(
            "Prévia em escala", fontSize = 10.sp, color = Color(0xFF8E8E93),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
    }
}
