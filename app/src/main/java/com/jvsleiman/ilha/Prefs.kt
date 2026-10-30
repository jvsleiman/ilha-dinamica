package com.jvsleiman.ilha

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Todos os ajustes da ilha. Tamanhos e deslocamentos em dp, tempos em segundos.
 * Guardados como um único JSON (uma escrita = uma atualização ao vivo da ilha).
 */
data class Config(
    val enabled: Boolean = true,
    // --- ilha parada ---
    val width: Float = 100f,
    val height: Float = 30f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Arredondamento da ilha parada: 1 = cápsula perfeita, 0 = quase retângulo. */
    val cantos: Float = 1f,
    // --- expandida ---
    /** Espaço entre a câmera e o conteúdo expandido. */
    val respiro: Float = 12f,
    /** Quanto a forma expandida sobe acima da câmera. */
    val acima: Float = 8f,
    /** Distância das bordas da tela quando expandida (cada lado). */
    val margem: Float = 12f,
    /** Raio dos cantos da forma expandida. */
    val raioExpandido: Float = 44f,
    /** Quanto a ilha estica com música ou atividade. */
    val musicaExtra: Float = 120f,
    // --- aparência ---
    val opacidade: Float = 1f,
    val escalaIcones: Float = 1f,
    val tamanhoTexto: Float = 1f,
    // --- animações ---
    /** 1 = normal. Maior = mais rápida (multiplica a rigidez da mola). */
    val velocidade: Float = 1f,
    /** 0 = sem quique, 1 = bem elástica. */
    val elasticidade: Float = 0.45f,
    /** Tempo da faixa de notificação aberta. */
    val tempoNotif: Float = 2.5f,
    /** Tempo do cartão expandido antes de recolher sozinho. */
    val tempoExpandida: Float = 12f,
    val animOnda: Boolean = true,
    val animBolha: Boolean = true,
    // --- integrações ---
    val musica: Boolean = true,
    val notificacoes: Boolean = true,
    val chamadas: Boolean = true,
    val bateria: Boolean = true,
    val atividades: Boolean = true,
    val timer: Boolean = true,
    // --- comportamento ---
    val esconderTelaCheia: Boolean = false,
    val esconderPaisagem: Boolean = true,
    /** Na tela bloqueada, não mostra o texto das notificações. */
    val privacidade: Boolean = true,
    /** Com uma atividade só (música), deslizar troca de faixa. */
    val deslizarTrocaMusica: Boolean = true,
    /** Ordem de prioridade das atividades (a primeira fica na ilha principal). */
    val prioridades: List<Tipo> = Tipo.PADRAO,
    // --- sistema ---
    val ligarAoReiniciar: Boolean = true,
    /** 0 = desligado, 1 = diária, 7 = semanal. */
    val autoAtualizar: Int = 1,
    val fonteAtualizacao: String = "jvsleiman/ilha-dinamica",
) {
    /** Compara só as medidas (para marcar o favorito ativo). */
    fun medidas() = listOf(width, height, offsetX, offsetY, cantos, respiro, acima, margem, raioExpandido, musicaExtra)
    fun mesmasMedidas(o: Config) = medidas() == o.medidas()
    /** Mola da forma, a partir dos ajustes de animação. */
    val amortecimento: Float get() = (1f - 0.42f * elasticidade).coerceIn(0.5f, 1f)
    val rigidez: Float get() = 260f * velocidade * velocidade
}

data class Favorito(val nome: String, val config: Config)

/** Favoritos que já vêm no app (só medidas; o resto continua como está). */
val PRONTOS = listOf(
    Favorito("Padrão", Config()),
    Favorito("Discreta", Config(width = 84f, height = 26f, respiro = 10f, acima = 6f, margem = 16f, musicaExtra = 96f)),
    Favorito("Grande", Config(width = 120f, height = 36f, respiro = 14f, acima = 10f, margem = 10f, musicaExtra = 150f)),
)

object Prefs {
    private const val KEY = "config"
    const val EDITANDO = "editando"
    const val REDETECTAR = "redetectar"
    const val BLOQUEADOS = "apps_bloqueados"
    const val VISTOS = "apps_vistos"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ilha", Context.MODE_PRIVATE)

    fun read(p: SharedPreferences): Config {
        val s = p.getString(KEY, null)
        if (s != null) return try { fromJson(JSONObject(s)) } catch (e: Exception) { Config() }
        // Versões antigas guardavam cada medida numa chave
        val d = Config()
        return d.copy(
            enabled = p.getBoolean("enabled", d.enabled),
            width = p.getFloat("width", d.width),
            height = p.getFloat("height", d.height),
            offsetX = p.getFloat("offsetX", d.offsetX),
            offsetY = p.getFloat("offsetY", d.offsetY),
            respiro = p.getFloat("respiro", d.respiro),
            acima = p.getFloat("acima", d.acima),
            margem = p.getFloat("margem", d.margem),
            musicaExtra = p.getFloat("musicaExtra", d.musicaExtra),
        )
    }

    fun write(p: SharedPreferences, c: Config) {
        p.edit().putString(KEY, toJson(c).toString()).apply()
    }

    fun toJson(c: Config) = JSONObject().apply {
        put("enabled", c.enabled)
        put("width", c.width.toDouble()); put("height", c.height.toDouble())
        put("offsetX", c.offsetX.toDouble()); put("offsetY", c.offsetY.toDouble())
        put("cantos", c.cantos.toDouble())
        put("respiro", c.respiro.toDouble()); put("acima", c.acima.toDouble())
        put("margem", c.margem.toDouble()); put("raioExpandido", c.raioExpandido.toDouble())
        put("musicaExtra", c.musicaExtra.toDouble())
        put("opacidade", c.opacidade.toDouble()); put("escalaIcones", c.escalaIcones.toDouble())
        put("tamanhoTexto", c.tamanhoTexto.toDouble())
        put("velocidade", c.velocidade.toDouble()); put("elasticidade", c.elasticidade.toDouble())
        put("tempoNotif", c.tempoNotif.toDouble()); put("tempoExpandida", c.tempoExpandida.toDouble())
        put("animOnda", c.animOnda); put("animBolha", c.animBolha)
        put("musica", c.musica); put("notificacoes", c.notificacoes); put("chamadas", c.chamadas)
        put("bateria", c.bateria); put("atividades", c.atividades); put("timer", c.timer)
        put("telaCheia2", c.esconderTelaCheia); put("esconderPaisagem", c.esconderPaisagem)
        put("privacidade", c.privacidade); put("deslizarTrocaMusica", c.deslizarTrocaMusica)
        put("prioridades", c.prioridades.joinToString(",") { it.name })
        put("ligarAoReiniciar", c.ligarAoReiniciar)
        put("autoAtualizar", c.autoAtualizar)
        put("fonteAtualizacao", c.fonteAtualizacao)
    }

    fun fromJson(o: JSONObject): Config {
        val d = Config()
        fun f(k: String, v: Float) = o.optDouble(k, v.toDouble()).toFloat()
        fun b(k: String, v: Boolean) = o.optBoolean(k, v)
        return Config(
            enabled = b("enabled", d.enabled),
            width = f("width", d.width), height = f("height", d.height),
            offsetX = f("offsetX", d.offsetX), offsetY = f("offsetY", d.offsetY),
            cantos = f("cantos", d.cantos),
            respiro = f("respiro", d.respiro), acima = f("acima", d.acima),
            margem = f("margem", d.margem), raioExpandido = f("raioExpandido", d.raioExpandido),
            musicaExtra = f("musicaExtra", d.musicaExtra),
            opacidade = f("opacidade", d.opacidade), escalaIcones = f("escalaIcones", d.escalaIcones),
            tamanhoTexto = f("tamanhoTexto", d.tamanhoTexto),
            velocidade = f("velocidade", d.velocidade), elasticidade = f("elasticidade", d.elasticidade),
            tempoNotif = f("tempoNotif", d.tempoNotif), tempoExpandida = f("tempoExpandida", d.tempoExpandida),
            animOnda = b("animOnda", d.animOnda), animBolha = b("animBolha", d.animBolha),
            musica = b("musica", d.musica), notificacoes = b("notificacoes", d.notificacoes),
            chamadas = b("chamadas", d.chamadas), bateria = b("bateria", d.bateria),
            atividades = b("atividades", d.atividades), timer = b("timer", d.timer),
            esconderTelaCheia = b("telaCheia2", d.esconderTelaCheia),
            esconderPaisagem = b("esconderPaisagem", d.esconderPaisagem),
            privacidade = b("privacidade", d.privacidade),
            deslizarTrocaMusica = b("deslizarTrocaMusica", d.deslizarTrocaMusica),
            prioridades = Tipo.lerOrdem(o.optString("prioridades", "")),
            ligarAoReiniciar = b("ligarAoReiniciar", d.ligarAoReiniciar),
            autoAtualizar = o.optInt("autoAtualizar", d.autoAtualizar),
            fonteAtualizacao = o.optString("fonteAtualizacao", d.fonteAtualizacao).ifBlank { d.fonteAtualizacao },
        )
    }

    // ---------- apps ----------

    fun bloqueados(p: SharedPreferences): Set<String> = p.getStringSet(BLOQUEADOS, emptySet()) ?: emptySet()

    fun bloquear(p: SharedPreferences, pkg: String, bloqueado: Boolean) {
        val s = bloqueados(p).toMutableSet()
        if (bloqueado) s.add(pkg) else s.remove(pkg)
        p.edit().putStringSet(BLOQUEADOS, s).apply()
    }

    /** Apps que já mandaram notificação (para a lista de permitidos mostrar primeiro). */
    fun vistos(p: SharedPreferences): Set<String> = p.getStringSet(VISTOS, emptySet()) ?: emptySet()

    fun marcarVisto(p: SharedPreferences, pkg: String) {
        val s = vistos(p)
        if (pkg in s) return
        p.edit().putStringSet(VISTOS, s + pkg).apply()
    }

    // ---------- favoritos ----------

    private fun favJson(f: Favorito) = toJson(f.config).apply { put("nome", f.nome) }

    fun favoritos(p: SharedPreferences): List<Favorito> = try {
        val arr = JSONArray(p.getString("favoritos", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Favorito(o.optString("nome", "Favorito"), fromJson(o))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun salvarFavoritos(p: SharedPreferences, lista: List<Favorito>) {
        val arr = JSONArray()
        lista.forEach { arr.put(favJson(it)) }
        p.edit().putString("favoritos", arr.toString()).apply()
    }

    /** Aplica só as medidas de um favorito, mantendo o resto dos ajustes. */
    fun aplicarMedidas(atual: Config, f: Config) = atual.copy(
        width = f.width, height = f.height, offsetX = f.offsetX, offsetY = f.offsetY, cantos = f.cantos,
        respiro = f.respiro, acima = f.acima, margem = f.margem, raioExpandido = f.raioExpandido,
        musicaExtra = f.musicaExtra,
    )

    // ---------- backup ----------

    fun exportar(p: SharedPreferences): String = JSONObject().apply {
        put("app", "D.I.A")
        put("formato", 1)
        put("config", toJson(read(p)))
        put("favoritos", JSONArray(p.getString("favoritos", "[]")))
        put("bloqueados", JSONArray(bloqueados(p).toList()))
    }.toString(2)

    /** Lê um backup. Lança exceção se o arquivo não for um backup válido. */
    fun importar(p: SharedPreferences, texto: String) {
        val o = JSONObject(texto)
        require(o.optString("app") == "D.I.A") { "Este arquivo não é um backup do D.I.A." }
        val cfg = fromJson(o.getJSONObject("config"))
        val favs = o.optJSONArray("favoritos") ?: JSONArray()
        val bl = o.optJSONArray("bloqueados") ?: JSONArray()
        p.edit()
            .putString(KEY, toJson(cfg).toString())
            .putString("favoritos", favs.toString())
            .putStringSet(BLOQUEADOS, (0 until bl.length()).map { bl.getString(it) }.toSet())
            .apply()
    }
}
