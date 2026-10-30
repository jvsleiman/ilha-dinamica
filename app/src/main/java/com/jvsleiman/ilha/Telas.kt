package com.jvsleiman.ilha

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun Modifier.horizontalRolagem(): Modifier = this.horizontalScroll(rememberScrollState())

// ---------- Personalizar ----------

fun LazyListScope.telaPersonalizar(a: Ajustes) {
    item { Cabecalho("Personalizar", "Posição, tamanho, aparência e animações. Muda na hora.") }

    item {
        val ctx = LocalContext.current
        val p = remember { Prefs.get(ctx) }
        var editando by remember { mutableStateOf(p.getBoolean(Prefs.EDITANDO, false)) }
        val tela by IslandBus.tela.collectAsState()
        Secao("Alinhamento") {
            Chave(
                "Arrastar a ilha na tela",
                "Com isto ligado, arraste a ilha (contorno amarelo) até ficar sobre a câmera.",
                editando,
            ) {
                editando = it
                p.edit().putBoolean(Prefs.EDITANDO, it).apply()
            }
            Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { a.salvar(a.cfg.copy(offsetX = 0f, offsetY = 0f)) }) { Text("Centralizar automaticamente") }
                OutlinedButton(onClick = { p.edit().putLong(Prefs.REDETECTAR, System.currentTimeMillis()).apply() }) {
                    Text("Detectar tela novamente")
                }
                OutlinedButton(onClick = { demo("notif") }) { Text("Testar animação") }
            }
            Text(tela, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Subtitulo("Ajuste fino")
            Ajuste("Horizontal (X)", a.cfg.offsetX, -120f..120f, { "${it.roundToInt()} dp" }) { a.salvar(a.cfg.copy(offsetX = it)) }
            Ajuste("Vertical (Y)", a.cfg.offsetY, -40f..80f, { "${it.roundToInt()} dp" }) { a.salvar(a.cfg.copy(offsetY = it)) }
        }
    }

    item { Favoritos(a) }

    item {
        val cfg = a.cfg
        Secao("Dimensões") {
            PreviaIlha(cfg, false)
            Subtitulo("Ilha parada")
            Ajuste("Largura", cfg.width, 40f..200f) { a.salvar(a.cfg.copy(width = it)) }
            Ajuste("Altura", cfg.height, 18f..56f) { a.salvar(a.cfg.copy(height = it)) }
            Ajuste("Arredondamento", cfg.cantos, 0.15f..1f, ::pct, 0.05f) { a.salvar(a.cfg.copy(cantos = it)) }
            Subtitulo("Com música ou atividade")
            Ajuste("Quanto estica", cfg.musicaExtra, 40f..240f) { a.salvar(a.cfg.copy(musicaExtra = it)) }
            Subtitulo("Expandida")
            Ajuste("Espaço acima da câmera", cfg.acima, 0f..24f) { a.salvar(a.cfg.copy(acima = it)) }
            Ajuste("Espaço abaixo da câmera", cfg.respiro, 0f..40f) { a.salvar(a.cfg.copy(respiro = it)) }
            Ajuste("Margem das laterais", cfg.margem, 4f..48f) { a.salvar(a.cfg.copy(margem = it)) }
            Ajuste("Raio dos cantos", cfg.raioExpandido, 16f..60f) { a.salvar(a.cfg.copy(raioExpandido = it)) }
        }
    }

    item {
        val cfg = a.cfg
        Secao("Aparência") {
            Ajuste("Opacidade", cfg.opacidade, 0.5f..1f, ::pct, 0.05f) { a.salvar(a.cfg.copy(opacidade = it)) }
            Text(
                "100% é o preto OLED puro, igual à câmera. Menos que isso deixa ver o fundo.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Ajuste("Tamanho dos textos", cfg.tamanhoTexto, 0.8f..1.3f, ::pct, 0.05f) { a.salvar(a.cfg.copy(tamanhoTexto = it)) }
            Ajuste("Escala dos ícones", cfg.escalaIcones, 0.7f..1.3f, ::pct, 0.05f) { a.salvar(a.cfg.copy(escalaIcones = it)) }
        }
    }

    item {
        val cfg = a.cfg
        Secao("Animações") {
            Ajuste("Velocidade", cfg.velocidade, 0.5f..1.8f, ::pct, 0.05f) { a.salvar(a.cfg.copy(velocidade = it)) }
            Ajuste("Efeito elástico", cfg.elasticidade, 0f..1f, ::pct, 0.05f) { a.salvar(a.cfg.copy(elasticidade = it)) }
            Ajuste("Notificação aberta por", cfg.tempoNotif, 1.5f..8f, ::seg, 0.5f) { a.salvar(a.cfg.copy(tempoNotif = it)) }
            Ajuste("Expandida recolhe após", cfg.tempoExpandida, 4f..30f, ::seg, 1f) { a.salvar(a.cfg.copy(tempoExpandida = it)) }
            Chave("Ondinha da música animada", valor = cfg.animOnda) { a.salvar(a.cfg.copy(animOnda = it)) }
            Chave("Separação animada da bolinha", valor = cfg.animBolha) { a.salvar(a.cfg.copy(animBolha = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { demo("expandir") }) { Text("Testar expansão") }
                TextButton(onClick = {
                    val d = Config()
                    a.salvar(
                        Prefs.aplicarMedidas(a.cfg, d).copy(
                            opacidade = d.opacidade, tamanhoTexto = d.tamanhoTexto, escalaIcones = d.escalaIcones,
                            velocidade = d.velocidade, elasticidade = d.elasticidade, tempoNotif = d.tempoNotif,
                            tempoExpandida = d.tempoExpandida, animOnda = d.animOnda, animBolha = d.animBolha,
                        ),
                    )
                }) { Text("Restaurar padrão") }
            }
        }
    }
}

@Composable
fun Favoritos(a: Ajustes) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var meus by remember { mutableStateOf(Prefs.favoritos(prefs)) }
    var nome by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var removendo by remember { mutableStateOf(false) }
    val atual = a.cfg

    Secao("Favoritos") {
        Text(
            if (removendo) "Toque num favorito seu para apagar."
            else "Toque para aplicar as medidas. Salve o ajuste atual para usar depois.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRONTOS.forEach { f ->
                FilterChip(
                    selected = f.config.mesmasMedidas(atual),
                    onClick = { if (!removendo) a.salvar(Prefs.aplicarMedidas(atual, f.config)) },
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
                        } else a.salvar(Prefs.aplicarMedidas(atual, f.config))
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
                        meus = meus + Favorito(nome.trim(), atual)
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

// ---------- Atividades ----------

fun LazyListScope.telaAtividades(a: Ajustes) {
    item { Cabecalho("Atividades", "Temporizador, integrações, prioridades e apps permitidos.") }
    item { Temporizador() }

    item {
        val cfg = a.cfg
        Secao("Integrações") {
            Chave("Música", "Spotify, YouTube Music e qualquer player com sessão de mídia: capa, controles e progresso.", cfg.musica) {
                a.salvar(a.cfg.copy(musica = it))
            }
            Chave("Notificações", "WhatsApp, Telegram, Instagram, SMS, Gmail, bancos e outros apps permitidos.", cfg.notificacoes) {
                a.salvar(a.cfg.copy(notificacoes = it))
            }
            Chave("Chamadas", "Telefone, WhatsApp e outros apps que publicam a chamada. Atender/recusar usam os botões do próprio app.", cfg.chamadas) {
                a.salvar(a.cfg.copy(chamadas = it))
            }
            Chave("Carregamento e bateria", "Aviso ao ligar o carregador, bateria fraca e carga completa.", cfg.bateria) {
                a.salvar(a.cfg.copy(bateria = it))
            }
            Chave("Temporizadores e cronômetros", "O do D.I.A e os do app Relógio que mostram a contagem na notificação.", cfg.timer) {
                a.salvar(a.cfg.copy(timer = it))
            }
            Chave("Atividades em andamento", "Navegação, downloads com progresso, gravação de tela ou áudio — quando o app informa.", cfg.atividades) {
                a.salvar(a.cfg.copy(atividades = it))
            }
        }
    }

    item {
        val cfg = a.cfg
        Secao("Prioridade") {
            Text(
                "Quando várias rodam ao mesmo tempo, a primeira fica na ilha e a segunda vai para a bolinha. " +
                    "Deslize a ilha para alternar. Chamada tocando sempre aparece por cima.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cfg.prioridades.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${t.rotulo}", Modifier.weight(1f))
                    IconButton(onClick = { a.salvar(a.cfg.copy(prioridades = moverPrioridade(a.cfg.prioridades, t, -1))) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir")
                    }
                    IconButton(
                        onClick = { a.salvar(a.cfg.copy(prioridades = moverPrioridade(a.cfg.prioridades, t, 1))) },
                        enabled = i < cfg.prioridades.lastIndex,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descer")
                    }
                }
            }
            Text(
                "Notificações nunca interrompem uma atividade: aparecem na bolinha ao lado por alguns segundos.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item { AppsPermitidos() }
}

@Composable
fun Temporizador() {
    val t by IslandBus.timer.collectAsState()
    val servico by IslandBus.serviceOn.collectAsState()
    var minutos by remember { mutableFloatStateOf(5f) }
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(t) {
        while (t != null) {
            agora = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    Secao("Temporizador do D.I.A") {
        val atual = t
        if (atual != null) {
            Text(
                atual.texto(agora), fontSize = 44.sp, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                (if (atual.cronometro) "Cronômetro" else "Temporizador") + if (atual.pausado) " · pausado" else "",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { IslandBus.pausarOuRetomar() }) { Text(if (atual.pausado) "Retomar" else "Pausar") }
                OutlinedButton(onClick = { IslandBus.cancelarTimer() }) { Text("Cancelar") }
            }
        } else {
            Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 15, 30, 60).forEach { m ->
                    FilterChip(selected = minutos == m.toFloat(), onClick = { minutos = m.toFloat() }, label = { Text("$m min") })
                }
            }
            Ajuste("Duração", minutos, 1f..120f, { "${it.roundToInt()} min" }) { minutos = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { IslandBus.iniciarTimer((minutos * 60_000).toLong()) }) { Text("Iniciar temporizador") }
                OutlinedButton(onClick = { IslandBus.iniciarCronometro() }) { Text("Cronômetro") }
            }
        }
        if (!servico) {
            Text(
                "A ilha precisa estar ativa (acessibilidade) para o temporizador tocar ao terminar.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private data class AppItem(val pkg: String, val nome: String, val icone: Bitmap?, val visto: Boolean)

@Composable
fun AppsPermitidos() {
    val ctx = LocalContext.current
    val p = remember { Prefs.get(ctx) }
    var aberto by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var bloqueados by remember { mutableStateOf(Prefs.bloqueados(p)) }
    var busca by remember { mutableStateOf("") }

    LaunchedEffect(aberto) {
        if (!aberto || apps != null) return@LaunchedEffect
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val vistos = Prefs.vistos(p)
            val lancadores = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL,
            ).map { it.activityInfo.packageName }.toSet()
            (lancadores + vistos).filter { it != ctx.packageName }.mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    AppItem(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(48, 48), pkg in vistos)
                } catch (e: Exception) {
                    null
                }
            }.sortedWith(compareByDescending<AppItem> { it.visto }.thenBy { it.nome.lowercase() })
        }
    }

    Secao("Apps permitidos") {
        Text(
            "Escolha quais apps podem usar a ilha (notificações, música e atividades). " +
                "${bloqueados.size} bloqueado(s).",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!aberto) {
            OutlinedButton(onClick = { aberto = true }) { Text("Escolher apps") }
        } else {
            OutlinedTextField(
                value = busca, onValueChange = { busca = it }, singleLine = true,
                label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth(),
            )
            val lista = apps
            if (lista == null) {
                Text("Carregando apps…", fontSize = 13.sp)
            } else {
                lista.filter { busca.isBlank() || it.nome.contains(busca, ignoreCase = true) }.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bmp = app.icone
                        if (bmp != null) {
                            Image(remember(bmp) { bmp.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(32.dp))
                        } else {
                            Spacer(Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(app.nome, maxLines = 1)
                            if (app.visto) Text("Já mandou notificação", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = app.pkg !in bloqueados,
                            onCheckedChange = { permitido ->
                                Prefs.bloquear(p, app.pkg, !permitido)
                                bloqueados = Prefs.bloqueados(p)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------- Ajustes ----------

fun LazyListScope.telaAjustes(retorno: Int, a: Ajustes) {
    item { Cabecalho("Ajustes", "Comportamento, inicialização, privacidade e backup.") }

    item {
        val cfg = a.cfg
        Secao("Comportamento") {
            Chave("Esconder em tela cheia", "Vídeos e jogos sem barra de status. Chamada tocando aparece mesmo assim.", cfg.esconderTelaCheia) {
                a.salvar(a.cfg.copy(esconderTelaCheia = it))
            }
            Chave("Esconder com o celular deitado", valor = cfg.esconderPaisagem) { a.salvar(a.cfg.copy(esconderPaisagem = it)) }
            Chave("Deslizar troca de música", "Com só a música na ilha, deslizar passa ou volta a faixa.", cfg.deslizarTrocaMusica) {
                a.salvar(a.cfg.copy(deslizarTrocaMusica = it))
            }
        }
    }

    item {
        val cfg = a.cfg
        Secao("Privacidade") {
            Chave(
                "Ocultar conteúdo na tela bloqueada",
                "Mostra só o nome do app até desbloquear. Notificações marcadas como secretas nunca aparecem.",
                cfg.privacidade,
            ) { a.salvar(a.cfg.copy(privacidade = it)) }
            Text(
                "O D.I.A não tem servidor: mensagens, contatos e notificações são processados só no celular. " +
                    "A única conexão é a consulta de versões no GitHub.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item {
        val ctx = LocalContext.current
        val cfg = a.cfg
        val bat = remember(retorno) { bateriaLivre(ctx) }
        Secao("Inicialização e bateria") {
            Chave(
                "Ligar a ilha ao reiniciar o celular",
                "O Android religa a ilha sozinho depois de reiniciar. Desligado, ela volta pausada.",
                cfg.ligarAoReiniciar,
            ) { a.salvar(a.cfg.copy(ligarAoReiniciar = it)) }
            Linha("Bateria sem restrição", if (bat) "Liberada" else "Restrita", bat)
            Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                    )
                }) { Text("Liberar bateria") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Informações do app") }
            }
            Text(
                "Motorola: em Bateria, deixe o D.I.A como \"Sem restrições\". Xiaomi: ative também \"Início automático\". " +
                    "Samsung: tire o app de \"Apps em suspensão\".",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item {
        val ctx = LocalContext.current
        val p = remember { Prefs.get(ctx) }
        var confirmar by remember { mutableStateOf(false) }
        val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Prefs.exportar(p).toByteArray()) }
                Toast.makeText(ctx, "Backup salvo", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Não consegui salvar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            try {
                val texto = ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                Prefs.importar(p, texto)
                a.recarregar()
                Toast.makeText(ctx, "Configurações restauradas", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "Backup inválido", Toast.LENGTH_LONG).show()
            }
        }
        Secao("Backup local") {
            Text(
                "Salva todos os ajustes, favoritos e apps bloqueados num arquivo do celular.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportar.launch("dia-backup.json") }) { Text("Exportar") }
                OutlinedButton(onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restaurar") }
            }
            HorizontalDivider()
            TextButton(onClick = { confirmar = true }) {
                Text("Restaurar todas as configurações", color = MaterialTheme.colorScheme.error)
            }
        }
        if (confirmar) {
            AlertDialog(
                onDismissRequest = { confirmar = false },
                title = { Text("Restaurar tudo?") },
                text = { Text("Todos os ajustes voltam ao padrão. Favoritos e apps bloqueados são mantidos.") },
                confirmButton = {
                    TextButton(onClick = { a.salvar(Config()); confirmar = false }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
            )
        }
    }

    item {
        Secao("Sobre") {
            Text(
                "D.I.A — Dynamic Island para Android. Inspirado na Dynamic Island do iPhone, com implementação própria " +
                    "e só APIs oficiais do Android. Limitações conhecidas por versão e fabricante estão em LIMITACOES.md no repositório.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
