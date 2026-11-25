package com.emendacerta

import java.time.LocalDate
import java.time.temporal.ChronoUnit

val DIAS = listOf("domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado")
val DIAS_ABREV = listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")
val MESES = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
val MESES_ABREV = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

/** 0 = domingo ... 6 = sábado */
fun LocalDate.dow(): Int = dayOfWeek.value % 7

// ---- Perfil do usuário ----
data class Perfil(
    val vinculo: String = "clt", // clt | servidor | estudante | autonomo
    val nivel: String = "basico", // só estudante: basico | superior
    val dias: List<Int> = listOf(1, 2, 3, 4, 5), // dias da semana em que trabalha ou tem aula
    val feriadoTrabalhado: Boolean = false, // escala que cobre feriados (comércio, saúde, plantão)
    val facultativos: Boolean = false, // tem folga em Carnaval e Corpus Christi
    val orcamento: Int = 5, // quantos dias topa gastar por ano em emendas
)

class Vinculo(
    val id: String,
    val rotulo: String,
    val faz: String,
    val un: String,
    val unp: String,
    val verbo: String,
    val zero: String,
)

val VINCULOS = listOf(
    Vinculo("clt", "CLT", "trabalha", "dia de férias", "dias de férias", "tire", "Sem gastar nenhum dia de férias"),
    Vinculo("servidor", "Servidor público", "trabalha", "dia de férias", "dias de férias", "tire", "Sem gastar nenhum dia de férias"),
    Vinculo("estudante", "Estudante", "estuda", "falta", "faltas", "falte", "Sem faltar a nenhuma aula"),
    Vinculo("autonomo", "Autônomo / MEI", "trabalha", "dia sem faturar", "dias sem faturar", "pare", "Sem perder nenhum dia de faturamento"),
)

fun vinc(p: Perfil): Vinculo = VINCULOS.firstOrNull { it.id == p.vinculo } ?: VINCULOS[0]

enum class Tipo(val titulo: String, val nota: Int) {
    OURO("Emenda de ouro", 10),
    PRONTA("Já vem emendado", 7),
    CARA("Emenda cara", 4),
    AVULSA("Folga avulsa", 2),
    PERDIDA("Cai na sua folga", 0),
    TRABALHA("Dia normal pra você", 0),
}

enum class Papel { FERIADO, FERIAS, FDS }

data class Dia(val data: LocalDate, val papel: Papel)
class Bruto(
    val iso: String,
    val nome: String,
    val data: LocalDate,
    val frase: String,
    val dica: String,
    val facultativo: Boolean,
)

data class Feriado(
    val iso: String,
    val nome: String,
    val data: LocalDate,
    val tipo: Tipo,
    val resumo: String,
    val nota: Int,
    val custo: Int,
    val folgaTotal: Int,
    val dias: List<Dia>,
    val frase: String,
    val dica: String,
    val facultativo: Boolean,
)

fun isoDe(d: LocalDate): String = d.toString()

fun nomeDia(d: LocalDate): String {
    val w = d.dow()
    return (if (w == 0 || w == 6) "o " else "a ") + DIAS[w].replace("-feira", "")
}
private fun expandir(d: LocalDate, passo: Int, ehFolga: (LocalDate) -> Boolean): Int {
    var n = 0
    while (n < 10 && ehFolga(d.plusDays((passo * (n + 1)).toLong()))) n++
    return n
}

private class Ponte(val custo: Int, val ganho: Int)
private fun ponte(ponta: LocalDate, passo: Int, ehFolga: (LocalDate) -> Boolean): Ponte? {
    for (c in 1..2) {
        val alvo = ponta.plusDays((passo * (c + 1)).toLong())
        if (ehFolga(alvo)) return Ponte(c, c + 1 + expandir(alvo, passo, ehFolga))
    }
    return null
}

private fun tipoDe(custo: Int, total: Int): Tipo = when {
    custo == 0 -> if (total >= 3) Tipo.PRONTA else Tipo.AVULSA
    custo == 1 -> if (total >= 4) Tipo.OURO else Tipo.CARA
    else -> Tipo.CARA
}

private class Op(val custo: Int, val ini: Int, val fim: Int, val ferias: List<Int>) {
    val total = fim - ini + 1
    val tipo = tipoDe(custo, total)
    val nota = if (tipo == Tipo.PRONTA && total >= 4) 8 else tipo.nota
}

private class Analise(
    val tipo: Tipo,
    val resumo: String,
    val nota: Int,
    val custo: Int,
    val folgaTotal: Int,
    val dias: List<Dia>,
)
private fun analisar(f: Bruto, p: Perfil, folgas: Set<String>): Analise {
    val data = f.data
    val v = vinc(p)
    val ehFolga: (LocalDate) -> Boolean = { d -> d.dow() !in p.dias || isoDe(d) in folgas }

    if (data.dow() !in p.dias) {
        return Analise(
            Tipo.PERDIDA,
            "Esse não rende nada pra você: caiu num dia em que você já não ${if (v.faz == "estuda") "tem aula" else "trabalha"}.",
            0, 0, 0, emptyList(),
        )
    }
    if (f.iso !in folgas) {
        val escala = p.vinculo != "estudante" && p.feriadoTrabalhado
        return Analise(
            Tipo.TRABALHA,
            if (escala) "Pela sua escala você trabalha nesse feriado. Veja se tem direito a folga compensatória ou pagamento em dobro."
            else "Ponto facultativo sem folga garantida no seu caso. Conte como dia normal até confirmar.",
            0, 0, 0, emptyList(),
        )
    }

    val e = expandir(data, -1, ehFolga)
    val d = expandir(data, 1, ehFolga)
    val ops = mutableListOf(Op(0, -e, d, emptyList()))
    ponte(data.plusDays((-e).toLong()), -1, ehFolga)?.let { pe ->
        ops.add(Op(pe.custo, -(e + pe.ganho), d, List(pe.custo) { k -> -(e + 1 + k) }))
    }
    ponte(data.plusDays(d.toLong()), 1, ehFolga)?.let { pd ->
        ops.add(Op(pd.custo, -e, d + pd.ganho, List(pd.custo) { k -> d + 1 + k }))
    }
    val o = ops.sortedWith(
        compareByDescending<Op> { it.nota }.thenBy { it.custo }.thenByDescending { it.total },
    ).first()

    val dias = (o.ini..o.fim).map { i ->
        val dt = data.plusDays(i.toLong())
        val papel = when {
            i in o.ferias -> Papel.FERIAS
            i == 0 || isoDe(dt) in folgas -> Papel.FERIADO
            else -> Papel.FDS
        }
        Dia(dt, papel)
    }
    val quais = o.ferias.joinToString(" e ") { nomeDia(data.plusDays(it.toLong())) }
    val custoTxt = "${o.custo} ${if (o.custo == 1) v.un else v.unp}"
    val resumo = when (o.tipo) {
        Tipo.OURO -> "Com $custoTxt ($quais) você emenda ${o.total} dias seguidos."
        Tipo.PRONTA -> "${v.zero} você já ganha ${o.total} dias seguidos."
        Tipo.CARA -> "Com $custoTxt ($quais) rendem ${o.total} dias seguidos. Só vale se você estiver precisando mesmo."
        else -> "Um dia de folga solto no meio da sua semana. Não dá pra emendar sem gastar muito."
    }
    return Analise(o.tipo, resumo, o.nota, o.custo, o.total, dias)
}
fun avisos(f: Feriado, p: Perfil): List<String> {
    val out = mutableListOf<String>()
    if (p.vinculo == "clt") {
        if (f.custo > 0) out.add("Pela CLT, as férias só podem ser divididas em períodos de pelo menos 5 dias. Por isso emenda de 1 ou 2 dias costuma ser combinada com a empresa (banco de horas, compensação ou abono).")
        if (f.tipo == Tipo.TRABALHA && p.feriadoTrabalhado) out.add("Trabalho em feriado sem folga compensatória deve ser pago em dobro (Súmula 146 do TST). A convenção coletiva da sua categoria pode mudar isso.")
    }
    if (p.vinculo == "servidor") {
        if (f.facultativo) out.add("No serviço público, ponto facultativo sai por decreto do seu órgão. Confira o ato oficial antes de planejar.")
        if (f.custo > 0) out.add("Confirme com a chefia se o dia pode sair como férias ou compensação.")
    }
    if (p.vinculo == "estudante" && f.custo > 0) {
        out.add(
            if (p.nivel == "basico") "Na educação básica a frequência mínima é de 75% das horas letivas (LDB). Escolas costumam decidir emendas no calendário, então veja se o dia já não foi liberado."
            else "Na faculdade, o limite de faltas vem do regulamento da instituição (costuma ser 25%). Veja se há prova ou chamada nesse dia.",
        )
    }
    if (p.vinculo == "autonomo") {
        if (f.custo > 0) out.add("Sem férias formais, o custo da emenda é o faturamento do dia. Combine prazos com seus clientes antes.")
        out.add("Em feriado nacional boleto e TED não compensam, mas o Pix funciona. Antecipe pagamentos.")
    }
    return out
}

fun resumoPerfil(p: Perfil): String {
    val ord = listOf(1, 2, 3, 4, 5, 6, 0).filter { it in p.dias }
    val util = p.dias.size == 5 && listOf(1, 2, 3, 4, 5).all { it in p.dias }
    val dias = if (util) "seg a sex" else ord.joinToString(", ") { DIAS_ABREV[it] }
    return "${vinc(p).rotulo}, $dias"
}

class PlanoAnual(val ids: Set<String>, val gasto: Int, val descanso: Int, val texto: String)
fun montarPlano(lista: List<Feriado>, p: Perfil, hoje: LocalDate): PlanoAnual {
    val v = vinc(p)
    val futuras = lista.filter { !it.data.isBefore(hoje) }
    val cand = futuras
        .filter { it.custo > 0 && it.nota >= 4 }
        .sortedWith(
            compareByDescending<Feriado> { it.folgaTotal.toDouble() / it.custo }.thenBy { it.data },
        )
    var resta = p.orcamento
    var gasto = 0
    var descanso = 0
    val ids = mutableSetOf<String>()
    for (f in cand) {
        if (f.custo <= resta) {
            resta -= f.custo
            gasto += f.custo
            descanso += f.folgaTotal
            ids.add(f.iso)
        }
    }
    val g = futuras.count { it.tipo == Tipo.PRONTA }
    val gtxt = if (g == 1) "1 feriado já vem emendado" else "$g feriados já vêm emendados"
    val texto = if (ids.isEmpty()) {
        if (g > 0) "Nenhuma emenda cabe no seu limite, mas $gtxt." else "Nenhuma emenda boa pela frente com o seu perfil."
    } else {
        val fora = if (g > 0) ", fora " + gtxt.replace("já vem", "que já vem").replace("já vêm", "que já vêm") else ""
        "${ids.size} ${if (ids.size == 1) "emenda" else "emendas"} por $gasto ${if (gasto == 1) v.un else v.unp} somam $descanso dias de folga corrida$fora."
    }
    return PlanoAnual(ids, gasto, descanso, texto)
}
private class Conteudo(
    val re: Regex,
    val frase: String,
    val dica: String,
    val facultativo: Boolean = false,
)

private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

private val CONTEUDO = listOf(
    Conteudo(r("confrat"), "O ano começa com folga, e ninguém tem moral pra te cobrar nada hoje.", "Durma até tarde. A virada já cobrou o suficiente."),
    Conteudo(r("carnaval"), "De todos, é o que mais depende de onde você trabalha.", "Confirme com a empresa antes de comprar passagem.", true),
    Conteudo(r("sexta.*santa|paix"), "Cai sempre numa sexta, então o fim de semana prolongado vem de graça.", "Se for viajar, saia cedo: a estrada lota na quinta à noite."),
    Conteudo(r("tiradentes"), "Lembra a execução de Tiradentes, em 1792.", "Boa hora de reler a história da Inconfidência Mineira."),
    Conteudo(r("trabalh"), "É o feriado mais coerente do calendário: folga justamente no Dia do Trabalho.", "Se cair no meio da semana, pense bem antes de gastar férias."),
    Conteudo(r("corpus"), "Cai sempre numa quinta, então basta 1 dia de férias para ter 4 de folga.", "É ponto facultativo em muitos lugares. Confirme antes.", true),
    Conteudo(r("independ"), "7 de setembro: desfile de manhã, sofá à tarde.", "Se cair numa terça ou quinta, é dos melhores do ano para emendar."),
    Conteudo(r("aparecida"), "Em 12 de outubro se juntam a padroeira e o Dia das Crianças.", "Cidades turísticas lotam. Reserve com antecedência."),
    Conteudo(r("finados"), "Feriado sério, de ir ao cemitério ou só ficar quieto em casa.", "Não é dia de balada. Planeje algo tranquilo."),
    Conteudo(r("consci"), "20 de novembro lembra Zumbi dos Palmares e virou feriado nacional em 2023.", "Procure uma atividade cultural na sua cidade: costumam ser gratuitas."),
    Conteudo(r("proclama"), "Em 15 de novembro de 1889 o Brasil virou república.", "Quando cai no fim de semana, é a decepção do ano."),
    Conteudo(r("natal"), "Todo mundo ocupado no dia 24 e ninguém lembra que o 26 existe.", "O dia 26 não é feriado: se emendar, conta como férias."),
)

private val CONTEUDO_PADRAO = Conteudo(r("."), "Mais um feriado pra gente não reclamar.", "")

private fun conteudoDe(nome: String): Conteudo =
    CONTEUDO.firstOrNull { it.re.containsMatchIn(nome) } ?: CONTEUDO_PADRAO

private val PASCOA = r("p[aá]scoa")
fun normalizarLista(pares: List<Pair<String, String>>): List<Bruto> =
    pares
        .filterNot { PASCOA.containsMatchIn(it.second) }
        .distinctBy { it.first }
        .map { (iso, nome) ->
            val c = conteudoDe(nome)
            Bruto(iso, nome, LocalDate.parse(iso), c.frase, c.dica, c.facultativo)
        }
        .sortedBy { it.data }
fun calcular(bruto: Map<Int, List<Bruto>>, anos: List<Int>, p: Perfil): Map<Int, List<Feriado>> {
    val todos = anos.flatMap { bruto[it].orEmpty() }
    val escala = p.vinculo != "estudante" && p.feriadoTrabalhado
    val folgas = todos.filter { !escala && (!it.facultativo || p.facultativos) }.map { it.iso }.toSet()
    return anos.associateWith { a ->
        bruto[a].orEmpty().map { f ->
            val r = analisar(f, p, folgas)
            Feriado(f.iso, f.nome, f.data, r.tipo, r.resumo, r.nota, r.custo, r.folgaTotal, r.dias, f.frase, f.dica, f.facultativo)
        }
    }
}
fun preposDia(d: LocalDate): String {
    val w = d.dow()
    return (if (w == 0 || w == 6) "num " else "numa ") + DIAS[w].replace("-feira", "")
}

class Chamada(val titulo: String, val texto: String)

fun chamada(hoje: LocalDate, f: Feriado): Chamada {
    val n = ChronoUnit.DAYS.between(hoje, f.data)
    val quando = when (n) {
        0L -> "hoje"
        1L -> "amanhã"
        else -> "daqui a $n dias"
    }
    return Chamada("${f.nome}, $quando.", "Cai ${preposDia(f.data)}. ${f.resumo}")
}

fun placar(ano: Int, lista: List<Feriado>, hoje: LocalDate): String {
    val atual = ano == hoje.year
    val n = lista.count { it.tipo == Tipo.OURO && (!atual || !it.data.isBefore(hoje)) }
    return if (atual) {
        when (n) {
            0 -> "Em $ano não sobrou nenhuma emenda de ouro."
            1 -> "Em $ano ainda sobra 1 emenda de ouro."
            else -> "Em $ano ainda sobram $n emendas de ouro."
        }
    } else {
        when (n) {
            0 -> "$ano não tem nenhuma emenda de ouro."
            1 -> "$ano tem 1 emenda de ouro."
            else -> "$ano tem $n emendas de ouro."
        }
    }
}
