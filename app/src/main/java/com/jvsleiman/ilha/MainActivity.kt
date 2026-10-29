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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.launch
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
import android.os.SystemClock
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
                        "Etapa 2 · música e notificações",
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
                        desc = "Obrigatório para música e notificações na ilha.",
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

            item { Atualizacao() }

            item { Diagnostico() }

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
                Favoritos(cfg) { salvar(it.copy(enabled = cfg.enabled)) }
            }

            item {
                Secao("Ajustes da ilha") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mostrar ilha", Modifier.weight(1f))
                        Switch(checked = cfg.enabled, onCheckedChange = { salvar(cfg.copy(enabled = it)) })
                    }
                    Subtitulo("Ilha parada")
                    Ajuste("Largura", cfg.width, 60f..180f, "dp") { salvar(cfg.copy(width = it)) }
                    Ajuste("Altura", cfg.height, 20f..48f, "dp") { salvar(cfg.copy(height = it)) }
                    Subtitulo("Posição")
                    Ajuste("Horizontal", cfg.offsetX, -60f..60f, "dp") { salvar(cfg.copy(offsetX = it)) }
                    Ajuste("Vertical", cfg.offsetY, -40f..40f, "dp") { salvar(cfg.copy(offsetY = it)) }
                    Subtitulo("Com música")
                    Ajuste("Quanto estica", cfg.musicaExtra, 60f..220f, "dp") { salvar(cfg.copy(musicaExtra = it)) }
                    Subtitulo("Expandida (notificações e música)")
                    Ajuste("Espaço acima da câmera", cfg.acima, 0f..24f, "dp") { salvar(cfg.copy(acima = it)) }
                    Ajuste("Espaço abaixo da câmera", cfg.respiro, 0f..40f, "dp") { salvar(cfg.copy(respiro = it)) }
                    Ajuste("Margem das laterais", cfg.margem, 4f..48f, "dp") { salvar(cfg.copy(margem = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            IslandBus.notifs.tryEmit(
                                NotifInfo("teste", "Ilha", "Pré-visualização", "Assim fica uma notificação na ilha", null, null),
                            )
                        }) { Text("Ver expandida") }
                        TextButton(onClick = { salvar(Config(enabled = cfg.enabled)) }) {
                            Text("Restaurar padrão")
                        }
                    }
                }
            }

            item {
                Text(
                    "Toque nela com uma música tocando para expandir; segure para abrir o app. " +
                        "Toque fora para recolher. Mexa nos ajustes e a ilha muda na hora.",
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

@Composable
fun Diagnostico() {
    val servico by IslandBus.serviceOn.collectAsState()
    val leitor by IslandBus.listenerOn.collectAsState()
    val musica by IslandBus.music.collectAsState()
    val evento by IslandBus.lastEvent.collectAsState()

    Secao("Diagnóstico") {
        Linha("Ilha desenhando", if (servico) "Sim" else "Não", servico)
        Linha("Leitor de notificações", if (leitor) "Conectado" else "Desconectado", leitor)
        val m = musica
        Linha(
            "Música detectada",
            if (m == null) "Nenhuma" else "${m.title} · ${if (m.playing) "tocando" else "pausada"}",
            m != null,
        )
        Text(
            "Último evento: $evento", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (IslandBus.music.value?.controller == null && IslandBus.music.value != null) {
                    IslandBus.music.value = null
                } else {
                    IslandBus.music.value = MusicInfo(
                        title = "Música de teste", artist = "Ilha Dinâmica", art = null,
                        accent = 0xFFFF9F7A.toInt(), playing = true, position = 0L,
                        positionTime = SystemClock.elapsedRealtime(), speed = 1f,
                        duration = 180_000L, controller = null,
                    )
                }
            }) { Text("Simular música") }
            OutlinedButton(onClick = {
                IslandBus.notifs.tryEmit(
                    NotifInfo("teste", "Ilha", "Notificação de teste", "Se você lê isto, a ilha funciona", null, null),
                )
            }) { Text("Simular aviso") }
        }
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
fun Atualizacao() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val atual = remember { Updater.installedBuild(ctx) }
    var rel by remember { mutableStateOf<Updater.Release?>(null) }
    var status by remember { mutableStateOf("Procurando atualização…") }
    var baixando by remember { mutableStateOf<Float?>(null) }

    fun verificar() {
        scope.launch {
            status = "Procurando atualização…"
            try {
                val r = Updater.latest()
                rel = r
                status = if (r.build > atual) "Nova versão disponível (compilação ${r.build})"
                else "Você está na versão mais recente (compilação $atual)"
            } catch (e: Exception) {
                rel = null
                status = e.message ?: "Erro ao procurar atualização"
            }
        }
    }

    LaunchedEffect(Unit) { verificar() }

    Secao("Atualização") {
        Text(status, fontSize = 14.sp)
        val p = baixando
        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val r = rel
            if (r != null && r.build > atual && p == null) {
                Button(onClick = {
                    if (!ctx.packageManager.canRequestPackageInstalls()) {
                        status = "Permita \"Instalar apps desconhecidos\" para a Ilha e toque de novo"
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${ctx.packageName}"),
                            ),
                        )
                    } else {
                        scope.launch {
                            try {
                                baixando = 0f
                                status = "Baixando…"
                                val f = Updater.download(ctx, r.apkUrl) { baixando = it }
                                status = "Instalando… o app vai fechar e reabrir atualizado"
                                Updater.install(ctx, f)
                            } catch (e: Exception) {
                                status = e.message ?: "Erro ao atualizar"
                            } finally {
                                baixando = null
                            }
                        }
                    }
                }) { Text("Atualizar agora") }
            }
            OutlinedButton(onClick = { verificar() }, enabled = p == null) { Text("Procurar de novo") }
        }
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

@Composable
fun Favoritos(atual: Config, aplicar: (Config) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var meus by remember { mutableStateOf(Prefs.favoritos(prefs)) }
    var nome by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var removendo by remember { mutableStateOf(false) }

    Secao("Favoritos") {
        Text(
            if (removendo) "Toque num favorito seu para apagar."
            else "Toque para aplicar. Salve o ajuste atual para usar depois.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRONTOS.forEach { f ->
                FilterChip(
                    selected = f.config.mesmasMedidas(atual),
                    onClick = { if (!removendo) aplicar(f.config) },
                    label = { Text(f.nome) },
                    enabled = !removendo,
                )
            }
            meus.forEachIndexed { i, f ->
                FilterChip(
                    selected = !removendo && f.config.mesmasMedidas(atual),
                    onClick = {
                        if (removendo) {
                            meus = meus.filterIndexed { j, _ -> j != i }
                            Prefs.salvarFavoritos(prefs, meus)
                            if (meus.isEmpty()) removendo = false
                        } else aplicar(f.config)
                    },
                    label = { Text(if (removendo) "✕  ${f.nome}" else f.nome) },
                )
            }
        }
        if (salvando) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it.take(20) },
                    label = { Text("Nome do favorito") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        meus = meus + Favorito(nome.trim(), atual.copy(enabled = true))
                        Prefs.salvarFavoritos(prefs, meus)
                        nome = ""
                        salvando = false
                    },
                    enabled = nome.isNotBlank(),
                ) { Text("Salvar") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { salvando = true; removendo = false }) { Text("Salvar atual") }
                if (meus.isNotEmpty()) {
                    TextButton(onClick = { removendo = !removendo }) {
                        Text(if (removendo) "Concluir" else "Apagar")
                    }
                }
            }
        }
    }
}
