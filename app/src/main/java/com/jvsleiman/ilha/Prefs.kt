package com.jvsleiman.ilha

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes da ilha. Tamanhos e deslocamentos em dp. */
data class Config(
    val enabled: Boolean = true,
    val width: Float = 100f,
    val height: Float = 30f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Espaço entre a faixa da câmera e o conteúdo expandido. */
    val respiro: Float = 10f,
    /** Distância das bordas da tela quando expandida (cada lado). */
    val margem: Float = 12f,
    /** Quanto a ilha estica com música tocando. */
    val musicaExtra: Float = 120f,
) {
    /** Compara só as medidas (ignora ligado/desligado). */
    fun mesmasMedidas(o: Config) = copy(enabled = true) == o.copy(enabled = true)
}

data class Favorito(val nome: String, val config: Config)

/** Favoritos que já vêm no app. */
val PRONTOS = listOf(
    Favorito("Padrão", Config()),
    Favorito("Discreta", Config(width = 84f, height = 26f, respiro = 8f, margem = 16f, musicaExtra = 96f)),
    Favorito("Grande", Config(width = 120f, height = 36f, respiro = 14f, margem = 10f, musicaExtra = 150f)),
)

object Prefs {
    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ilha", Context.MODE_PRIVATE)

    fun read(p: SharedPreferences): Config {
        val d = Config()
        return Config(
            enabled = p.getBoolean("enabled", d.enabled),
            width = p.getFloat("width", d.width),
            height = p.getFloat("height", d.height),
            offsetX = p.getFloat("offsetX", d.offsetX),
            offsetY = p.getFloat("offsetY", d.offsetY),
            respiro = p.getFloat("respiro", d.respiro),
            margem = p.getFloat("margem", d.margem),
            musicaExtra = p.getFloat("musicaExtra", d.musicaExtra),
        )
    }

    fun write(p: SharedPreferences, c: Config) {
        p.edit()
            .putBoolean("enabled", c.enabled)
            .putFloat("width", c.width)
            .putFloat("height", c.height)
            .putFloat("offsetX", c.offsetX)
            .putFloat("offsetY", c.offsetY)
            .putFloat("respiro", c.respiro)
            .putFloat("margem", c.margem)
            .putFloat("musicaExtra", c.musicaExtra)
            .apply()
    }

    // ---------- favoritos ----------

    private fun toJson(f: Favorito) = JSONObject().apply {
        put("nome", f.nome)
        put("width", f.config.width.toDouble())
        put("height", f.config.height.toDouble())
        put("offsetX", f.config.offsetX.toDouble())
        put("offsetY", f.config.offsetY.toDouble())
        put("respiro", f.config.respiro.toDouble())
        put("margem", f.config.margem.toDouble())
        put("musicaExtra", f.config.musicaExtra.toDouble())
    }

    private fun fromJson(o: JSONObject): Favorito {
        val d = Config()
        return Favorito(
            o.optString("nome", "Favorito"),
            Config(
                width = o.optDouble("width", d.width.toDouble()).toFloat(),
                height = o.optDouble("height", d.height.toDouble()).toFloat(),
                offsetX = o.optDouble("offsetX", d.offsetX.toDouble()).toFloat(),
                offsetY = o.optDouble("offsetY", d.offsetY.toDouble()).toFloat(),
                respiro = o.optDouble("respiro", d.respiro.toDouble()).toFloat(),
                margem = o.optDouble("margem", d.margem.toDouble()).toFloat(),
                musicaExtra = o.optDouble("musicaExtra", d.musicaExtra.toDouble()).toFloat(),
            ),
        )
    }

    fun favoritos(p: SharedPreferences): List<Favorito> = try {
        val arr = JSONArray(p.getString("favoritos", "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    fun salvarFavoritos(p: SharedPreferences, lista: List<Favorito>) {
        val arr = JSONArray()
        lista.forEach { arr.put(toJson(it)) }
        p.edit().putString("favoritos", arr.toString()).apply()
    }
}
