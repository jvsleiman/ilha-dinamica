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
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

/** Abre uma tela do sistema sem derrubar o app se o fabricante não tiver aquela tela. */
fun abrirAjuste(ctx: Context, intent: Intent, alternativa: Intent? = null) {
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        try {
            ctx.startActivity(alternativa ?: Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Não consegui abrir os ajustes do Android", Toast.LENGTH_SHORT).show()
        }
    }
}

fun infoDoApp(ctx: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

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
        // A verificação automática vem ligada ("Diária"), mas só era agendada ao reiniciar o celular
        // ou ao tocar na opção. Agenda ao abrir o app, sem reiniciar a contagem se já existir.
        try {
            Updater.agendar(this, Prefs.read(Prefs.get(this)).autoAtualizar, manter = true)
        } catch (e: Exception) { IslandBus.log("Não consegui agendar a verificação: ${e.message}") }
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
    val servico by IslandBus.serviceOn.collectAsState()
    // Saiu da aba Personalizar: desliga o modo de edição
    LaunchedEffect(aba) {
        if (aba != Aba.PERSONALIZAR) Prefs.get(ctx).edit().putBoolean(Prefs.EDITANDO, false).apply()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Aba.entries.forEach { a ->
                    NavigationBarItem(
                        selected = a == aba,
                        onClick = { trocar(a) },
                        icon = { Icon(a.icone, contentDescription = a.titulo) },
                        label = { Text(a.titulo, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Fora do Início, avisa que nada do que se mexe aqui aparece sem o serviço
            if (!servico && aba != Aba.INICIO && aba != Aba.ATUALIZAR) {
                item {
                    Aviso(
                        "A ilha está desligada",
                        "Ative a Acessibilidade para ver as mudanças na tela.",
                        acao = "Ativar",
                    ) { trocar(Aba.INICIO) }
                }
            }
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

// ---------- Início ----------

fun LazyListScope.telaInicio(retorno: Int, a: Ajustes) {
    item { Destaque(a) }
    item { Testes(a) }
    item { Permissoes(retorno) }
    item { Diagnostico() }
}

/** Topo do Início: marca, versão, estado, prévia animada e o interruptor principal. */
@Composable
private fun Destaque(a: Ajustes) {
    val ctx = LocalContext.current
    val cfg = a.cfg
    val servico by IslandBus.serviceOn.collectAsState()
    val versao = remember { Updater.installedName(ctx) }
    var comAtividade by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2200)
            comAtividade = !comAtividade
        }
    }
    val (estado, cor) = when {
        !servico -> "Serviço desligado" to VermelhoErro
        !cfg.enabled -> "Pausada" to AmareloAviso
        else -> "Funcionando" to VerdeOk
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Marca: quadrado laranja com a "ilha" preta
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFC15A), LaranjaDia))),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(24.dp).height(9.dp).clip(CircleShape).background(Color.Black))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("D.I.A", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Dynamic Island para Android · v$versao", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Etiqueta(estado, cor)
        }
        Secao("Ilha", Icons.Filled.Star) {
            PreviaIlha(cfg, comAtividade && cfg.enabled)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar a ilha", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(
                        when {
                            !servico -> "Ative a Acessibilidade em Permissões, abaixo"
                            cfg.enabled -> "Aparecendo sobre a câmera"
                            else -> "Pausada: não aparece até você ligar"
                        },
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = cfg.enabled, onCheckedChange = { a.salvar(cfg.copy(enabled = it)) })
            }
        }
    }
}

@Composable
private fun Testes(a: Ajustes) {
    val ctx = LocalContext.current
    Secao("Testar na ilha de verdade", Icons.Filled.PlayArrow, "Manda um evento de teste para a ilha, sem mexer nos seus apps.") {
        val itens = listOf(
            Triple("notif", "Notificação", Icons.Filled.Notifications to AzulInfo),
            Triple("musica", "Música", Icons.Filled.Favorite to Color(0xFFFF7A9A)),
            Triple("expandir", "Expandir", Icons.Filled.Search to LaranjaDia),
            Triple("carregando", "Carregador", Icons.Filled.Build to VerdeOk),
            Triple("bateria", "Bateria fraca", Icons.Filled.Warning to VermelhoErro),
            Triple("timer", "Timer 1 min", Icons.Filled.DateRange to Color(0xFFFF9F0A)),
        )
        itens.chunked(3).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { (k, nome, ic) ->
                    Bloco(nome, ic.first, ic.second, Modifier.weight(1f)) { testar(ctx, a.cfg, k) }
                }
            }
        }
    }
}

/**
 * Demonstrações: mandam eventos reais para a ilha (sem inventar dados de outros apps).
 * Sem o serviço ligado nada aparece, então avisa em vez de "não responder".
 */
fun testar(ctx: Context, cfg: Config, k: String) {
    when {
        !IslandBus.serviceOn.value -> {
            Toast.makeText(ctx, "Ative a Acessibilidade (em Permissões) para a ilha aparecer", Toast.LENGTH_LONG).show()
            return
        }
        !cfg.enabled -> {
            Toast.makeText(ctx, "Ligue \"Mostrar a ilha\" para ver o teste", Toast.LENGTH_SHORT).show()
            return
        }
    }
    demo(k)
}

fun demo(k: String) {
    if (k == "musica") {
        val m = IslandBus.music.value
        // Segundo toque tira a música de teste
        IslandBus.music.value = if (m != null && m.controller == null) null else IslandBus.musicaDeTeste()
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

    val total = if (Build.VERSION.SDK_INT >= 33) 4 else 3
    val prontas = listOf(acess, notif, bat, avisos).take(total).count { it }
    var detalhes by remember { mutableStateOf(false) }
    val tudoPronto = prontas == total

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Secao(
            "Permissões", Icons.Filled.Lock,
            if (tudoPronto) "Tudo pronto" else "$prontas de $total prontas",
        ) {
            LinearProgressIndicator(
                progress = { prontas / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (tudoPronto) VerdeOk else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (tudoPronto && !detalhes) {
                TextButton(onClick = { detalhes = true }) { Text("Ver detalhes") }
            } else {
                Permissao(
                    titulo = "Acessibilidade",
                    desc = "Obrigatória. É o que desenha a ilha por cima da barra.",
                    porQue = "Só o serviço de acessibilidade pode desenhar sobre a barra de status, em volta da câmera. " +
                        "O D.I.A não lê o conteúdo das telas nem o que você digita.",
                    ok = acess,
                    acao = "Ativar",
                ) { abrirAjuste(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                Divisor()
                Permissao(
                    titulo = "Acesso às notificações",
                    desc = "Para música, notificações, chamadas e atividades.",
                    porQue = "Lê as sessões de mídia (capa, faixa, controles) e as notificações para mostrar na ilha. " +
                        "Tudo fica no celular; nada é enviado para servidores.",
                    ok = notif,
                    acao = "Ativar",
                ) { abrirAjuste(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                Divisor()
                Permissao(
                    titulo = "Bateria sem restrição",
                    desc = "Evita que o sistema feche a ilha em segundo plano.",
                    porQue = "Fabricantes como Motorola, Xiaomi e Samsung encerram apps em segundo plano. " +
                        "Liberar a bateria mantém a ilha estável; o consumo continua baixo porque ela só redesenha quando algo muda.",
                    ok = bat,
                    acao = "Liberar",
                ) {
                    abrirAjuste(
                        ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    Divisor()
                    Permissao(
                        titulo = "Notificações do D.I.A",
                        desc = "Opcional. Só para avisar de versões novas.",
                        porQue = "Usada apenas pela verificação automática de atualizações.",
                        ok = avisos,
                        acao = "Permitir",
                    ) { pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                if (tudoPronto) TextButton(onClick = { detalhes = false }) { Text("Esconder detalhes") }
            }
        }

        if (!acess || !notif) {
            Aviso(
                "A opção aparece bloqueada?",
                "Apps instalados por APK vêm com essas permissões travadas. Abra as informações do app, " +
                    "toque nos ⋮ no canto de cima e escolha \"Permitir configurações restritas\". Depois tente de novo.",
                cor = AzulInfo,
                acao = "Abrir",
            ) { abrirAjuste(ctx, infoDoApp(ctx)) }
        }
    }
}

@Composable
fun Diagnostico() {
    val servico by IslandBus.serviceOn.collectAsState()
    val leitor by IslandBus.listenerOn.collectAsState()
    val musica by IslandBus.music.collectAsState()
    val lives by IslandBus.lives.collectAsState()
    val historico by IslandBus.historico.collectAsState()
    val evento by IslandBus.lastEvent.collectAsState()
    val visib by IslandBus.visibilidade.collectAsState()
    val ctx = LocalContext.current
    var erro by remember { mutableStateOf(Prefs.get(ctx).getString(App.ERRO, null)) }
    var verRegistro by remember { mutableStateOf(false) }

    Secao("Diagnóstico", Icons.Filled.Info, "O que a ilha está vendo agora.") {
        Linha("Serviço da ilha", if (servico) "Ligado" else "Desligado", servico)
        if (servico) Linha("Na tela", visib, visib == "Visível")
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
        Divisor()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Último evento: $evento", fontSize = 12.sp, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
            if (historico.size > 1) {
                TextButton(onClick = { verRegistro = !verRegistro }) { Text(if (verRegistro) "Ocultar" else "Registro") }
            }
        }
        if (verRegistro) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                historico.forEach {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = NumerosFixos)
                }
            }
            OutlinedButton(onClick = {
                val texto = buildString {
                    append("D.I.A v${Updater.installedName(ctx)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Serviço: $servico · Leitor: $leitor · $visib\n")
                    append(IslandBus.tela.value).append("\n\n")
                    historico.forEach { append(it).append('\n') }
                }
                copiar(ctx, "Diagnóstico D.I.A", texto)
            }) { Text("Copiar diagnóstico") }
        }
        val e = erro
        if (e != null) {
            Subtitulo("Último erro")
            Text(
                e.take(1500), fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(VermelhoErro.copy(alpha = 0.08f)).padding(12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copiar(ctx, "Erro D.I.A", e) }) { Text("Copiar erro") }
                TextButton(onClick = { Prefs.get(ctx).edit().remove(App.ERRO).apply(); erro = null }) { Text("Limpar") }
            }
        }
    }
}

fun copiar(ctx: Context, rotulo: String, texto: String) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
    cm.setPrimaryClip(android.content.ClipData.newPlainText(rotulo, texto))
    // Android 13+ já mostra o próprio aviso de "copiado"
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
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
    var carregandoHistorico by remember { mutableStateOf(false) }
    var fonte by remember(a.geracao) { mutableStateOf(a.cfg.fonteAtualizacao) }
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
                else "Você está na versão mais recente ($nome)"
            } catch (e: Exception) {
                rel = null
                status = e.message ?: "Erro ao procurar atualização"
            } finally {
                ocupado = false
            }
        }
    }

    LaunchedEffect(Unit) { verificar() }

    val temNova = rel?.let { it.build > atual } == true

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Cabecalho("Atualizações", "Versão instalada: $nome (compilação $atual)")

        Secao("Verificar", Icons.Filled.Refresh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                when {
                    ocupado -> Etiqueta("Buscando", AzulInfo)
                    temNova -> Etiqueta("Nova", LaranjaDia)
                    rel != null -> Etiqueta("Em dia", VerdeOk)
                }
            }
            val p = baixando
            if (p != null) {
                LinearProgressIndicator(
                    progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            val r = rel
            if (r != null && temNova) {
                if (r.notas.isNotBlank()) {
                    Subtitulo("Novidades")
                    Text(
                        r.notas, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                    )
                }
                if (r.apkUrl == null) {
                    Text("Esta versão ainda não tem APK publicado.", fontSize = 13.sp)
                } else if (p == null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!ctx.packageManager.canRequestPackageInstalls()) {
                                status = "Permita \"Instalar apps desconhecidos\" para o D.I.A e toque de novo"
                                abrirAjuste(
                                    ctx,
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
                                    infoDoApp(ctx),
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
                        },
                    ) { Text("Baixar e instalar ${r.name}") }
                }
            }
            OutlinedButton(
                onClick = { verificar() },
                enabled = !ocupado && baixando == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ocupado) "Procurando…" else "Verificar atualizações") }
            Text(
                "O APK é baixado por HTTPS, conferido (SHA-256 e assinatura igual à do app instalado) " +
                    "e instalado pelo instalador do Android, que sempre pede sua confirmação.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Secao("Verificação automática", Icons.Filled.Notifications, "Consulta em segundo plano, só com internet. Nunca instala sozinha.") {
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
                Aviso("Sem permissão de notificação", "Permita as notificações do D.I.A para receber o aviso.", acao = "Permitir") {
                    if (Build.VERSION.SDK_INT >= 33) pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        Secao("Histórico de alterações", Icons.Filled.DateRange) {
            val h = historico
            when {
                h == null -> OutlinedButton(
                    enabled = !carregandoHistorico,
                    onClick = {
                        scope.launch {
                            carregandoHistorico = true
                            historico = try { Updater.historico(a.cfg.fonteAtualizacao) } catch (e: Exception) {
                                status = e.message ?: "Erro"
                                null
                            }
                            carregandoHistorico = false
                        }
                    },
                ) { Text(if (carregandoHistorico) "Carregando…" else "Carregar histórico") }
                h.isEmpty() -> Text("Nada para mostrar.", fontSize = 13.sp)
                else -> h.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape)
                                .background(if (r.build == atual) VerdeOk else if (i == 0) LaranjaDia else MaterialTheme.colorScheme.outline),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(r.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(r.data, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.build == atual) Text("Instalada", fontSize = 11.sp, color = VerdeOk)
                            if (r.notas.isNotBlank()) {
                                Text(r.notas, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6)
                            }
                        }
                    }
                    if (i < h.lastIndex) Divisor()
                }
            }
        }

        Secao("Fonte das versões", Icons.Filled.Settings, "Repositório do GitHub (dono/nome) de onde as versões são lidas.") {
            OutlinedTextField(
                value = fonte, onValueChange = { fonte = it.trim() }, singleLine = true,
                label = { Text("Repositório") }, modifier = Modifier.fillMaxWidth(),
                isError = fonte.isNotEmpty() && !Regex("^[\\w.-]+/[\\w.-]+$").matches(fonte),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { a.salvar(a.cfg.copy(fonteAtualizacao = fonte)); historico = null; verificar() },
                    enabled = Regex("^[\\w.-]+/[\\w.-]+$").matches(fonte) && fonte != a.cfg.fonteAtualizacao,
                ) { Text("Salvar") }
                TextButton(onClick = { fonte = Config().fonteAtualizacao }) { Text("Usar a oficial") }
            }
        }
    }
}
