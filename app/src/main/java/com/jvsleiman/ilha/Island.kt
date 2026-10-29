package com.jvsleiman.ilha

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.abs
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Mola da forma: macia, com um quique bem leve no final. */
private fun <T> mola() = spring<T>(dampingRatio = 0.82f, stiffness = 260f)

/** Mola do conteúdo: sem quique. */
private fun <T> molaConteudo() = spring<T>(dampingRatio = 1f, stiffness = 320f)

private val Cinza = Color(0xFF9A9AA2)

@Composable
fun Island(
    ui: UiState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onAction: (String) -> Unit,
    onSwipe: (Boolean) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)
    val hold by rememberUpdatedState(onLongPress)

    val w by animateDpAsState(ui.wMain.dp, mola<Dp>(), label = "largura")
    val dx by animateDpAsState(ui.dx.dp, mola<Dp>(), label = "deslocamento")
    val h by animateDpAsState(ui.h.dp, mola<Dp>(), label = "altura")
    val r by animateDpAsState(ui.r.dp, mola<Dp>(), label = "cantos")
    val y by animateFloatAsState(ui.y, mola<Float>(), label = "topo")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                // Sempre centralizada na câmera, qualquer que seja a largura no momento
                .offset { IntOffset((ui.cx + dx.toPx() - w.toPx() / 2f).roundToInt(), y.roundToInt()) }
                .size(w, h)
                .clip(RoundedCornerShape(r))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { tap() }, onLongPress = { hold() })
                }
                // Deslizar para o lado: troca de música
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (abs(dx) > 48.dp.toPx()) swipe(dx > 0) },
                        onHorizontalDrag = { change, d -> dx += d; change.consume() },
                    )
                },
        ) {
            // O conteúdo é montado já no tamanho final e fica centralizado;
            // a ilha só "revela" ele enquanto cresce, sem reorganizar o texto a cada quadro.
            AnimatedContent(
                targetState = ui.modo,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .requiredSize(ui.wMain.dp, ui.h.dp),
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) +
                        scaleIn(molaConteudo(), initialScale = 0.88f, transformOrigin = TransformOrigin(0.5f, 0f)))
                        .togetherWith(
                            fadeOut(tween(110)) +
                                scaleOut(tween(160), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f)),
                        )
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "conteudo",
            ) { modo ->
                when (modo) {
                    Modo.IDLE -> Box(Modifier.fillMaxSize())
                    Modo.MUSIC_C -> ui.music?.let { MusicaCompacta(it) }
                    Modo.MUSIC_X -> ui.music?.let { MusicaExpandida(it, ui.topo, onAction) }
                    Modo.NOTIF -> ui.notif?.let { FaixaNotif(it, ui.contagem) }
                    Modo.NOTIF_C -> ui.notif?.let { IconeNotif(it) }
                    Modo.NOTIF_X -> ui.notif?.let { CartaoNotif(it, ui, onAction) }
                }
            }

            // Ícone do app: um elemento só que desliza entre faixa → lado da câmera → cabeçalho do cartão
            IconeCompartilhado(ui)
        }

        // Bolinha da ilha dividida (fica fora da ilha principal)
        Bolinha(ui, w, dx, y, onAction)
    }
}

// ---------- música ----------

@Composable
private fun Capa(m: MusicInfo, size: Dp, corner: Dp) {
    val bmp = m.art
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(
            img, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
        )
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(Color(m.accent)))
    }
}

@Composable
private fun Onda(cor: Color, tocando: Boolean, altura: Dp) {
    val t = rememberInfiniteTransition(label = "onda")
    Row(
        Modifier.height(altura),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { i ->
            val f by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(380 + i * 110, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "barra$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (tocando) f else 0.25f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cor),
            )
        }
    }
}

@Composable
private fun MusicaCompacta(m: MusicInfo) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Capa(m, 22.dp, 7.dp)
        Spacer(Modifier.weight(1f))
        Onda(Color(m.accent), m.playing, 14.dp)
        Spacer(Modifier.width(8.dp))
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun MusicaExpandida(m: MusicInfo, topo: Float, onAction: (String) -> Unit) {
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            agora = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = m.currentPosition(agora)

    Column(
        Modifier.fillMaxSize().padding(
            start = MARGEM_INTERNA.dp, end = MARGEM_INTERNA.dp, top = topo.dp, bottom = MARGEM_INTERNA.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pointerInput(Unit) { detectTapGestures { onAction("app") } }) {
                Capa(m, 54.dp, 14.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    m.artist, color = Cinza, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Onda(Color(m.accent), m.playing, 18.dp)
        }

        if (m.duration > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mmss(pos), color = Cinza, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                // Toque na barra: pula para aquele ponto da música
                Box(
                    Modifier.weight(1f).height(18.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                onAction("seek:" + (o.x / size.width).coerceIn(0f, 1f))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF333333)),
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth((pos.toFloat() / m.duration).coerceIn(0f, 1f))
                            .background(Color.White),
                    )
                }
                }
                Spacer(Modifier.width(8.dp))
                Text("-" + mmss(m.duration - pos), color = Cinza, fontSize = 11.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Botao("prev", onAction) { Icone(IconeTipo.PREV, 26.dp) }
            Spacer(Modifier.width(40.dp))
            Botao("play", onAction) { Icone(if (m.playing) IconeTipo.PAUSE else IconeTipo.PLAY, 32.dp) }
            Spacer(Modifier.width(40.dp))
            Botao("next", onAction) { Icone(IconeTipo.NEXT, 26.dp) }
        }
    }
}

@Composable
private fun Botao(acao: String, onAction: (String) -> Unit, conteudo: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).pointerInput(acao) { detectTapGestures { onAction(acao) } },
        contentAlignment = Alignment.Center,
    ) { conteudo() }
}

private enum class IconeTipo { PLAY, PAUSE, NEXT, PREV }

@Composable
private fun Icone(tipo: IconeTipo, tamanho: Dp) {
    Canvas(Modifier.size(tamanho)) {
        val s = size.width
        fun tri(x0: Float, x1: Float) = Path().apply {
            moveTo(x0, s * 0.2f); lineTo(x1, s * 0.5f); lineTo(x0, s * 0.8f); close()
        }
        when (tipo) {
            IconeTipo.PLAY -> drawPath(tri(s * 0.25f, s * 0.82f), Color.White)
            IconeTipo.PAUSE -> {
                drawRect(Color.White, Offset(s * 0.24f, s * 0.2f), androidx.compose.ui.geometry.Size(s * 0.17f, s * 0.6f))
                drawRect(Color.White, Offset(s * 0.59f, s * 0.2f), androidx.compose.ui.geometry.Size(s * 0.17f, s * 0.6f))
            }
            IconeTipo.NEXT -> {
                drawPath(tri(s * 0.12f, s * 0.5f), Color.White)
                drawPath(tri(s * 0.5f, s * 0.88f), Color.White)
            }
            IconeTipo.PREV -> {
                drawPath(tri(s * 0.88f, s * 0.5f), Color.White)
                drawPath(tri(s * 0.5f, s * 0.12f), Color.White)
            }
        }
    }
}

// ---------- notificação ----------

@Composable
private fun IconeApp(bmp: android.graphics.Bitmap?, tamanho: Dp) {
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(
            img, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(tamanho).clip(RoundedCornerShape(tamanho / 2)),
        )
    } else {
        Box(Modifier.size(tamanho).clip(RoundedCornerShape(tamanho / 2)).background(Color(0xFF333333)))
    }
}

/**
 * Bolinha de um segundo app. Ela "nasce" de dentro da borda direita da ilha principal
 * (pequena e invisível) e se separa até a posição final, como uma gota se soltando.
 */
@Composable
private fun Bolinha(ui: UiState, wMain: Dp, dx: Dp, y: Float, onAction: (String) -> Unit) {
    val acao by rememberUpdatedState(onAction)
    var ultima by remember { mutableStateOf<NotifInfo?>(null) }
    var qtd by remember { mutableStateOf(1) }
    if (ui.bolha != null) { ultima = ui.bolha; qtd = ui.bolhaContagem }
    val ativa = ui.bolha != null && ui.modo == Modo.MUSIC_C

    val sep by animateFloatAsState(if (ativa) 1f else 0f, mola<Float>(), label = "separacao")
    val n = ultima ?: return
    if (sep < 0.01f && !ativa) return

    val b = ui.alturaCamera.dp
    Box(
        Modifier
            .offset {
                val bordaDireita = ui.cx + dx.toPx() + wMain.toPx() / 2f
                val dentro = bordaDireita - b.toPx()                       // colada por dentro da borda
                val fora = bordaDireita + ESPACO_BOLHA.dp.toPx()          // posição final, 8 dp ao lado
                IntOffset((dentro + (fora - dentro) * sep).roundToInt(), y.roundToInt())
            }
            .size(b)
            .graphicsLayer {
                val e = 0.3f + 0.7f * sep.coerceIn(0f, 1.2f)
                scaleX = e; scaleY = e
                alpha = sep.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { acao("bolha") }, onLongPress = { acao("bolha_abrir") })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Conteúdo só aparece depois que a bolinha já se separou ~60%
        Box(Modifier.graphicsLayer { alpha = ((sep - 0.6f) / 0.4f).coerceIn(0f, 1f) }) {
            IconeApp(n.appIcon ?: n.icon, 20.dp)
        }
    }
    if (qtd > 1) {
        Box(
            Modifier
                .offset {
                    val bordaDireita = ui.cx + dx.toPx() + wMain.toPx() / 2f
                    val fora = bordaDireita + ESPACO_BOLHA.dp.toPx() + b.toPx() - 11.dp.toPx()
                    IntOffset(fora.roundToInt(), (y - 3.dp.toPx()).roundToInt())
                }
                .graphicsLayer { alpha = ((sep - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$qtd", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IconeCompartilhado(ui: UiState) {
    var ultimo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val n = ui.notif
    if (n != null) ultimo = n.appIcon ?: n.icon
    val ativo = n != null && (ui.modo == Modo.NOTIF || ui.modo == Modo.NOTIF_C || ui.modo == Modo.NOTIF_X)
    val cartao = ui.modo == Modo.NOTIF_X

    val tam = if (cartao) 18f else 20f
    val x by animateDpAsState((if (cartao) MARGEM_INTERNA else 6f).dp, molaIcone(), label = "iconeX")
    val y by animateDpAsState(
        (if (cartao) ui.acima + (ui.alturaCamera - tam) / 2f else (ui.alturaCamera - tam) / 2f).dp,
        molaIcone(), label = "iconeY",
    )
    val t by animateDpAsState(tam.dp, molaIcone(), label = "iconeTam")
    val alfa by animateFloatAsState(if (ativo) 1f else 0f, tween(if (ativo) 200 else 120), label = "iconeAlfa")

    if (alfa > 0.01f) {
        Box(Modifier.offset(x, y).graphicsLayer { alpha = alfa }) { IconeApp(ultimo, t) }
    }
}

private fun <T> molaIcone() = spring<T>(dampingRatio = 0.85f, stiffness = 300f)

/** Faixa larga: ícone e nome do app à esquerda, "1 nova mensagem" na cor do app à direita. */
@Composable
private fun FaixaNotif(n: NotifInfo, contagem: Int) {
    Row(
        Modifier.fillMaxSize().padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(20.dp + 8.dp)) // lugar do ícone compartilhado
        val qtd = contagem.coerceAtLeast(1)
        // Como no iPhone: "Nome (1)" à esquerda e "Ver" na cor do app à direita
        Text(
            "${n.title} ($qtd)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("Ver", color = Color(n.accent), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Compacto: só o ícone do app ao lado da câmera, avisando que tem mensagem. */
@Composable
private fun IconeNotif(n: NotifInfo) {
    // O ícone é desenhado pelo IconeCompartilhado
    Box(Modifier.fillMaxSize())
}

/** Cartão aberto: cabeçalho do app, pessoa, horário, mensagem e botões contornados. */
@Composable
private fun CartaoNotif(n: NotifInfo, ui: UiState, onAction: (String) -> Unit) {
    val hora = remember(n.time) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(if (n.time > 0) n.time else System.currentTimeMillis()))
    }
    val accent = Color(n.accent)
    Box(Modifier.fillMaxSize()) {
        // Cabeçalho na mesma altura da câmera, encostado à esquerda
        Row(
            Modifier
                .padding(start = MARGEM_INTERNA.dp, top = ui.acima.dp)
                .height(ui.alturaCamera.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(18.dp + 8.dp)) // lugar do ícone compartilhado
            Text(n.app, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }

        Column(
            Modifier.fillMaxSize().padding(
                start = MARGEM_INTERNA.dp, end = MARGEM_INTERNA.dp, top = ui.topo.dp, bottom = MARGEM_INTERNA.dp,
            ),
        ) {
            // Pessoa: foto com bolinha "online", nome e horário
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconeApp(n.icon, 40.dp)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(12.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.Black).padding(2.dp).clip(RoundedCornerShape(4.dp)).background(accent),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    n.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(hora, color = Cinza, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            // Mensagem num balão, como no app
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2C2C2E)).padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    n.text.ifBlank { "Nova mensagem" }, color = Color.White, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (n.actions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    n.actions.forEachIndexed { i, a ->
                        Box(
                            Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, accent, RoundedCornerShape(18.dp))
                                .pointerInput(i) { detectTapGestures { onAction("acao:$i") } }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                a.title.toString(), color = accent, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
