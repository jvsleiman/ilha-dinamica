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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
        Secao("Alinhamento", Icons.Filled.Edit, "Encaixe a ilha sobre a câmera frontal.") {
            Chave(
                "Arrastar a ilha na tela",
                "Com isto ligado, arraste a ilha (contorno amarelo) até ficar sobre a câmera.",
                editando,
            ) {
                editando = it
                p.edit().putBoolean(Prefs.EDITANDO, it).apply()
                if (it && !IslandBus.serviceOn.value) {
                    Toast.makeText(ctx, "Ative a Acessibilidade para arrastar a ilha", Toast.LENGTH_SHORT).show()
                }
            }
            Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { a.salvar(a.cfg.copy(offsetX = 0f, offsetY = 0f)) }) { Text("Centralizar") }
                OutlinedButton(onClick = {
                    p.edit().putLong(Prefs.REDETECTAR, System.currentTimeMillis()).apply()
                    Toast.makeText(ctx, "Tela detectada de novo", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Detectar tela novamente")
                }
                OutlinedButton(onClick = { testar(ctx, a.cfg, "notif") }) { Text("Testar animação") }
            }
            Text(
                tela, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = NumerosFixos,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            )
            Subtitulo("Ajuste fino")
            // Mesmos limites do arrasto na tela (antes o controle cortava em ±120)
            Ajuste("Horizontal (X)", a.cfg.offsetX, -200f..200f, { "${it.roundToInt()} dp" }) { a.salvar(a.cfg.copy(offsetX = it)) }
            Ajuste("Vertical (Y)", a.cfg.offsetY, -60f..120f, { "${it.roundToInt()} dp" }) { a.salvar(a.cfg.copy(offsetY = it)) }
        }
    }

    item { Favoritos(a) }

    item {
        val cfg = a.cfg
        var comAtividade by remember { mutableStateOf(false) }
        Secao("Dimensões", Icons.Filled.Menu) {
            PreviaIlha(cfg, comAtividade)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !comAtividade, onClick = { comAtividade = false }, label = { Text("Parada") })
                FilterChip(selected = comAtividade, onClick = { comAtividade = true }, label = { Text("Com atividade") })
            }
            Subtitulo("Ilha parada")
            Ajuste("Largura", cfg.width, 40f..200f) { a.salvar(a.cfg.copy(width = it)) }
            Ajuste("Altura", cfg.height, 18f..56f) { a.salvar(a.cfg.copy(height = it)) }
            Ajuste("Arredondamento", cfg.cantos, 0.15f..1f, ::pct, 0.05f) { a.salvar(a.cfg.copy(cantos = it)) }
            Subtitulo("Com música ou atividade")
            Ajuste("Quanto estica", cfg.musicaExtra, 40f..240f) {
                comAtividade = true
                a.salvar(a.cfg.copy(musicaExtra = it))
            }
            Subtitulo("Expandida")
            Ajuste("Espaço acima da câmera", cfg.acima, 0f..24f) { a.salvar(a.cfg.copy(acima = it)) }
            Ajuste("Espaço abaixo da câmera", cfg.respiro, 0f..40f) { a.salvar(a.cfg.copy(respiro = it)) }
            Ajuste("Margem das laterais", cfg.margem, 4f..48f) { a.salvar(a.cfg.copy(margem = it)) }
            Ajuste("Raio dos cantos", cfg.raioExpandido, 16f..60f) { a.salvar(a.cfg.copy(raioExpandido = it)) }
        }
    }

    item {
        val cfg = a.cfg
        Secao("Aparência", Icons.Filled.Face) {
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
        val ctx = LocalContext.current
        val cfg = a.cfg
        Secao("Animações", Icons.Filled.Refresh) {
            Ajuste("Velocidade", cfg.velocidade, 0.5f..1.8f, ::pct, 0.05f) { a.salvar(a.cfg.copy(velocidade = it)) }
            Ajuste("Efeito elástico", cfg.elasticidade, 0f..1f, ::pct, 0.05f) { a.salvar(a.cfg.copy(elasticidade = it)) }
            Ajuste("Notificação aberta por", cfg.tempoNotif, 1.5f..8f, ::seg, 0.5f) { a.salvar(a.cfg.copy(tempoNotif = it)) }
            Ajuste("Expandida recolhe após", cfg.tempoExpandida, 4f..30f, ::seg, 1f) { a.salvar(a.cfg.copy(tempoExpandida = it)) }
            Chave("Ondinha da música animada", valor = cfg.animOnda) { a.salvar(a.cfg.copy(animOnda = it)) }
            Chave("Separação animada da bolinha", valor = cfg.animBolha) { a.salvar(a.cfg.copy(animBolha = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { testar(ctx, a.cfg, "expandir") }) { Text("Testar expansão") }
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
    // Relê quando os ajustes são recarregados (ex.: backup restaurado)
    var meus by remember(a.geracao) { mutableStateOf(Prefs.favoritos(prefs)) }
    var nome by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var removendo by remember { mutableStateOf(false) }
    val atual = a.cfg

    Secao(
        "Favoritos", Icons.Filled.Star,
        if (removendo) "Toque num favorito seu para apagar." else "Toque para aplicar as medidas. Salve o ajuste atual para usar depois.",
    ) {
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
                TextButton(onClick = { salvando = false; nome = "" }) { Text("Cancelar") }
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
        Secao("Integrações", Icons.Filled.Share, "O que pode aparecer na ilha.") {
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
        Secao(
            "Prioridade", Icons.Filled.ThumbUp,
            "Quando várias rodam juntas, a primeira fica na ilha e a segunda vai para a bolinha. Chamada tocando sempre fica por cima.",
        ) {
            cfg.prioridades.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(if (i == 0) LaranjaDia else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (i == 0) Color.Black else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(iconeDoTipo(t), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.rotulo, Modifier.weight(1f), fontSize = 14.sp, maxLines = 2)
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
                "Notificações nunca interrompem uma atividade: aparecem na bolinha ao lado por alguns segundos. " +
                    "Deslize a ilha para alternar entre as atividades.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item { AppsPermitidos() }
}

private fun iconeDoTipo(t: Tipo): ImageVector = when (t) {
    Tipo.CHAMADA -> Icons.Filled.Call
    Tipo.ATIVIDADE -> Icons.Filled.Build
    Tipo.TIMER -> Icons.Filled.DateRange
    Tipo.MUSICA -> Icons.Filled.Favorite
}

@Composable
fun Temporizador() {
    val ctx = LocalContext.current
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
    Secao("Temporizador do D.I.A", Icons.Filled.DateRange, "Aparece na ilha e toca ao terminar.") {
        val atual = t
        if (atual != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    atual.texto(agora), fontSize = 52.sp, fontWeight = FontWeight.Light, style = NumerosFixos,
                    color = if (atual.pausado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Text(
                    (if (atual.cronometro) "Cronômetro" else "Temporizador") + if (atual.pausado) " · pausado" else "",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!atual.cronometro) {
                    LinearProgressIndicator(
                        progress = { atual.progresso(agora) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { IslandBus.pausarOuRetomar() }, modifier = Modifier.weight(1f)) {
                    Text(if (atual.pausado) "Retomar" else "Pausar")
                }
                OutlinedButton(onClick = { IslandBus.cancelarTimer() }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            }
        } else {
            Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 15, 30, 60).forEach { m ->
                    FilterChip(selected = minutos == m.toFloat(), onClick = { minutos = m.toFloat() }, label = { Text("$m min") })
                }
            }
            Ajuste("Duração", minutos, 1f..120f, { "${it.roundToInt()} min" }) { minutos = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    IslandBus.iniciarTimer((minutos * 60_000).toLong())
                    if (!servico) Toast.makeText(ctx, "Contando, mas a ilha está desligada: ative a Acessibilidade para ouvir o aviso", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.weight(1f)) { Text("Iniciar ${minutos.roundToInt()} min") }
                OutlinedButton(onClick = { IslandBus.iniciarCronometro() }, modifier = Modifier.weight(1f)) { Text("Cronômetro") }
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
    var soBloqueados by remember { mutableStateOf(false) }

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

    Secao(
        "Apps permitidos", Icons.Filled.Lock,
        "Quais apps podem usar a ilha (notificações, música e atividades).",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(
                if (bloqueados.isEmpty()) "Todos permitidos" else "${bloqueados.size} bloqueado(s)",
                if (bloqueados.isEmpty()) VerdeOk else AmareloAviso,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { aberto = !aberto }) { Text(if (aberto) "Fechar lista" else "Escolher apps") }
        }
        if (aberto) {
            OutlinedTextField(
                value = busca, onValueChange = { busca = it }, singleLine = true,
                label = { Text("Buscar app") }, modifier = Modifier.fillMaxWidth(),
            )
            if (bloqueados.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !soBloqueados, onClick = { soBloqueados = false }, label = { Text("Todos") })
                    FilterChip(selected = soBloqueados, onClick = { soBloqueados = true }, label = { Text("Só bloqueados") })
                }
            }
            val lista = apps
            if (lista == null) {
                Text("Carregando apps…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val filtrados = lista.filter {
                    (busca.isBlank() || it.nome.contains(busca, ignoreCase = true)) && (!soBloqueados || it.pkg in bloqueados)
                }
                if (filtrados.isEmpty()) Text("Nenhum app encontrado.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                filtrados.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bmp = app.icone
                        if (bmp != null) {
                            Image(
                                remember(bmp) { bmp.asImageBitmap() }, contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Spacer(Modifier.size(36.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(app.nome, maxLines = 1, fontSize = 14.sp)
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
        Secao("Comportamento", Icons.Filled.Settings) {
            Chave("Esconder em tela cheia (experimental)", "Vídeos e jogos sem barra de status. Em alguns aparelhos a detecção falha; se a ilha sumir, desligue.", cfg.esconderTelaCheia) {
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
        Secao("Privacidade", Icons.Filled.Lock) {
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
        Secao("Inicialização e bateria", Icons.Filled.Refresh) {
            Chave(
                "Ligar a ilha ao reiniciar o celular",
                "O Android religa a ilha sozinho depois de reiniciar. Desligado, ela volta pausada.",
                cfg.ligarAoReiniciar,
            ) { a.salvar(a.cfg.copy(ligarAoReiniciar = it)) }
            Linha("Bateria sem restrição", if (bat) "Liberada" else "Restrita", bat)
            Row(Modifier.horizontalRolagem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!bat) {
                    Button(onClick = {
                        abrirAjuste(
                            ctx,
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        )
                    }) { Text("Liberar bateria") }
                }
                OutlinedButton(onClick = { abrirAjuste(ctx, infoDoApp(ctx)) }) { Text("Informações do app") }
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
                // A frequência de verificação pode ter mudado com o backup
                Updater.agendar(ctx, a.cfg.autoAtualizar)
                Toast.makeText(ctx, "Configurações restauradas", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "Backup inválido", Toast.LENGTH_LONG).show()
            }
        }
        Secao("Backup local", Icons.Filled.Share, "Salva todos os ajustes, favoritos e apps bloqueados num arquivo do celular.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportar.launch("dia-backup.json") }, modifier = Modifier.weight(1f)) { Text("Exportar") }
                OutlinedButton(
                    onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Restaurar") }
            }
            Divisor()
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
                    TextButton(onClick = {
                        a.salvar(Config())
                        Updater.agendar(ctx, Config().autoAtualizar)
                        confirmar = false
                    }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
            )
        }
    }

    item { Ajuda(a) }

    item {
        Secao("Sobre", Icons.Filled.Info) {
            Text(
                "D.I.A — Dynamic Island para Android. Inspirado na Dynamic Island do iPhone, com implementação própria " +
                    "e só APIs oficiais do Android.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Gestos e limitações conhecidas (antes só existiam no LIMITACOES.md do repositório). */
@Composable
private fun Ajuda(a: Ajustes) {
    val ctx = LocalContext.current
    var aberto by remember { mutableStateOf<String?>(null) }
    Secao("Ajuda", Icons.Filled.AccountCircle, "Gestos da ilha e o que cada aparelho permite.") {
        Subtitulo("Gestos")
        listOf(
            "Toque" to "Abre o app da atividade (ou expande, se não houver app).",
            "Segurar" to "Expande a ilha com controles.",
            "Deslizar" to "Alterna atividades; com só música, troca a faixa.",
            "Arrastar longe para a esquerda" to "Fecha o que está na ilha.",
            "Tocar fora" to "Recolhe a ilha expandida.",
            "Tocar na bolinha" to "Traz a 2ª atividade ou a notificação para a ilha. Segurar abre o app.",
        ).forEach { (g, d) ->
            Row {
                Text(g, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(118.dp))
                Text(d, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Subtitulo("Limitações conhecidas")
        val topicos = listOf(
            "Por que acessibilidade?" to "Só uma janela de acessibilidade consegue ficar sobre a barra de status, em volta da câmera. " +
                "O serviço não lê o conteúdo das telas e não recebe eventos de digitação.",
            "Versão do Android" to "Mínimo Android 11. No 12+ o app de chamadas informa se está tocando ou em andamento. " +
                "No 13+ as permissões vêm travadas em apps instalados por APK: libere em Informações do app → ⋮ → Permitir configurações restritas.",
            "Chamadas" to "Atender/recusar só existem quando o app de chamadas oferece esses botões na notificação. Nome e foto vêm da própria notificação.",
            "Temporizadores e navegação" to "Aparecem se o app mostrar a contagem ou o texto na notificação. Relógios com notificação personalizada não expõem o tempo; setas de navegação não são expostas.",
            "Música" to "Depende do player ter uma sessão de mídia. Alguns players não informam a duração (aí não há barra de progresso) nem a tela para abrir (aí o toque expande a ilha).",
            "Tela cheia" to "Detectada pela visibilidade da barra de status. Em alguns fabricantes a informação atrasa ou não chega; dá para desligar em Comportamento.",
            "Fabricantes" to "Motorola: bateria Sem restrições. Xiaomi/HyperOS: Início automático e Sem restrições. Samsung: tire de Apps em suspensão. Realme/OnePlus: permita Execução em segundo plano.",
        )
        topicos.forEach { (t, d) ->
            val ativo = aberto == t
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { aberto = if (ativo) null else t }) {
                        Icon(
                            if (ativo) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (ativo) "Recolher" else "Ver",
                        )
                    }
                }
                if (ativo) {
                    Text(
                        d, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = {
            val url = "https://github.com/${a.cfg.fonteAtualizacao}/blob/main/LIMITACOES.md"
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Nenhum navegador encontrado", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Ver lista completa no GitHub") }
    }
}
