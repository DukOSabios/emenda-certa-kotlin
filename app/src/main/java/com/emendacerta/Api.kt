package com.emendacerta

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Dados: BrasilAPI  https://brasilapi.com.br/api/feriados/v1/{ano}   (sem chave)
 * Reserva: Nager.Date https://date.nager.at/api/v3/PublicHolidays/{ano}/BR
 */
object FeriadosApi {

    suspend fun buscarAno(ano: Int): List<Bruto> = withContext(Dispatchers.IO) {
        try {
            parse(get("https://brasilapi.com.br/api/feriados/v1/$ano"))
        } catch (e: Exception) {
            parse(get("https://date.nager.at/api/v3/PublicHolidays/$ano/BR"))
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): List<Bruto> {
        val arr = JSONArray(json)
        val pares = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            // Nager.Date traz o nome em português em "localName"; a BrasilAPI usa "name"
            val local = o.optString("localName")
            val nome = if (local.isNotBlank()) local else o.optString("name")
            o.getString("date") to nome
        }
        return normalizarLista(pares)
    }
}

sealed interface Estado {
    object Carregando : Estado
    object Erro : Estado
    data class Pronto(val porAno: Map<Int, List<Bruto>>) : Estado
}

class FeriadosViewModel : ViewModel() {
    private val _estado = MutableStateFlow<Estado>(Estado.Carregando)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _estado.value = Estado.Carregando
            val ano = LocalDate.now().year
            _estado.value = try {
                val listas = coroutineScope {
                    listOf(ano, ano + 1).map { a -> async { FeriadosApi.buscarAno(a) } }.awaitAll()
                }
                Estado.Pronto(mapOf(ano to listas[0], (ano + 1) to listas[1]))
            } catch (e: Exception) {
                Estado.Erro
            }
        }
    }
}
object PerfilStore {
    private const val ARQ = "emenda-certa"
    private const val CHAVE = "perfil:v1"

    /** null quando o usuário ainda não preencheu o perfil */
    fun carregar(ctx: Context): Perfil? {
        val t = ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE).getString(CHAVE, null) ?: return null
        return try {
            val o = JSONObject(t)
            val padrao = Perfil()
            val dias = o.optJSONArray("dias")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: padrao.dias
            Perfil(
                vinculo = o.optString("vinculo", padrao.vinculo),
                nivel = o.optString("nivel", padrao.nivel),
                dias = dias,
                feriadoTrabalhado = o.optBoolean("feriadoTrabalhado", padrao.feriadoTrabalhado),
                facultativos = o.optBoolean("facultativos", padrao.facultativos),
                orcamento = o.optInt("orcamento", padrao.orcamento),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun salvar(ctx: Context, p: Perfil) {
        val o = JSONObject()
            .put("vinculo", p.vinculo)
            .put("nivel", p.nivel)
            .put("dias", JSONArray(p.dias))
            .put("feriadoTrabalhado", p.feriadoTrabalhado)
            .put("facultativos", p.facultativos)
            .put("orcamento", p.orcamento)
        ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE).edit().putString(CHAVE, o.toString()).apply()
    }
}
