package com.jvsleiman.ilha

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun acessibilidadeAtiva(ctx: Context): Boolean {
    val lista = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val meu = ComponentName(ctx, IslandService::class.java)
    return lista.split(':').any { ComponentName.unflattenFromString(it) == meu }
}

fun notificacoesAtivas(ctx: Context): Boolean =
    (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
        .contains(ctx.packageName)

fun bateriaLivre(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

class MainActivity : ComponentActivity() {
    // Muda a cada volta para o app, para reler o estado das permissões
    private var retorno by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Tema { Tela(retorno) } }
    }

    override fun onResume() {
        super.onResume()
        retorno++
    }
}

@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val escuro = isSystemInDarkTheme()
    val cores: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        escuro -> darkColorScheme(primary = Color(0xFFF5A524))
        else -> lightColorScheme(primary = Color(0xFFB87400))
    }
    MaterialTheme(colorScheme = cores, content = conteudo)
}

@Composable
fun Tela(retorno: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var cfg by remember { mutableStateOf(Prefs.read(prefs)) }

    fun salvar(novo: Config) {
        cfg = novo
        Prefs.write(prefs, novo) // o serviço recebe na hora e reposiciona a ilha
    }

    // "retorno" força reler as permissões quando você volta das configurações
    val acess = remember(retorno) { acessibilidadeAtiva(ctx) }
    val notif = remember(retorno) { notificacoesAtivas(ctx) }
    val bat = remember(retorno) { bateriaLivre(ctx) }
    val pkg = Uri.parse("package:${ctx.packageName}")

    Scaffold { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text("Ilha Dinâmica", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Etapa 1 · ilha parada sobre a câmera",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Secao("Permissões") {
                    Permissao(
                        titulo = "Acessibilidade",
                        desc = "Obrigatória. É o que desenha a ilha por cima da barra.",
                        ok = acess,
                        acao = "Ativar",
                    ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    HorizontalDivider()
                    Permissao(
                        titulo = "Acesso às notificações",
                        desc = "Para música, chamadas e timer (a partir da etapa 2).",
                        ok = notif,
                        acao = "Ativar",
                    ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    HorizontalDivider()
                    Permissao(
                        titulo = "Bateria sem restrição",
                        desc = "Evita que o Motorola feche a ilha em segundo plano.",
                        ok = bat,
                        acao = "Liberar",
                    ) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                        )
                    }
                }
            }

            if (!acess || !notif) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("A opção aparece bloqueada?", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Apps instalados por APK vêm com essas permissões travadas. " +
                                    "Abra as informações do app, toque nos ⋮ no canto de cima e " +
                                    "escolha \"Permitir configurações restritas\". Depois tente de novo.",
                                fontSize = 14.sp,
                            )
                            OutlinedButton(onClick = {
                                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
                            }) { Text("Abrir informações do app") }
                        }
                    }
                }
            }

            item {
                Secao("Ajustes da ilha") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mostrar ilha", Modifier.weight(1f))
                        Switch(checked = cfg.enabled, onCheckedChange = { salvar(cfg.copy(enabled = it)) })
                    }
                    Ajuste("Largura", cfg.width, 60f..180f, "dp") { salvar(cfg.copy(width = it)) }
                    Ajuste("Altura", cfg.height, 20f..48f, "dp") { salvar(cfg.copy(height = it)) }
                    Ajuste("Posição horizontal", cfg.offsetX, -40f..40f, "dp") { salvar(cfg.copy(offsetX = it)) }
                    Ajuste("Posição vertical", cfg.offsetY, -30f..30f, "dp") { salvar(cfg.copy(offsetY = it)) }
                    TextButton(onClick = { salvar(Config(enabled = cfg.enabled)) }) {
                        Text("Restaurar padrão")
                    }
                }
            }

            item {
                Text(
                    "Mexa nos ajustes e veja a ilha mudar na hora. Toque nela para testar a " +
                        "animação de expandir; toque fora para recolher.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

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
fun Permissao(titulo: String, desc: String, ok: Boolean, acao: String, abrir: () -> Unit) {
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
}

@Composable
fun Ajuste(
    nome: String,
    valor: Float,
    faixa: ClosedFloatingPointRange<Float>,
    unidade: String,
    mudar: (Float) -> Unit,
) {
    Column {
        Row {
            Text(nome, Modifier.weight(1f))
            Text(
                "${valor.roundToInt()} $unidade",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = valor,
            onValueChange = { mudar(it.roundToInt().toFloat()) },
            valueRange = faixa,
        )
    }
}
