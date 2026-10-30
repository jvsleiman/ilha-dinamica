package com.jvsleiman.ilha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicaTest {

    private data class A(val nome: String, val tipo: Tipo, val t: Long)

    @Test
    fun prioridadePadraoColocaChamadaPrimeiro() {
        val itens = listOf(A("musica", Tipo.MUSICA, 1), A("timer", Tipo.TIMER, 2), A("chamada", Tipo.CHAMADA, 3))
        val r = ordenarPorPrioridade(itens, Tipo.PADRAO, { it.tipo }, { it.t })
        assertEquals(listOf("chamada", "timer", "musica"), r.map { it.nome })
    }

    @Test
    fun prioridadeConfiguradaERecenciaDesempatam() {
        val ordem = listOf(Tipo.MUSICA, Tipo.TIMER, Tipo.CHAMADA, Tipo.ATIVIDADE)
        val itens = listOf(A("t1", Tipo.TIMER, 1), A("m", Tipo.MUSICA, 0), A("t2", Tipo.TIMER, 5))
        val r = ordenarPorPrioridade(itens, ordem, { it.tipo }, { it.t })
        assertEquals(listOf("m", "t2", "t1"), r.map { it.nome })
    }

    @Test
    fun lerOrdemCompletaTiposFaltandoEIgnoraLixo() {
        assertEquals(Tipo.PADRAO, Tipo.lerOrdem(""))
        val r = Tipo.lerOrdem("MUSICA, xyz,MUSICA,TIMER")
        assertEquals(listOf(Tipo.MUSICA, Tipo.TIMER, Tipo.CHAMADA, Tipo.ATIVIDADE), r)
    }

    @Test
    fun moverPrioridadeRespeitaLimites() {
        val o = Tipo.PADRAO
        assertEquals(o, moverPrioridade(o, Tipo.CHAMADA, -1))
        assertEquals(listOf(Tipo.ATIVIDADE, Tipo.CHAMADA, Tipo.TIMER, Tipo.MUSICA), moverPrioridade(o, Tipo.CHAMADA, 1))
        assertEquals(o, moverPrioridade(o, Tipo.MUSICA, 1))
    }

    @Test
    fun relogioFormata() {
        assertEquals("0:00", relogio(-5))
        assertEquals("1:05", relogio(65_000))
        assertEquals("1:02:05", relogio(3_725_000))
    }

    @Test
    fun numeroDaVersaoLeOFinalDaTag() {
        assertEquals(42L, numeroDaVersao("v0.3.42"))
        assertEquals(19L, numeroDaVersao("v0.1.19"))
        assertEquals(0L, numeroDaVersao("sem-numero"))
    }

    @Test
    fun coresDosBotoesDeChamada() {
        assertEquals(1, corDaAcaoDeChamada("Atender"))
        assertEquals(-1, corDaAcaoDeChamada("Recusar"))
        assertEquals(-1, corDaAcaoDeChamada("Desligar"))
        assertEquals(-1, corDaAcaoDeChamada("Decline"))
        assertEquals(0, corDaAcaoDeChamada("Responder com mensagem"))
    }

    @Test
    fun temporizadorContaPausaERetoma() {
        var t = TimerProprio.temporizador(60_000, agora = 1_000)
        assertEquals(60_000, t.restante(1_000))
        assertEquals(30_000, t.restante(31_000))
        assertEquals(0.5f, t.progresso(31_000), 0.001f)
        t = t.pausar(31_000)
        assertTrue(t.pausado)
        assertEquals(30_000, t.restante(500_000)) // pausado não anda
        t = t.retomar(500_000)
        assertEquals(30_000, t.restante(500_000))
        assertFalse(t.terminou(529_999))
        assertTrue(t.terminou(530_000))
        assertEquals("1:00", TimerProprio.temporizador(60_000, 0).texto(0))
        assertEquals("0:01", TimerProprio.temporizador(60_000, 0).texto(59_500))
    }

    @Test
    fun cronometroContaPraCima() {
        var c = TimerProprio.cronometro(agora = 10_000)
        assertEquals(5_000, c.decorrido(15_000))
        c = c.pausar(15_000)
        assertEquals(5_000, c.decorrido(99_000))
        c = c.retomar(100_000)
        assertEquals(7_000, c.decorrido(102_000))
        assertFalse(c.terminou(Long.MAX_VALUE))
    }
}
