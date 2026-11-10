package com.jvsleiman.ilha

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- cores ----------

val LaranjaDia = Color(0xFFF5A524)
val VerdeOk = Color(0xFF34C759)
val VermelhoErro = Color(0xFFFF453A)
val AmareloAviso = Color(0xFFFFD60A)
val AzulInfo = Color(0xFF0A84FF)
private val Contorno = Color(0xFF232327)

/** Números com largura fixa (o valor não "dança" enquanto muda). */
val NumerosFixos = TextStyle(fontFeatureSettings = "tnum")

/** Tema escuro por padrão, com o laranja do app. */
@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    val cores = darkColorScheme(
        primary = LaranjaDia,
        onPrimary = Color(0xFF1A1100),
        primaryContainer = Color(0xFF3A2A0A),
        onPrimaryContainer = Color(0xFFFFDDA8),
        secondaryContainer = Color(0xFF2A2418),
        onSecondaryContainer = Color(0xFFF2E6CF),
        tertiary = VerdeOk,
        error = VermelhoErro,
        background = Color(0xFF000000),
        onBackground = Color(0xFFF2F2F7),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFF2F2F7),
        onSurfaceVariant = Color(0xFFA1A1AA),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0B0B0D),
        surfaceContainer = Color(0xFF121214),
        surfaceContainerHigh = Color(0xFF1C1C1F),
        surfaceContainerHighest = Color(0xFF26262A),
        outline = Color(0xFF48484E),
        outlineVariant = Contorno,
    )
    MaterialTheme(colorScheme = cores, content = conteudo)
}

/** Lê e grava os ajustes; qualquer mudança chega na hora à ilha (o serviço escuta as preferências). */
class Ajustes(private val prefs: SharedPreferences) {
    var cfg by mutableStateOf(Prefs.read(prefs))
        private set

    /** Muda quando os ajustes são relidos (backup restaurado etc.): telas com cópia local se atualizam. */
    var geracao by mutableIntStateOf(0)
        private set

    fun salvar(novo: Config) {
        cfg = novo
        Prefs.write(prefs, novo)
    }

    fun recarregar() {
        cfg = Prefs.read(prefs)
        geracao++
    }
}

@Composable
fun rememberAjustes(): Ajustes {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val a = remember { Ajustes(prefs) }
    // Outras telas (ou o arrasto no modo edição) podem mudar os ajustes: mantém sincronizado
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == "config" || k == "favoritos" || k == Prefs.BLOQUEADOS) a.recarregar()
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return a
}

fun prefs(ctx: Context) = Prefs.get(ctx)

// ---------- blocos de tela ----------

/** Ícone num quadradinho arredondado com fundo tingido. */
@Composable
fun Selo(icone: ImageVector, cor: Color = MaterialTheme.colorScheme.primary, tamanho: Int = 32) {
    Box(
        Modifier.size(tamanho.dp).clip(RoundedCornerShape((tamanho * 0.32f).dp)).background(cor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size((tamanho * 0.56f).dp))
    }
}

/** Cartão de seção: título com ícone, descrição opcional e conteúdo. */
@Composable
fun Secao(
    titulo: String,
    icone: ImageVector? = null,
    descricao: String? = null,
    conteudo: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icone != null) {
                    Selo(icone)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (descricao != null) {
                        Text(descricao, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            conteudo()
        }
    }
}

/** Título grande de cada aba. */
@Composable
fun Cabecalho(titulo: String, sub: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)) {
        Text(titulo, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Etiqueta de estado: bolinha colorida + texto. */
@Composable
fun Etiqueta(texto: String, cor: Color) {
    Row(
        Modifier.clip(CircleShape).background(cor.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(6.dp))
        Text(texto, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cor, maxLines = 1)
    }
}

/** Faixa de aviso com ação (ex.: serviço desligado). */
@Composable
fun Aviso(titulo: String, texto: String, cor: Color = AmareloAviso, acao: String? = null, aoTocar: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, cor.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Selo(Icons.Filled.Warning, cor, 34)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(texto, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (acao != null && aoTocar != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    acao, color = cor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).clickable { aoTocar() }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Botão quadrado com ícone e rótulo (grade de testes, atalhos). */
@Composable
fun Bloco(rotulo: String, icone: ImageVector, cor: Color, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { aoTocar() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Selo(icone, cor, 36)
        Text(
            rotulo, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Permissao(titulo: String, desc: String, porQue: String, ok: Boolean, acao: String, abrir: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape)
                .background(if (ok) VerdeOk.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (ok) {
                Icon(
                    Icons.Filled.Check, contentDescription = "Ativa",
                    tint = VerdeOk, modifier = Modifier.size(14.dp),
                )
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(titulo, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                if (ok) "Ativa" else desc, fontSize = 13.sp,
                color = if (ok) VerdeOk else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ok) {
                Text(
                    porQue, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!ok) {
            FilledTonalButton(onClick = abrir) { Text(acao) }
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nome, Modifier.weight(1f), fontSize = 14.sp)
            Text(
                texto(valor), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, style = NumerosFixos,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Slider(
            value = valor.coerceIn(faixa.start, faixa.endInclusive),
            onValueChange = { mudar(((it / passo).roundToInt() * passo)) },
            valueRange = faixa,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

fun pct(v: Float) = "${(v * 100).roundToInt()}%"
fun seg(v: Float) = if (v == v.roundToInt().toFloat()) "${v.roundToInt()} s" else "%.1f s".format(v)

@Composable
fun Chave(nome: String, desc: String? = null, valor: Boolean, mudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { mudar(!valor) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(nome, fontSize = 15.sp)
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
                .background(if (ok) VerdeOk else MaterialTheme.colorScheme.outline),
        )
        Text(nome, Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp)
        Text(
            valor, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun Subtitulo(t: String) {
    Text(
        t.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Linha fina entre itens dentro de uma seção. */
@Composable
fun Divisor() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * Pré-visualização em escala: o topo da tela do celular (relógio, bateria) com a ilha nas medidas atuais.
 * Com [comAtividade], a ilha estica e mostra uma capa e a ondinha, como com música tocando.
 */
@Composable
fun PreviaIlha(cfg: Config, comAtividade: Boolean, altura: Int = 84) {
    val telaDp = LocalConfiguration.current.screenWidthDp.toFloat()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(altura.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF1B1B22), Color(0xFF101014))))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp)),
    ) {
        val escala = maxWidth.value / telaDp
        val mola = spring<androidx.compose.ui.unit.Dp>(dampingRatio = cfg.amortecimento, stiffness = cfg.rigidez * 0.6f)
        val wAlvo = (if (comAtividade) cfg.width + cfg.musicaExtra else cfg.width) * escala
        val w by animateDpAsState(wAlvo.dp, mola, label = "previaLargura")
        val h = cfg.height * escala
        val r = (cfg.height / 2f) * cfg.cantos.coerceIn(0.15f, 1f) * escala
        val y = (12f + cfg.offsetY * escala).coerceAtLeast(0f)
        val xCentro = maxWidth.value / 2f + cfg.offsetX * escala

        // Barra de status de mentira: hora à esquerda, bateria à direita
        Text(
            "12:30", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.offset(18.dp, (y + h / 2f - 7f).dp),
        )
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = (y + h / 2f - 5f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                Box(Modifier.padding(end = 2.dp).width(3.dp).height((4 + i * 2).dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.8f)))
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(18.dp).height(9.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp)).padding(1.5.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.85f)))
            }
        }

        Box(
            Modifier
                .offset((xCentro - w.value / 2f).dp, y.dp)
                .size(w, h.dp)
                .clip(RoundedCornerShape(r.dp))
                .background(Color.Black.copy(alpha = cfg.opacidade)),
            contentAlignment = Alignment.Center,
        ) {
            if (comAtividade && w.value > cfg.width * escala + 24f) {
                val icone = (h * 0.62f).dp
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = (h * 0.22f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(icone).clip(RoundedCornerShape(icone / 3))
                            .background(Brush.linearGradient(listOf(Color(0xFFFF9F7A), Color(0xFFF5A524)))),
                    )
                    Spacer(Modifier.weight(1f))
                    listOf(0.55f, 1f, 0.7f, 0.4f).forEach { f ->
                        Box(
                            Modifier.padding(start = 1.5.dp).width(2.dp).height((h * 0.5f * f).dp)
                                .clip(RoundedCornerShape(1.dp)).background(Color(0xFFFF9F7A)),
                        )
                    }
                }
            }
        }
        Text(
            if (comAtividade) "Com atividade" else "Parada",
            fontSize = 10.sp, color = Color(0xFF8E8E93),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
    }
}
