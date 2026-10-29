package com.jvsleiman.ilha

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mesma mola do protótipo (amortecimento 0.72, rigidez 380). */
private fun <T> mola() = spring<T>(dampingRatio = 0.72f, stiffness = 380f)

@Composable
fun Island(
    config: Config,
    expanded: Boolean,
    onTap: () -> Unit,
    onCollapsed: () -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val screenW = LocalConfiguration.current.screenWidthDp
    val expW = (screenW - 24).dp

    val w by animateDpAsState(
        targetValue = if (expanded) expW else config.width.dp,
        animationSpec = mola<Dp>(), label = "largura",
    )
    val h by animateDpAsState(
        targetValue = if (expanded) EXPANDED_H.dp else config.height.dp,
        animationSpec = mola<Dp>(), label = "altura",
        finishedListener = { if (!expanded) onCollapsed() },
    )
    val r by animateDpAsState(
        targetValue = if (expanded) 36.dp else (config.height / 2f).dp,
        animationSpec = mola<Dp>(), label = "cantos",
    )
    val conteudo by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) tween(260, delayMillis = 120) else tween(90),
        label = "conteudo",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(r))
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { tap() } },
        ) {
            if (conteudo > 0.01f) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .graphicsLayer {
                            alpha = conteudo
                            scaleX = 0.94f + 0.06f * conteudo
                            scaleY = 0.94f + 0.06f * conteudo
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFF34C759)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Ilha funcionando",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Toque fora para recolher",
                            color = Color(0xFF9A9AA2), fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}
