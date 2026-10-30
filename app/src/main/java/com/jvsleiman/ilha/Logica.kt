package com.jvsleiman.ilha

/*
 * Regras puras (sem Android), cobertas por testes em app/src/test.
 */

/** Tipos de atividade contínua, na ordem de prioridade padrão. */
enum class Tipo(val rotulo: String) {
    CHAMADA("Chamadas"),
    ATIVIDADE("Atividades (navegação, downloads, gravação)"),
    TIMER("Temporizadores e cronômetros"),
    MUSICA("Música");

    companion object {
        val PADRAO = listOf(CHAMADA, ATIVIDADE, TIMER, MUSICA)

        /** Lê "MUSICA,TIMER,..." garantindo que todos os tipos aparecem uma vez. */
        fun lerOrdem(s: String): List<Tipo> {
            val lidos = s.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }.distinct()
            return lidos + PADRAO.filter { it !in lidos }
        }
    }
}

/** Ordena atividades pela prioridade configurada; empate mantém a mais recente primeiro. */
fun <T> ordenarPorPrioridade(itens: List<T>, ordem: List<Tipo>, tipo: (T) -> Tipo, recencia: (T) -> Long): List<T> =
    itens.sortedWith(compareBy<T> { ordem.indexOf(tipo(it)).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        .thenByDescending { recencia(it) })

/** Move um tipo uma posição para cima (-1) ou para baixo (+1) na ordem. */
fun moverPrioridade(ordem: List<Tipo>, t: Tipo, passo: Int): List<Tipo> {
    val i = ordem.indexOf(t)
    val j = i + passo
    if (i < 0 || j !in ordem.indices) return ordem
    return ordem.toMutableList().apply { this[i] = this[j]; this[j] = t }
}

/** 65_000 → "1:05"; 3_725_000 → "1:02:05". Negativo vira zero. */
fun relogio(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** Número da compilação no fim da tag: "v0.3.42" → 42. */
fun numeroDaVersao(tag: String): Long = Regex("(\\d+)$").find(tag.trim())?.value?.toLongOrNull() ?: 0L

/** Cor de um botão de chamada pelo texto: 1 = atender (verde), -1 = recusar/desligar (vermelho), 0 = outro. */
fun corDaAcaoDeChamada(titulo: String): Int {
    val t = titulo.lowercase()
    val verdes = listOf("atender", "answer", "aceitar", "accept")
    val vermelhos = listOf("recusar", "rejeitar", "desligar", "encerrar", "decline", "reject", "hang up", "end call", "ignorar")
    return when {
        vermelhos.any { it in t } -> -1
        verdes.any { it in t } -> 1
        else -> 0
    }
}

/** Temporizador ou cronômetro do próprio app. Tempos em ms do relógio monotônico (elapsedRealtime). */
data class TimerProprio(
    val cronometro: Boolean,
    /** Temporizador: duração total. Cronômetro: 0. */
    val duracao: Long,
    /** Temporizador: instante em que termina. Cronômetro: instante em que começou. */
    val marco: Long,
    /** Se pausado: o valor congelado (restante no temporizador, decorrido no cronômetro). */
    val congelado: Long? = null,
) {
    val pausado get() = congelado != null

    fun restante(agora: Long): Long =
        if (cronometro) 0L else (congelado ?: (marco - agora)).coerceAtLeast(0L)

    fun decorrido(agora: Long): Long =
        if (cronometro) (congelado ?: (agora - marco)).coerceAtLeast(0L) else duracao - restante(agora)

    /** Fração já passada do temporizador (0..1). Cronômetro: sempre 0. */
    fun progresso(agora: Long): Float =
        if (cronometro || duracao <= 0) 0f else (decorrido(agora).toFloat() / duracao).coerceIn(0f, 1f)

    fun terminou(agora: Long) = !cronometro && !pausado && agora >= marco

    /** O que mostrar na ilha. */
    fun texto(agora: Long) = relogio(if (cronometro) decorrido(agora) else restante(agora) + 999)

    fun pausar(agora: Long) = if (pausado) this else copy(congelado = if (cronometro) decorrido(agora) else restante(agora))

    fun retomar(agora: Long): TimerProprio {
        val c = congelado ?: return this
        return if (cronometro) copy(marco = agora - c, congelado = null) else copy(marco = agora + c, congelado = null)
    }

    companion object {
        fun temporizador(ms: Long, agora: Long) = TimerProprio(false, ms, agora + ms)
        fun cronometro(agora: Long) = TimerProprio(true, 0L, agora)
    }
}
