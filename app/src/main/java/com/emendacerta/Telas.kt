package com.emendacerta

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

const val AUTOR = "Grupo 6"
object Cor {
    val Fundo = Color(0xFFF2F1EA)
    val Folha = Color(0xFFFCFBF7)
    val Tinta = Color(0xFF17140F)
    val Suave = Color(0xFF6B665B)
    val Linha = Color(0xFFD5D1C4)
    val Vermelho = Color(0xFFC81E14)
    val Amarelo = Color(0xFFF4C20D)
    val Fds = Color(0xFFD9D5C8)
}

// Alfa Slab One (números e títulos) + Work Sans (texto), ambas SIL OFL — arquivos em res/font
private val Disp = FontFamily(Font(R.font.alfa_slab_one))
private val Txt = FontFamily(
    Font(R.font.work_sans_regular, FontWeight.Normal),
    Font(R.font.work_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.work_sans_semibold, FontWeight.SemiBold),
    Font(R.font.work_sans_bold, FontWeight.Bold),
)

/** Marca-texto: o fundo amarelo atrás do texto, como se tivesse sido grifado */
private val Grifo = SpanStyle(background = Cor.Amarelo, color = Cor.Tinta)

private val MESES_12 = (0..11).toList()
private val RANK = mapOf("sem" to 0, "fds" to 1, "ferias" to 2, "feriado" to 3)

// ---------------------------------------------------------------------------
@Composable
fun AppRoot(vm: FeriadosViewModel = viewModel()) {
    val ctx = LocalContext.current
    val estado by vm.estado.collectAsState()
    val hoje = remember { LocalDate.now() }
    val salvo = remember { PerfilStore.carregar(ctx) }
    var perfil by remember { mutableStateOf(salvo ?: Perfil()) }
    // na primeira vez abre direto a tela de perfil
    var editando by rememberSaveable { mutableStateOf(salvo == null) }
    var selData by rememberSaveable { mutableStateOf<String?>(null) }
    var ano by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var aba by rememberSaveable { mutableStateOf("lista") } // lista | calendario

    BackHandler(enabled = editando || selData != null) {
        if (editando) editando = false else selData = null
    }

    val dados: Map<Int, List<Feriado>>? = remember(estado, perfil) {
        (estado as? Estado.Pronto)?.let { calcular(it.porAno, it.porAno.keys.sorted(), perfil) }
    }
    val sel: Feriado? = dados?.values?.flatten()?.firstOrNull { it.iso == selData }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cor.Fundo)
            .statusBarsPadding(),
    ) {
        if (editando) {
            TelaPerfil(
                p = perfil,
                onChange = { perfil = it; PerfilStore.salvar(ctx, it) },
                onFechar = { editando = false },
            )
        } else if (sel != null) {
            Detalhe(sel, perfil, onVoltar = { selData = null })
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp)) {
                    Text("Emenda Certa", fontFamily = Disp, fontSize = 36.sp, lineHeight = 42.sp, color = Cor.Tinta)
                    Text(
                        buildAnnotatedString {
                            append("${resumoPerfil(perfil)}. ")
                            withStyle(
                                SpanStyle(
                                    color = Cor.Vermelho, fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Normal, textDecoration = TextDecoration.Underline,
                                ),
                            ) { append("Editar perfil") }
                        },
                        fontFamily = Txt, fontStyle = FontStyle.Italic, color = Cor.Suave, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp).clickable { editando = true },
                    )
                }
                when (val e = estado) {
                    Estado.Carregando -> Centro {
                        CircularProgressIndicator(color = Cor.Vermelho)
                        Spacer(Modifier.height(10.dp))
                        Text("Olhando o calendário…", fontFamily = Txt, color = Cor.Suave)
                    }
                    Estado.Erro -> Centro {
                        Text(
                            "Não consegui buscar os feriados.",
                            fontFamily = Disp, fontSize = 22.sp, color = Cor.Tinta, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Confira sua internet e tente de novo.", fontFamily = Txt, color = Cor.Suave)
                        Spacer(Modifier.height(20.dp))
                        Box(
                            Modifier
                                .background(Cor.Tinta, RoundedCornerShape(2.dp))
                                .clickable { vm.carregar() }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) { Text("Tentar de novo", fontFamily = Txt, fontWeight = FontWeight.Bold, color = Cor.Fundo) }
                    }
                    is Estado.Pronto -> ListaFeriados(
                        dados = dados.orEmpty(),
                        ano = ano,
                        aba = aba,
                        perfil = perfil,
                        hoje = hoje,
                        onAno = { ano = it },
                        onAba = { aba = it },
                        onSelecionar = { selData = it.iso },
                    )
                }
            }
        }
    }
}

@Composable
private fun Centro(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

// ---------------------------------------------------------------------------
@Composable
private fun ListaFeriados(
    dados: Map<Int, List<Feriado>>,
    ano: Int,
    aba: String,
    perfil: Perfil,
    hoje: LocalDate,
    onAno: (Int) -> Unit,
    onAba: (String) -> Unit,
    onSelecionar: (Feriado) -> Unit,
) {
    val anos = dados.keys.sorted()
    val lista = dados[ano].orEmpty()
    val proximo = anos.flatMap { dados[it].orEmpty() }
        .firstOrNull { !it.data.isBefore(hoje) && it.nota > 0 }
    val abertura = proximo?.let { chamada(hoje, it) }
    val plano = remember(lista, perfil, hoje) { montarPlano(lista, perfil, hoje) }
    val cal = aba == "calendario"

    // mapa do calendário: ouro, já emendados e emendas do plano ganham a faixa completa
    val mapa = remember(lista, plano) {
        val m = mutableMapOf<String, String>()
        fun marca(iso: String, p: String) {
            val atual = m[iso]
            if (atual == null || RANK.getValue(p) > RANK.getValue(atual)) m[iso] = p
        }
        lista.forEach { f ->
            if (f.nota == 0) marca(f.iso, "sem")
            else if (f.tipo == Tipo.OURO || f.tipo == Tipo.PRONTA || f.iso in plano.ids) {
                f.dias.forEach { d ->
                    marca(
                        isoDe(d.data),
                        when (d.papel) { Papel.FERIADO -> "feriado"; Papel.FERIAS -> "ferias"; Papel.FDS -> "fds" },
                    )
                }
            } else marca(f.iso, "feriado")
        }
        m.toMap()
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.navigationBarsPadding()) {
        // abertura: texto corrido com números reais, sem cartão
        if (abertura != null) {
            item {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
                    Text(abertura.titulo, fontFamily = Disp, fontSize = 25.sp, lineHeight = 31.sp, color = Cor.Vermelho)
                    Text(
                        abertura.texto,
                        fontFamily = Txt, fontSize = 16.sp, lineHeight = 24.sp, color = Cor.Tinta,
                        modifier = Modifier.padding(top = 6.dp).widthIn(max = 330.dp),
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                anos.forEach { a ->
                    val on = a == ano
                    Text(
                        "$a",
                        fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        color = if (on) Cor.Tinta else Cor.Suave,
                        modifier = Modifier
                            .padding(end = 22.dp)
                            .clickable { onAno(a) }
                            .drawBehind {
                                if (on) {
                                    val h = 4.dp.toPx()
                                    drawRect(Cor.Amarelo, Offset(0f, size.height - h), Size(size.width, h))
                                }
                            }
                            .padding(bottom = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (cal) "ver lista" else "ver calendário",
                    fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = Cor.Vermelho, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onAba(if (cal) "lista" else "calendario") }.padding(bottom = 3.dp),
                )
            }
        }
        item {
            Text(
                placar(ano, lista, hoje),
                fontFamily = Txt, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = Cor.Suave,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
            )
        }
        item {
            Text(
                buildAnnotatedString {
                    withStyle(Grifo.copy(fontWeight = FontWeight.SemiBold)) { append(" seu plano ") }
                    append(" ${plano.texto}")
                },
                fontFamily = Txt, fontSize = 15.sp, lineHeight = 24.sp, color = Cor.Tinta,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp).widthIn(max = 400.dp),
            )
        }
        if (cal) {
            item {
                Text(
                    "Vermelho: feriado. Amarelo: dia que você gasta. Cinza: folga que emenda.",
                    fontFamily = Txt, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = Cor.Suave,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            items(MESES_12, key = { "m$it" }) { m ->
                Mes(ano, m, mapa, perfil, hoje) { iso -> lista.firstOrNull { it.iso == iso }?.let(onSelecionar) }
            }
        } else {
            if (lista.isEmpty()) {
                item { Text("Nenhum feriado encontrado.", fontFamily = Txt, color = Cor.Suave, modifier = Modifier.padding(24.dp)) }
            }
            items(lista, key = { it.iso }) { f ->
                LinhaFeriado(f, passou = f.data.isBefore(hoje), noPlano = f.iso in plano.ids, onClick = { onSelecionar(f) })
            }
        }
    }
}

@Composable
private fun LinhaFeriado(f: Feriado, passou: Boolean, noPlano: Boolean, onClick: () -> Unit) {
    val perdida = f.nota == 0
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .alpha(if (passou) 0.4f else 1f),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cor.Linha))
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(66.dp)) {
                Text(
                    MESES_ABREV[f.data.monthValue - 1].uppercase(),
                    fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    letterSpacing = 1.5.sp, color = Cor.Vermelho,
                )
                Text("${f.data.dayOfMonth}", fontFamily = Disp, fontSize = 38.sp, lineHeight = 44.sp, color = Cor.Tinta)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    f.nome,
                    fontFamily = Txt, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                    color = if (perdida) Cor.Suave else Cor.Tinta,
                    textDecoration = if (perdida) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    buildAnnotatedString {
                        append("${DIAS[f.data.dow()]} · ")
                        if (f.tipo == Tipo.OURO || noPlano) {
                            val extra = if (noPlano && f.tipo != Tipo.OURO) ", no plano" else ""
                            withStyle(Grifo.copy(fontWeight = FontWeight.SemiBold)) { append(" ${f.tipo.titulo.lowercase()}$extra ") }
                        } else {
                            append(f.tipo.titulo.lowercase())
                        }
                    },
                    fontFamily = Txt, fontSize = 14.sp, color = Cor.Suave,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Calendário mensal
@Composable
private fun Mes(
    ano: Int,
    mes: Int,
    mapa: Map<String, String>,
    perfil: Perfil,
    hoje: LocalDate,
    onAbrir: (String) -> Unit,
) {
    val primeiro = LocalDate.of(ano, mes + 1, 1)
    val cels: List<LocalDate?> =
        List(primeiro.dow()) { null } + (1..primeiro.lengthOfMonth()).map { primeiro.withDayOfMonth(it) }
    val linhas = cels.chunked(7).map { r -> r + List(7 - r.size) { null } }

    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
        Text(
            MESES[mes].replaceFirstChar { it.uppercase() },
            fontFamily = Disp, fontSize = 22.sp, color = Cor.Tinta,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            DIAS_ABREV.forEach { d ->
                Text(
                    d.take(1).uppercase(),
                    fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Cor.Suave,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                )
            }
        }
        linhas.forEach { linha ->
            Row(Modifier.fillMaxWidth()) {
                linha.forEach { d ->
                    Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val papel = mapa[isoDe(d)]
                            val folga = d.dow() !in perfil.dias
                            val bg = when (papel) {
                                "feriado" -> Cor.Vermelho
                                "ferias" -> Cor.Amarelo
                                "fds" -> Cor.Fds
                                else -> null
                            }
                            val fg = when {
                                papel == "feriado" -> Color.White
                                papel == "sem" -> Cor.Vermelho
                                folga -> Cor.Suave
                                else -> Cor.Tinta
                            }
                            val ehHoje = d == hoje
                            var m = Modifier.size(32.dp)
                            if (bg != null) m = m.background(bg, RoundedCornerShape(2.dp))
                            m = when {
                                ehHoje -> m.border(2.dp, Cor.Tinta, RoundedCornerShape(2.dp))
                                papel == "sem" -> m.border(1.dp, Cor.Vermelho, RoundedCornerShape(2.dp))
                                else -> m
                            }
                            if (papel == "feriado" || papel == "sem") m = m.clickable { onAbrir(isoDe(d)) }
                            Box(m, contentAlignment = Alignment.Center) {
                                Text("${d.dayOfMonth}", fontFamily = Txt, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Perfil
@Composable
private fun Chip(on: Boolean, onClick: () -> Unit, texto: String) {
    Text(
        texto,
        fontFamily = Txt, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        color = if (on) Cor.Tinta else Cor.Suave,
        modifier = Modifier
            .background(if (on) Cor.Amarelo else Color.Transparent, RoundedCornerShape(2.dp))
            .border(1.dp, Cor.Tinta, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Opcao(on: Boolean, onClick: () -> Unit, titulo: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(end = 14.dp, top = 1.dp)
                .size(24.dp)
                .background(if (on) Cor.Vermelho else Color.Transparent)
                .border(2.dp, if (on) Cor.Vermelho else Cor.Tinta),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Text("✓", fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, fontFamily = Txt, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Cor.Tinta)
            if (sub != null) {
                Text(sub, fontFamily = Txt, fontSize = 13.sp, lineHeight = 19.sp, color = Cor.Suave, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun PassoBotao(texto: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).border(1.dp, Cor.Tinta, RoundedCornerShape(2.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(texto, fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Cor.Tinta) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelaPerfil(p: Perfil, onChange: (Perfil) -> Unit, onFechar: () -> Unit) {
    val v = vinc(p)
    val estud = p.vinculo == "estudante"
    val alterna = { i: Int ->
        val t = if (i in p.dias) p.dias.filter { it != i } else (p.dias + i).sorted()
        if (t.isNotEmpty()) onChange(p.copy(dias = t)) // sempre sobra ao menos um dia
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 56.dp),
    ) {
        Text("Seu perfil", fontFamily = Disp, fontSize = 36.sp, lineHeight = 42.sp, color = Cor.Tinta)
        Text(
            "O calendário se ajusta à sua rotina. Dá pra mudar quando quiser.",
            fontFamily = Txt, fontStyle = FontStyle.Italic, color = Cor.Suave, fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Rotulo("Você é")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VINCULOS.forEach { x -> Chip(p.vinculo == x.id, { onChange(p.copy(vinculo = x.id)) }, x.rotulo) }
        }

        if (estud) {
            Rotulo("Onde estuda")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(p.nivel == "basico", { onChange(p.copy(nivel = "basico")) }, "Escola")
                Chip(p.nivel == "superior", { onChange(p.copy(nivel = "superior")) }, "Faculdade")
            }
        }

        Rotulo("Dias em que você ${if (estud) "tem aula" else "trabalha"}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4, 5, 6, 0).forEach { i -> Chip(i in p.dias, { alterna(i) }, DIAS_ABREV[i]) }
        }

        Rotulo("Sua rotina")
        if (!estud) {
            Opcao(
                on = p.feriadoTrabalhado,
                onClick = { onChange(p.copy(feriadoTrabalhado = !p.feriadoTrabalhado)) },
                titulo = "Minha escala cobre feriados",
                sub = "Comércio, saúde, plantão, 12x36. Os feriados passam a contar como dia de trabalho.",
            )
        }
        Opcao(
            on = p.facultativos,
            onClick = { onChange(p.copy(facultativos = !p.facultativos)) },
            titulo = "Tenho folga em Carnaval e Corpus Christi",
            sub = "Pontos facultativos. Sem isso, eles contam como dia normal.",
        )

        Rotulo("Quanto você topa gastar por ano")
        Row(verticalAlignment = Alignment.CenterVertically) {
            PassoBotao("−") { onChange(p.copy(orcamento = maxOf(0, p.orcamento - 1))) }
            Text(
                "${p.orcamento}",
                fontFamily = Disp, fontSize = 36.sp, color = Cor.Vermelho, textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp),
            )
            PassoBotao("+") { onChange(p.copy(orcamento = minOf(30, p.orcamento + 1))) }
            Text(
                if (p.orcamento == 1) v.un else v.unp,
                fontFamily = Txt, fontSize = 15.sp, color = Cor.Suave,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }

        Box(
            Modifier
                .padding(top = 36.dp)
                .background(Cor.Tinta, RoundedCornerShape(2.dp))
                .clickable { onFechar() }
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) { Text("Ver meu calendário", fontFamily = Txt, fontWeight = FontWeight.Bold, color = Cor.Fundo) }
    }
}

// ---------------------------------------------------------------------------
@Composable
private fun Faixa(dias: List<Dia>, verbo: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = dias.size
        val w: Dp = ((maxWidth - 4.dp * n) / n).coerceAtMost(58.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            dias.forEach { d ->
                val feriado = d.papel == Papel.FERIADO
                val ferias = d.papel == Papel.FERIAS
                val bg = if (feriado) Cor.Vermelho else if (ferias) Cor.Amarelo else Cor.Fds
                val fg = if (feriado) Color.White else Cor.Tinta
                Column(Modifier.width(w)) {
                    Column(
                        Modifier.fillMaxWidth().height(64.dp).background(bg, RoundedCornerShape(2.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            DIAS_ABREV[d.data.dow()].uppercase(),
                            fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                            letterSpacing = 1.sp, color = fg,
                        )
                        Text("${d.data.dayOfMonth}", fontFamily = Disp, fontSize = 22.sp, color = fg)
                    }
                    Text(
                        if (feriado) "feriado" else if (ferias) verbo else "",
                        fontFamily = Txt, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Cor.Suave,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(top = 5.dp).height(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        texto.uppercase(),
        fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        letterSpacing = 1.5.sp, color = Cor.Vermelho,
        modifier = Modifier.padding(top = 34.dp, bottom = 6.dp),
    )
}

@Composable
private fun Detalhe(f: Feriado, perfil: Perfil, onVoltar: () -> Unit) {
    val notas = avisos(f, perfil)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 56.dp),
    ) {
        Text(
            "← voltar",
            fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Cor.Vermelho,
            modifier = Modifier.clickable { onVoltar() }.padding(bottom = 18.dp),
        )

        // a folhinha
        Column(Modifier.fillMaxWidth().border(1.dp, Cor.Tinta).background(Cor.Folha)) {
            Box(Modifier.fillMaxWidth().background(Cor.Vermelho).padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(
                    "${MESES[f.data.monthValue - 1].uppercase()} ${f.data.year}",
                    fontFamily = Txt, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    letterSpacing = 2.5.sp, color = Color.White,
                )
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)) {
                Text("${f.data.dayOfMonth}", fontFamily = Disp, fontSize = 124.sp, lineHeight = 136.sp, color = Cor.Vermelho)
                Text(DIAS[f.data.dow()], fontFamily = Txt, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Cor.Tinta)
                Text(
                    f.nome,
                    fontFamily = Disp, fontSize = 28.sp, lineHeight = 34.sp, color = Cor.Tinta,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }

        Text(
            buildAnnotatedString {
                if (f.tipo == Tipo.OURO) withStyle(Grifo) { append(" ${f.tipo.titulo} ") } else append(f.tipo.titulo)
            },
            fontFamily = Disp, fontSize = 26.sp, lineHeight = 36.sp, color = Cor.Tinta,
            modifier = Modifier.padding(top = 30.dp),
        )
        if (f.nota > 0) {
            Text(
                "nota ${f.nota} de 10",
                fontFamily = Txt, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = Cor.Suave,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            f.resumo,
            fontFamily = Txt, fontSize = 17.sp, lineHeight = 25.sp, color = Cor.Tinta,
            modifier = Modifier.padding(top = 12.dp, bottom = 26.dp),
        )

        if (f.dias.isNotEmpty()) Faixa(f.dias, vinc(perfil).verbo)

        Rotulo("Meu palpite")
        Text(f.frase, fontFamily = Txt, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 28.sp, color = Cor.Tinta)

        if (f.dica.isNotBlank()) {
            Rotulo("Pra aproveitar")
            Text(f.dica, fontFamily = Txt, fontSize = 16.sp, lineHeight = 24.sp, color = Cor.Tinta)
        }

        if (f.facultativo) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Cor.Vermelho, fontWeight = FontWeight.Bold)) { append("* ") }
                    append("Ponto facultativo na maior parte do país. Antes de planejar, confirme se o seu trabalho dá folga.")
                },
                fontFamily = Txt, fontSize = 13.sp, lineHeight = 19.sp, color = Cor.Suave,
                modifier = Modifier.padding(top = 30.dp),
            )
        }

        if (notas.isNotEmpty()) {
            Rotulo("Pro seu caso")
            notas.forEachIndexed { i, n ->
                Text(
                    n,
                    fontFamily = Txt, fontSize = 16.sp, lineHeight = 24.sp, color = Cor.Tinta,
                    modifier = Modifier.padding(top = if (i > 0) 10.dp else 0.dp),
                )
            }
        }

        Text(
            "feito por $AUTOR · dados da BrasilAPI (só feriados nacionais)",
            fontFamily = Txt, fontSize = 12.sp, color = Cor.Suave,
            modifier = Modifier.padding(top = 44.dp),
        )
    }
}
