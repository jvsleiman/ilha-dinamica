package com.jvsleiman.ilha

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
    onSettled: () -> Unit,
    onAction: (String) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onLongPress)

    val w by animateDpAsState(ui.w.dp, mola<Dp>(), label = "largura")
    val h by animateDpAsState(ui.h.dp, mola<Dp>(), label = "altura")
    val r by animateDpAsState(ui.r.dp, mola<Dp>(), label = "cantos")

    // Quando a animação assenta, a janela encolhe para o tamanho exato
    val assentou = w == ui.w.dp && h == ui.h.dp
    LaunchedEffect(assentou, ui.modo) { if (assentou) onSettled() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(r))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { tap() }, onLongPress = { hold() })
                },
        ) {
            // O conteúdo é montado já no tamanho final e fica centralizado;
            // a ilha só "revela" ele enquanto cresce, sem reorganizar o texto a cada quadro.
            AnimatedContent(
                targetState = ui.modo,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .requiredSize(ui.w.dp, ui.h.dp),
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
                    Modo.MUSIC_X -> ui.music?.let { MusicaExpandida(it, onAction) }
                    Modo.NOTIF -> ui.notif?.let { Notificacao(it) }
                }
            }
        }
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
private fun MusicaExpandida(m: MusicInfo, onAction: (String) -> Unit) {
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            agora = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = m.currentPosition(agora)

    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
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
                Box(
                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF333333)),
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth((pos.toFloat() / m.duration).coerceIn(0f, 1f))
                            .background(Color.White),
                    )
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
private fun Notificacao(n: NotifInfo) {
    Row(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 18.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bmp = n.icon
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(
                img, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                n.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (n.text.isNotBlank()) {
                Text(
                    n.text, color = Cinza, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(n.app, color = Cinza, fontSize = 11.sp, maxLines = 1)
    }
}
