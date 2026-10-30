package com.jvsleiman.ilha

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mola do conteúdo: sem quique. */
private fun <T> molaConteudo() = spring<T>(dampingRatio = 1f, stiffness = 320f)

private val Cinza = Color(0xFF9A9AA2)
private val Verde = Color(0xFF34C759)
private val Vermelho = Color(0xFFFF453A)
private val Laranja = Color(0xFFFF9F0A)
private val Amarelo = Color(0xFFFFD60A)

/** Números com largura fixa: o relógio não "dança" a cada segundo. */
private val Numeros = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun Island(
    ui: UiState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onAction: (String) -> Unit,
    onSwipe: (Boolean) -> Unit,
    onFechar: () -> Unit,
) {
    val fechar by rememberUpdatedState(onFechar)
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)
    val hold by rememberUpdatedState(onLongPress)

    // Mola da forma: rigidez e amortecimento vêm dos ajustes de animação
    val mola = spring<Dp>(dampingRatio = ui.amortecimento, stiffness = ui.rigidez)
    val w by animateDpAsState(ui.wMain.dp, mola, label = "largura")
    val dx by animateDpAsState(ui.dx.dp, mola, label = "deslocamento")
    val h by animateDpAsState(ui.h.dp, mola, label = "altura")
    val r by animateDpAsState(ui.r.dp, mola, label = "cantos")
    val y by animateFloatAsState(ui.y, spring(dampingRatio = ui.amortecimento, stiffness = ui.rigidez), label = "topo")

    // Arrastar: a ilha acompanha o dedo. Curto = troca (música/atividade); longo para a esquerda = fecha.
    val scope = rememberCoroutineScope()
    val vibrar = LocalHapticFeedback.current
    val arrasto = remember { Animatable(0f) }
    val telaPx = LocalConfiguration.current.screenWidthDp * LocalDensity.current.density
    val limite = telaPx * 0.3f
    val podeFechar = ui.modo != Modo.IDLE && !ui.editando
    val progresso = (-arrasto.value / limite).coerceIn(0f, 1f)

    // Tamanho do texto: escala só dentro da ilha
    val dens = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(dens.density, dens.fontScale * ui.tamanhoTexto)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    // Sempre centralizada na câmera, qualquer que seja a largura no momento
                    .offset {
                        IntOffset((ui.cx + dx.toPx() - w.toPx() / 2f + arrasto.value).roundToInt(), y.roundToInt())
                    }
                    .graphicsLayer {
                        alpha = 1f - 0.55f * progresso
                        scaleX = 1f - 0.12f * progresso
                        scaleY = 1f - 0.12f * progresso
                    }
                    .size(w, h)
                    .clip(RoundedCornerShape(r))
                    .background(Color.Black.copy(alpha = ui.opacidade))
                    .then(
                        if (ui.editando) Modifier.border(2.dp, Amarelo, RoundedCornerShape(r)) else Modifier,
                    )
                    .pointerInput(ui.editando) {
                        if (ui.editando) return@pointerInput // no modo edição o arrasto é tratado pelo serviço
                        detectTapGestures(onTap = { tap() }, onLongPress = { hold() })
                    }
                    .pointerInput(podeFechar, ui.editando) {
                        if (ui.editando) return@pointerInput
                        var passou = false
                        val volta = spring<Float>(dampingRatio = 0.72f, stiffness = 380f)
                        detectHorizontalDragGestures(
                            onDragStart = { passou = false },
                            onHorizontalDrag = { change, d ->
                                change.consume()
                                val atual = arrasto.value
                                // para a direita tem resistência (só serve para voltar/alternar)
                                val novo = atual + if (atual + d > 0f) d * 0.35f else d
                                scope.launch { arrasto.snapTo(novo) }
                                val alem = podeFechar && -novo > limite
                                if (alem && !passou) vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                                passou = alem
                            },
                            onDragEnd = {
                                val v = arrasto.value
                                scope.launch {
                                    if (podeFechar && -v > limite) {
                                        // sai voando para a esquerda e fecha
                                        arrasto.animateTo(-telaPx, tween(180))
                                        fechar()
                                        arrasto.snapTo(0f)
                                    } else {
                                        if (abs(v) > 48.dp.toPx()) swipe(v > 0)
                                        arrasto.animateTo(0f, volta)
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { arrasto.animateTo(0f, volta) } },
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
                        Modo.MUSIC_C -> ui.music?.let { MusicaCompacta(it, ui) }
                        Modo.MUSIC_X -> ui.music?.let { MusicaExpandida(it, ui, onAction) }
                        Modo.NOTIF -> ui.notif?.let { FaixaNotif(it, ui.contagem) }
                        Modo.NOTIF_C -> Box(Modifier.fillMaxSize()) // o ícone é desenhado pelo IconeCompartilhado
                        Modo.NOTIF_X -> ui.notif?.let { CartaoNotif(it, ui, onAction) }
                        Modo.LIVE_C -> ui.live?.let { LiveCompacta(it, ui) }
                        Modo.LIVE_X -> ui.live?.let { LiveExpandida(it, ui, onAction) }
                        Modo.CALL_IN -> ui.live?.let { ChamadaRecebendo(it, ui, onAction) }
                        Modo.ALERTA -> ui.alerta?.let { AlertaCompacto(it) }
                    }
                }

                // Ícone do app: um elemento só que desliza entre faixa → lado da câmera → cabeçalho do cartão
                IconeCompartilhado(ui)
            }

            // Bolinha da ilha dividida (fica fora da ilha principal)
            Bolinha(ui, w, dx, y, onAction)
        }
    }
}

// ---------- utilidades ----------

/** Relógio que só "anda" enquanto [ativo]: evita redesenhar a ilha parada. */
@Composable
private fun rememberAgora(ativo: Boolean): Pair<Long, Long> {
    var mono by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var parede by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ativo) {
        while (ativo) {
            mono = SystemClock.elapsedRealtime()
            parede = System.currentTimeMillis()
            // acorda bem na virada do segundo
            delay(1000L - (parede % 1000L) + 5L)
        }
    }
    return mono to parede
}

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

private enum class Glifo { TELEFONE, DESLIGAR, TIMER, RAIO, PAUSA, PLAY, XIS }

/** Ícones desenhados (sem depender de fontes ou imagens). */
@Composable
private fun Desenho(g: Glifo, cor: Color, tamanho: Dp) {
    Canvas(Modifier.size(tamanho)) {
        val s = size.width
        when (g) {
            Glifo.TELEFONE, Glifo.DESLIGAR -> rotate(if (g == Glifo.DESLIGAR) 135f else 0f) {
                // fone: arco grosso com pontas arredondadas
                drawArc(
                    cor, startAngle = 110f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(s * 0.18f, s * 0.18f), size = Size(s * 0.64f, s * 0.64f),
                    style = Stroke(width = s * 0.2f, cap = StrokeCap.Round),
                )
            }
            Glifo.TIMER -> {
                drawCircle(cor, radius = s * 0.36f, center = Offset(s / 2, s * 0.55f), style = Stroke(s * 0.11f))
                drawLine(cor, Offset(s / 2, s * 0.55f), Offset(s / 2, s * 0.34f), s * 0.11f, StrokeCap.Round)
                drawLine(cor, Offset(s * 0.4f, s * 0.08f), Offset(s * 0.6f, s * 0.08f), s * 0.11f, StrokeCap.Round)
            }
            Glifo.RAIO -> drawPath(
                Path().apply {
                    moveTo(s * 0.58f, s * 0.05f); lineTo(s * 0.2f, s * 0.56f); lineTo(s * 0.47f, s * 0.56f)
                    lineTo(s * 0.4f, s * 0.95f); lineTo(s * 0.8f, s * 0.42f); lineTo(s * 0.53f, s * 0.42f); close()
                },
                cor,
            )
            Glifo.PAUSA -> {
                drawRect(cor, Offset(s * 0.24f, s * 0.2f), Size(s * 0.17f, s * 0.6f))
                drawRect(cor, Offset(s * 0.59f, s * 0.2f), Size(s * 0.17f, s * 0.6f))
            }
            Glifo.PLAY -> drawPath(
                Path().apply { moveTo(s * 0.28f, s * 0.2f); lineTo(s * 0.82f, s * 0.5f); lineTo(s * 0.28f, s * 0.8f); close() },
                cor,
            )
            Glifo.XIS -> {
                drawLine(cor, Offset(s * 0.28f, s * 0.28f), Offset(s * 0.72f, s * 0.72f), s * 0.12f, StrokeCap.Round)
                drawLine(cor, Offset(s * 0.72f, s * 0.28f), Offset(s * 0.28f, s * 0.72f), s * 0.12f, StrokeCap.Round)
            }
        }
    }
}

/** Bateria pequena, que enche com animação até [nivel]. */
@Composable
private fun Bateria(nivel: Float, cor: Color) {
    val enche = remember { Animatable(0f) }
    LaunchedEffect(nivel) { enche.animateTo(nivel.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.width(27.dp).height(13.dp)) {
        val corpo = size.width * 0.9f
        val raio = androidx.compose.ui.geometry.CornerRadius(size.height * 0.3f)
        drawRoundRect(Color.White.copy(alpha = 0.35f), size = Size(corpo, size.height), cornerRadius = raio, style = Stroke(1.5.dp.toPx()))
        val p = 2.dp.toPx()
        drawRoundRect(
            cor, topLeft = Offset(p, p),
            size = Size((corpo - 2 * p) * enche.value, size.height - 2 * p),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.18f),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.35f), topLeft = Offset(corpo + 1.dp.toPx(), size.height * 0.32f),
            size = Size(size.width - corpo - 1.dp.toPx(), size.height * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
        )
    }
}

/** Anel de progresso (bolinha e compacto). */
@Composable
private fun Anel(progresso: Float?, cor: Color, tamanho: Dp) {
    val giro = rememberInfiniteTransition(label = "anel")
    val a by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "giro")
    Canvas(Modifier.size(tamanho)) {
        val st = Stroke(width = size.width * 0.16f, cap = StrokeCap.Round)
        val inset = st.width / 2
        val tl = Offset(inset, inset)
        val sz = Size(size.width - st.width, size.height - st.width)
        drawArc(cor.copy(alpha = 0.25f), 0f, 360f, false, tl, sz, style = st)
        if (progresso != null) drawArc(cor, -90f, 360f * progresso, false, tl, sz, style = st)
        else drawArc(cor, a - 90f, 90f, false, tl, sz, style = st)
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
private fun Onda(cor: Color, tocando: Boolean, altura: Dp, animada: Boolean) {
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
            val alt = when {
                !tocando -> 0.25f
                animada -> f
                else -> listOf(0.6f, 1f, 0.75f, 0.45f)[i]
            }
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(alt)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cor),
            )
        }
    }
}

@Composable
private fun MusicaCompacta(m: MusicInfo, ui: UiState) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Capa(m, 22.dp * ui.escalaIcones, 7.dp)
        Spacer(Modifier.weight(1f))
        Onda(Color(m.accent), m.playing, 14.dp * ui.escalaIcones, ui.animOnda)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun MusicaExpandida(m: MusicInfo, ui: UiState, onAction: (String) -> Unit) {
    val (agora, _) = rememberAgora(m.playing)
    val pos = m.currentPosition(agora)

    Column(
        Modifier.fillMaxSize().padding(
            start = MARGEM_INTERNA.dp, end = MARGEM_INTERNA.dp, top = ui.topo.dp, bottom = MARGEM_INTERNA.dp,
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
            Onda(Color(m.accent), m.playing, 18.dp, ui.animOnda)
        }

        if (m.duration > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(relogio(pos), color = Cinza, fontSize = 11.sp, style = Numeros)
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
                    Barra((pos.toFloat() / m.duration).coerceIn(0f, 1f), Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text("-" + relogio(m.duration - pos), color = Cinza, fontSize = 11.sp, style = Numeros)
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
private fun Barra(fracao: Float, cor: Color) {
    Box(
        Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF333333)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fracao.coerceIn(0f, 1f)).background(cor))
    }
}

/** Barra sem fim conhecido: um trecho que corre de um lado para o outro. */
@Composable
private fun BarraIndeterminada(cor: Color) {
    val t = rememberInfiniteTransition(label = "indeterminada")
    val x by t.animateFloat(-0.3f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)), label = "x")
    Canvas(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF333333))) {
        drawRect(cor, Offset(size.width * x, 0f), Size(size.width * 0.3f, size.height))
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
                drawRect(Color.White, Offset(s * 0.24f, s * 0.2f), Size(s * 0.17f, s * 0.6f))
                drawRect(Color.White, Offset(s * 0.59f, s * 0.2f), Size(s * 0.17f, s * 0.6f))
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

// ---------- atividades contínuas ----------

/** Ícone da esquerda de uma atividade: foto/ícone do app ou desenho do tipo. */
@Composable
private fun IconeLive(l: LiveInfo, tamanho: Dp) {
    when {
        l.proprio != null -> Desenho(Glifo.TIMER, Color(l.accent), tamanho)
        l.tipo == Tipo.CHAMADA -> Box(
            Modifier.size(tamanho).clip(CircleShape).background(Verde.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Desenho(Glifo.TELEFONE, Verde, tamanho * 0.7f) }
        else -> IconeApp(l.appIcon ?: l.icon, tamanho)
    }
}

@Composable
private fun LiveCompacta(l: LiveInfo, ui: UiState) {
    val (mono, parede) = rememberAgora(l.tiqueTaque && !(l.proprio?.pausado ?: false))
    val texto = l.textoCurto(parede, mono)
    val cor = Color(l.accent)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeLive(l, 20.dp * ui.escalaIcones)
        Spacer(Modifier.weight(1f))
        when {
            texto.isNotEmpty() && l.progresso == null -> Text(
                texto, color = cor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Numeros, maxLines = 1,
            )
            else -> Anel(l.progressoAgora(mono), cor, 18.dp * ui.escalaIcones)
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun LiveExpandida(l: LiveInfo, ui: UiState, onAction: (String) -> Unit) {
    val t = l.proprio
    val (mono, parede) = rememberAgora(l.tiqueTaque && !(t?.pausado ?: false))
    val cor = Color(l.accent)
    val texto = l.textoCurto(parede, mono)
    val prog = l.progressoAgora(mono)
    Column(
        Modifier.fillMaxSize().padding(
            start = MARGEM_INTERNA.dp, end = MARGEM_INTERNA.dp, top = ui.topo.dp, bottom = MARGEM_INTERNA.dp,
        ),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (l.tipo == Tipo.CHAMADA || l.proprio != null) IconeLive(l, 40.dp) else IconeApp(l.icon ?: l.appIcon, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    l.title.ifBlank { l.app }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = l.text.ifBlank { if (l.title.isBlank()) "" else l.app }
                if (sub.isNotBlank()) {
                    Text(sub, color = Cinza, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (prog == null && !l.indeterminado && texto.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(texto, color = cor, fontSize = 30.sp, fontWeight = FontWeight.Medium, style = Numeros, maxLines = 1)
            }
        }
        if (prog != null || l.indeterminado) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { if (prog != null) Barra(prog, cor) else BarraIndeterminada(cor) }
                if (texto.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(texto, color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Numeros)
                }
            }
        } else if (l.tiqueTaque) {
            Spacer(Modifier.height(34.dp))
        }
        if (t != null) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotaoRedondo(if (t.pausado) Glifo.PLAY else Glifo.PAUSA, cor, cor.copy(alpha = 0.22f)) { onAction("timer:pausar") }
                BotaoRedondo(Glifo.XIS, Color.White, Color(0xFF3A3A3C)) { onAction("timer:cancelar") }
            }
        } else if (l.actions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                l.actions.forEachIndexed { i, a ->
                    val c = when (if (l.tipo == Tipo.CHAMADA) corDaAcaoDeChamada(a.title.toString()) else 0) {
                        1 -> Verde
                        -1 -> Vermelho
                        else -> cor
                    }
                    Pilula(a.title.toString(), c) { onAction("live:$i") }
                }
            }
        }
    }
}

@Composable
private fun BotaoRedondo(g: Glifo, cor: Color, fundo: Color, tamanho: Dp = 36.dp, aoTocar: () -> Unit) {
    Box(
        Modifier.size(tamanho).clip(CircleShape).background(fundo).pointerInput(g) { detectTapGestures { aoTocar() } },
        contentAlignment = Alignment.Center,
    ) { Desenho(g, cor, tamanho * 0.5f) }
}

@Composable
private fun Pilula(texto: String, cor: Color, aoTocar: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, cor, RoundedCornerShape(18.dp))
            .pointerInput(texto) { detectTapGestures { aoTocar() } }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = cor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Chamada tocando: foto, nome e os botões do próprio app de chamadas (recusar em vermelho, atender em verde). */
@Composable
private fun ChamadaRecebendo(l: LiveInfo, ui: UiState, onAction: (String) -> Unit) {
    // Pulso suave na foto enquanto toca
    val p = rememberInfiniteTransition(label = "toque")
    val pulso by p.animateFloat(1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulso")
    Row(
        Modifier.fillMaxSize().padding(
            start = MARGEM_INTERNA.dp, end = 14.dp, top = ui.topo.dp, bottom = MARGEM_INTERNA.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.graphicsLayer { scaleX = pulso; scaleY = pulso }) { IconeApp(l.icon, 46.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(l.text.ifBlank { l.app }, color = Cinza, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                l.title.ifBlank { "Chamada recebida" }, color = Color.White, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        l.actions.forEachIndexed { i, a ->
            Spacer(Modifier.width(10.dp))
            when (corDaAcaoDeChamada(a.title.toString())) {
                -1 -> BotaoRedondo(Glifo.DESLIGAR, Color.White, Vermelho, 46.dp) { onAction("live:$i") }
                1 -> BotaoRedondo(Glifo.TELEFONE, Color.White, Verde, 46.dp) { onAction("live:$i") }
                else -> Pilula(a.title.toString(), Cinza) { onAction("live:$i") }
            }
        }
    }
}

/** Aviso rápido: carregador, bateria fraca, carga completa, temporizador concluído. */
@Composable
private fun AlertaCompacto(a: Alerta) {
    val cor = Color(a.cor)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (a.tipo) {
            AlertaTipo.TIMER -> Desenho(Glifo.TIMER, cor, 16.dp)
            AlertaTipo.BAIXA -> {}
            else -> Desenho(Glifo.RAIO, cor, 14.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(a.titulo, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(a.valor, color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Numeros, maxLines = 1)
        if (a.tipo != AlertaTipo.TIMER) {
            Spacer(Modifier.width(6.dp))
            Bateria(a.nivel, cor)
        }
    }
}

// ---------- ilha dividida ----------

/**
 * Bolinha da ilha dividida: uma notificação de passagem ou a segunda atividade.
 * Ela "nasce" de dentro da borda direita da ilha principal (pequena e invisível)
 * e se separa até a posição final, como uma gota se soltando.
 */
@Composable
private fun Bolinha(ui: UiState, wMain: Dp, dx: Dp, y: Float, onAction: (String) -> Unit) {
    val acao by rememberUpdatedState(onAction)
    var ultima by remember { mutableStateOf<Bolha?>(null) }
    if (ui.bolha != null) ultima = ui.bolha
    val ativa = ui.bolha != null && ui.modo.compactoComAtividade()

    val sep by animateFloatAsState(
        if (ativa) 1f else 0f,
        if (ui.animBolha) spring<Float>(dampingRatio = ui.amortecimento, stiffness = ui.rigidez) else snap<Float>(),
        label = "separacao",
    )
    val b0 = ultima ?: return
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
            .background(Color.Black.copy(alpha = ui.opacidade))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { acao("bolha") }, onLongPress = { acao("bolha_abrir") })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Conteúdo só aparece depois que a bolinha já se separou ~60%
        Box(Modifier.graphicsLayer { alpha = ((sep - 0.6f) / 0.4f).coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
            val l = b0.live
            val m = b0.musica
            when {
                m != null -> Capa(m, 20.dp, 10.dp)
                l != null && (l.proprio != null || l.tipo == Tipo.CHAMADA) -> IconeLive(l, 20.dp)
                l != null && (l.progresso != null || l.indeterminado) -> Box(contentAlignment = Alignment.Center) {
                    Anel(l.progresso, Color(l.accent), 24.dp)
                    IconeApp(b0.icone, 14.dp)
                }
                else -> IconeApp(b0.icone, 20.dp)
            }
        }
    }
    if (b0.contagem > 1 && b0.live == null && b0.musica == null) {
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
            Text("${b0.contagem}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- notificação ----------

@Composable
private fun IconeCompartilhado(ui: UiState) {
    var ultimo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val n = ui.notif
    if (n != null) ultimo = n.appIcon ?: n.icon
    val ativo = n != null && (ui.modo == Modo.NOTIF || ui.modo == Modo.NOTIF_C || ui.modo == Modo.NOTIF_X)
    val cartao = ui.modo == Modo.NOTIF_X

    val tam = if (cartao) 18f else 20f * ui.escalaIcones
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

/** Faixa larga: ícone e "Nome (1)" à esquerda, "Ver" na cor do app à direita. */
@Composable
private fun FaixaNotif(n: NotifInfo, contagem: Int) {
    Row(
        Modifier.fillMaxSize().padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(20.dp + 8.dp)) // lugar do ícone compartilhado
        val qtd = contagem.coerceAtLeast(1)
        Text(
            "${n.title} ($qtd)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("Ver", color = Color(n.accent), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
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
                        Pilula(a.title.toString(), accent) { onAction("acao:$i") }
                    }
                }
            }
        }
    }
}
