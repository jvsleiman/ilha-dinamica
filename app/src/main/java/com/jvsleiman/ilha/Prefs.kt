package com.jvsleiman.ilha

import android.content.Context
import android.content.SharedPreferences

/** Ajustes da ilha. Tamanhos e deslocamentos em dp. */
data class Config(
    val enabled: Boolean = true,
    val width: Float = 100f,
    val height: Float = 30f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
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
        )
    }

    fun write(p: SharedPreferences, c: Config) {
        p.edit()
            .putBoolean("enabled", c.enabled)
            .putFloat("width", c.width)
            .putFloat("height", c.height)
            .putFloat("offsetX", c.offsetX)
            .putFloat("offsetY", c.offsetY)
            .apply()
    }
}
