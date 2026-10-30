package com.jvsleiman.ilha

import android.app.Application

/** Guarda o último erro fatal para aparecer no Diagnóstico (fica só no celular). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val texto = "v${Updater.installedName(this)} · ${java.util.Date()}\n" + e.stackTraceToString()
                Prefs.get(this).edit().putString(ERRO, texto).commit()
            } catch (_: Throwable) { }
            anterior?.uncaughtException(t, e)
        }
    }

    companion object { const val ERRO = "ultimo_erro" }
}
