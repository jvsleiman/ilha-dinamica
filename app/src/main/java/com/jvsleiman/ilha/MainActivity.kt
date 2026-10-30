package com.jvsleiman.ilha

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

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

fun avisosPermitidos(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

enum class Aba(val titulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    PERSONALIZAR("Personalizar", Icons.Filled.Edit),
    ATIVIDADES("Atividades", Icons.Filled.PlayArrow),
    AJUSTES("Ajustes", Icons.Filled.Settings),
    ATUALIZAR("Atualizar", Icons.Filled.Refresh),
}

class MainActivity : ComponentActivity() {
    // Muda a cada volta para o app, para reler o estado das permissões
    private var retorno by mutableIntStateOf(0)
    private var aba by mutableStateOf(Aba.INICIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getStringExtra("aba") == "atualizar") aba = Aba.ATUALIZAR
        setContent { Tema { App(retorno, aba) { aba = it } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("aba") == "atualizar") aba = Aba.ATUALIZAR
    }

    override fun onResume() {
        super.onResume()
        retorno++
    }

    override fun onStop() {
        // Sair do app encerra o modo de edição (a ilha volta a responder aos toques normalmente)
        Prefs.get(this).edit().putBoolean(Prefs.EDITANDO, false).apply()
        super.onStop()
    }
}

@Composable
fun App(retorno: Int, aba: Aba, trocar: (Aba) -> Unit) {
    val ajustes = rememberAjustes()
    val ctx = LocalContext.current
    // Saiu da aba Personalizar: desliga o modo de edição
    LaunchedEffect(aba) {
        if (aba != Aba.PERSONALIZAR) Prefs.get(ctx).edit().putBoolean(Prefs.EDITANDO, false).apply()
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach { a ->
                    NavigationBarItem(
                        selected = a == aba,
                        onClick = { trocar(a) },
                        icon = { Icon(a.icone, contentDescription = a.titulo) },
                        label = { Text(a.titulo, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (aba) {
                Aba.INICIO -> telaInicio(retorno, ajustes)
                Aba.PERSONALIZAR -> telaPersonalizar(ajustes)
                Aba.ATIVIDADES -> telaAtividades(ajustes)
                Aba.AJUSTES -> telaAjustes(retorno, ajustes)
                Aba.ATUALIZAR -> item { TelaAtualizacoes(ajustes) }
            }
        }
    }
}

@Composable
fun Cabecalho(titulo: String, sub: String) {
    Column {
        Text(titulo, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Início ----------

fun LazyListScope.telaInicio(retorno: Int, a: Ajustes) {
    item {
        val ctx = LocalContext.current
        Cabecalho("D.I.A", "Dynamic Island para Android · v${remember { Updater.installedName(ctx) }}")
    }
    item {
        val cfg = a.cfg
        val servico by IslandBus.serviceOn.collectAsState()
        Secao("Ilha") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar a ilha", fontWeight = FontWeight.Medium)
                    Text(
                        if (!servico) "Serviço desligado: ative a acessibilidade abaixo"
                        else if (cfg.enabled) "Funcionando" else "Pausada",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = cfg.enabled, onCheckedChange = { a.salvar(cfg.copy(enabled = it)) })
            }
            var comAtividade by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1800)
                    comAtividade = !comAtividade
                }
            }
            PreviaIlha(cfg, comAtividade)
            Subtitulo("Testar na ilha de verdade")
            Row(
                Modifier.fillMaxWidth().horizontalRolagem(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "notif" to "Notificação", "carregando" to "Carregador", "bateria" to "Bateria fraca",
                    "timer" to "Timer 1 min", "musica" to "Música", "expandir" to "Expandir",
                ).forEach { (k, n) ->
                    OutlinedButton(onClick = { demo(k) }) { Text(n) }
                }
            }
        }
    }
    item { Permissoes(retorno) }
    item { Diagnostico() }
}

/** Demonstrações: mandam eventos reais para a ilha (sem inventar dados de outros apps). */
fun demo(k: String) {
    if (k == "musica") {
        val m = IslandBus.music.value
        if (m != null && m.controller == null) IslandBus.music.value = null
        else IslandBus.music.value = MusicInfo(
            title = "Música de teste", artist = "D.I.A", art = null,
            accent = 0xFFFF9F7A.toInt(), playing = true, position = 0L,
            positionTime = SystemClock.elapsedRealtime(), speed = 1f,
            duration = 180_000L, controller = null,
        )
        return
    }
    IslandBus.demo.tryEmit(k)
}

@Composable
fun Permissoes(retorno: Int) {
    val ctx = LocalContext.current
    val acess = remember(retorno) { acessibilidadeAtiva(ctx) }
    val notif = remember(retorno) { notificacoesAtivas(ctx) }
    val bat = remember(retorno) { bateriaLivre(ctx) }
    var avisos by remember(retorno) { mutableStateOf(avisosPermitidos(ctx)) }
    val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
    val pkg = Uri.parse("package:${ctx.packageName}")

    Secao("Permissões") {
        Permissao(
            titulo = "Acessibilidade",
            desc = "Obrigatória. É o que desenha a ilha por cima da barra.",
            porQue = "Só o serviço de acessibilidade pode desenhar sobre a barra de status, em volta da câmera. " +
                "O D.I.A não lê o conteúdo das telas nem o que você digita.",
            ok = acess,
            acao = "Ativar",
        ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        HorizontalDivider()
        Permissao(
            titulo = "Acesso às notificações",
            desc = "Para música, notificações, chamadas e atividades.",
            porQue = "Lê as sessões de mídia (capa, faixa, controles) e as notificações para mostrar na ilha. " +
                "Tudo fica no celular; nada é enviado para servidores.",
            ok = notif,
            acao = "Ativar",
        ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        HorizontalDivider()
        Permissao(
            titulo = "Bateria sem restrição",
            desc = "Evita que o sistema feche a ilha em segundo plano.",
            porQue = "Fabricantes como Motorola, Xiaomi e Samsung encerram apps em segundo plano. " +
                "Liberar a bateria mantém a ilha estável; o consumo continua baixo porque ela só redesenha quando algo muda.",
            ok = bat,
            acao = "Liberar",
        ) { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)) }
        if (Build.VERSION.SDK_INT >= 33) {
            HorizontalDivider()
            Permissao(
                titulo = "Notificações do D.I.A",
                desc = "Opcional. Só para avisar de versões novas.",
                porQue = "Usada apenas pela verificação automática de atualizações.",
                ok = avisos,
                acao = "Permitir",
            ) { pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    if (!acess || !notif) {
        Card(
            Modifier.padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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

@Composable
fun Diagnostico() {
    val servico by IslandBus.serviceOn.collectAsState()
    val leitor by IslandBus.listenerOn.collectAsState()
    val musica by IslandBus.music.collectAsState()
    val lives by IslandBus.lives.collectAsState()
    val evento by IslandBus.lastEvent.collectAsState()
    val visib by IslandBus.visibilidade.collectAsState()
    val ctx = LocalContext.current
    var erro by remember { mutableStateOf(Prefs.get(ctx).getString(App.ERRO, null)) }

    Secao("Diagnóstico") {
        Linha("Serviço da ilha", if (servico) "Ligado" else "Desligado", servico)
        if (servico) Text(visib, fontSize = 13.sp, color = if (visib == "Visível") Color(0xFF34C759) else MaterialTheme.colorScheme.error)
        Linha("Leitor de notificações", if (leitor) "Conectado" else "Desconectado", leitor)
        val m = musica
        Linha(
            "Música detectada",
            if (m == null) "Nenhuma" else "${m.title} · ${if (m.playing) "tocando" else "pausada"}",
            m != null,
        )
        Linha(
            "Atividades em andamento",
            if (lives.isEmpty()) "Nenhuma" else lives.values.joinToString { it.app },
            lives.isNotEmpty(),
        )
        Text(
            "Último evento: $evento", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val e = erro
        if (e != null) {
            Subtitulo("Último erro")
            Text(e.take(1500), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Erro D.I.A", e))
                }) { Text("Copiar erro") }
                TextButton(onClick = { Prefs.get(ctx).edit().remove(App.ERRO).apply(); erro = null }) { Text("Limpar") }
            }
        }
    }
}

// ---------- Atualizações ----------

@Composable
fun TelaAtualizacoes(a: Ajustes) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val atual = remember { Updater.installedBuild(ctx) }
    val nome = remember { Updater.installedName(ctx) }
    var rel by remember { mutableStateOf<Updater.Release?>(null) }
    var status by remember { mutableStateOf("Toque em Verificar atualizações") }
    var baixando by remember { mutableStateOf<Float?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var historico by remember { mutableStateOf<List<Updater.Release>?>(null) }
    var fonte by remember { mutableStateOf(a.cfg.fonteAtualizacao) }
    var avisos by remember { mutableStateOf(avisosPermitidos(ctx)) }
    val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }

    fun verificar() {
        scope.launch {
            ocupado = true
            status = "Procurando atualização…"
            try {
                val r = Updater.latest(a.cfg.fonteAtualizacao)
                rel = r
                status = if (r.build > atual) "Nova versão disponível: ${r.name}"
                else "Você está na versão mais recente (${nome})"
            } catch (e: Exception) {
                rel = null
                status = e.message ?: "Erro ao procurar atualização"
            } finally {
                ocupado = false
            }
        }
    }

    LaunchedEffect(Unit) { verificar() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Cabecalho("Atualizações", "Versão instalada: $nome (compilação $atual)")

        Secao("Verificar") {
            Button(
                onClick = { verificar() },
                enabled = !ocupado && baixando == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verificar atualizações") }
            Text(status, fontSize = 14.sp)
            val p = baixando
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            val r = rel
            if (r != null && r.build > atual) {
                if (r.notas.isNotBlank()) {
                    Subtitulo("Novidades")
                    Text(r.notas, fontSize = 14.sp)
                }
                if (r.apkUrl == null) {
                    Text("Esta versão ainda não tem APK publicado.", fontSize = 13.sp)
                } else if (p == null) {
                    Button(onClick = {
                        if (!ctx.packageManager.canRequestPackageInstalls()) {
                            status = "Permita \"Instalar apps desconhecidos\" para o D.I.A e toque de novo"
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
                            )
                        } else {
                            scope.launch {
                                try {
                                    baixando = 0f
                                    status = "Baixando…"
                                    val f = Updater.download(ctx, r.apkUrl) { baixando = it }
                                    status = "Conferindo integridade e assinatura…"
                                    Updater.verificar(ctx, f, r.digest)
                                    status = "Confirme a instalação na tela do Android"
                                    Updater.install(ctx, f)
                                } catch (e: Exception) {
                                    status = e.message ?: "Erro ao atualizar"
                                } finally {
                                    baixando = null
                                }
                            }
                        }
                    }) { Text("Baixar e instalar") }
                }
            }
            Text(
                "O APK é baixado por HTTPS, conferido (SHA-256 e assinatura igual à do app instalado) " +
                    "e instalado pelo instalador do Android, que sempre pede sua confirmação.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Secao("Verificação automática") {
            Text(
                "Consulta em segundo plano, só com internet, e avisa por notificação. Nunca instala sozinha.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Desligada", 1 to "Diária", 7 to "Semanal").forEach { (d, n) ->
                    FilterChip(
                        selected = a.cfg.autoAtualizar == d,
                        onClick = {
                            a.salvar(a.cfg.copy(autoAtualizar = d))
                            Updater.agendar(ctx, d)
                            if (d > 0 && !avisos && Build.VERSION.SDK_INT >= 33) {
                                pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        label = { Text(n) },
                    )
                }
            }
            if (a.cfg.autoAtualizar > 0 && !avisos) {
                Text("Permita as notificações do D.I.A para receber o aviso.", fontSize = 12.sp)
            }
        }

        Secao("Histórico de alterações") {
            val h = historico
            if (h == null) {
                OutlinedButton(onClick = {
                    scope.launch {
                        historico = try { Updater.historico(a.cfg.fonteAtualizacao) } catch (e: Exception) {
                            status = e.message ?: "Erro"
                            emptyList()
                        }
                    }
                }) { Text("Carregar histórico") }
            } else if (h.isEmpty()) {
                Text("Nada para mostrar.", fontSize = 13.sp)
            } else {
                h.forEach { r ->
                    Column {
                        Row {
                            Text(r.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(r.data, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.notas.isNotBlank()) {
                            Text(r.notas, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Secao("Fonte das versões") {
            Text(
                "Repositório do GitHub (dono/nome) de onde as versões são lidas.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = fonte, onValueChange = { fonte = it.trim() }, singleLine = true,
                label = { Text("Repositório") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { a.salvar(a.cfg.copy(fonteAtualizacao = fonte)); historico = null; verificar() },
                    enabled = Regex("^[\\w.-]+/[\\w.-]+$").matches(fonte) && fonte != a.cfg.fonteAtualizacao,
                ) { Text("Salvar") }
                TextButton(onClick = { fonte = Config().fonteAtualizacao }) { Text("Usar a oficial") }
            }
        }
    }
}
