package br.com.mercadofinancas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.mercadofinancas.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class DashboardMetrics(
    val totalDataCompra: Double = 0.0,
    val totalFaturaMes: Double = 0.0,
    val totalDescontos: Double = 0.0,
    val qtdCompras: Int = 0,
    val ticketMedio: Double = 0.0,
    val inflacaoPessoalPct: Double = 0.0,
    val gastosPorCategoria: Map<String, Double> = emptyMap(),
    val gastosPorCentroCusto: Map<String, Double> = emptyMap(),
    val topProdutos: List<Pair<String, Double>> = emptyList(),
    val topMercados: List<Pair<String, Double>> = emptyList(),
    val evolucaoMensal: List<Triple<String, Double, Double>> = emptyList()
)

class MercadoViewModel(private val dao: CompraDao) : ViewModel() {
    private val gson = Gson()

    val mesSelecionado = MutableStateFlow(YearMonth.now().toString())
    val mercadoFiltro = MutableStateFlow("ALL")
    val centroCustoFiltro = MutableStateFlow("ALL")
    val modoVisaoFatura = MutableStateFlow(false)

    val orcamentoMensal: StateFlow<Double> = dao.observarConfig("orcamento_mensal")
        .map { it?.valor?.toDoubleOrNull() ?: 2500.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2500.0)

    val categorias: StateFlow<List<String>> = dao.observarConfig("categorias")
        .map { cfg ->
            cfg?.valor?.let { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
                ?: listOf("Mercearia e Básicos", "Carnes e Frios", "Hortifrúti", "Bebidas", "Padaria e Lanches", "Limpeza", "Higiene Pessoal", "Utilidades")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val centrosCusto: StateFlow<List<String>> = dao.observarConfig("centros_custo")
        .map { cfg ->
            cfg?.valor?.let { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
                ?: listOf("Casa", "Pets", "Lazer/Churrasco", "Outros")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todasCompras: StateFlow<List<CompraComItens>> = dao.observarTodasCompras()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        todasCompras, mesSelecionado, mercadoFiltro, centroCustoFiltro, modoVisaoFatura
    ) { compras, mesStr, mercFiltro, ccFiltro, isFatura ->
        calcularMetricas(compras, mesStr, mercFiltro, ccFiltro, isFatura)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    private fun calcularMetricas(
        compras: List<CompraComItens>,
        mesStr: String,
        mercFiltro: String,
        ccFiltro: String,
        isFatura: Boolean
    ): DashboardMetrics {
        val ymAlvo = runCatching { YearMonth.parse(mesStr) }.getOrElse { YearMonth.now() }
        val filtradasMercado = compras.filter { mercFiltro == "ALL" || it.compra.supermercado == mercFiltro }

        var totalDataCompra = 0.0
        var totalFaturaMes = 0.0
        var totalDescontos = 0.0
        var qtdCompras = 0

        val catMap = mutableMapOf<String, Double>()
        val ccMap = mutableMapOf<String, Double>()
        val prodMap = mutableMapOf<String, Double>()
        val mercMap = mutableMapOf<String, Double>()

        for (ci in filtradasMercado) {
            val ymCompra = runCatching { YearMonth.parse(ci.compra.dataIso.take(7)) }.getOrNull() ?: continue
            val parcelas = ci.compra.parcelas.coerceAtLeast(1)
            val diffMeses = ChronoUnit.MONTHS.between(ymCompra, ymAlvo)

            if (ymCompra == ymAlvo) {
                var somaCompra = 0.0
                for (it in ci.itens) {
                    if (ccFiltro == "ALL" || it.centroCusto == ccFiltro) {
                        somaCompra += it.valorLiquido
                        totalDescontos += it.descontoItem
                        if (!isFatura) {
                            catMap[it.categoria] = (catMap[it.categoria] ?: 0.0) + it.valorLiquido
                            ccMap[it.centroCusto] = (ccMap[it.centroCusto] ?: 0.0) + it.valorLiquido
                            prodMap[it.produto] = (prodMap[it.produto] ?: 0.0) + it.valorLiquido
                        }
                    }
                }
                if (somaCompra > 0.0 || ccFiltro == "ALL") {
                    totalDataCompra += somaCompra
                    qtdCompras++
                    if (!isFatura) mercMap[ci.compra.supermercado] = (mercMap[ci.compra.supermercado] ?: 0.0) + somaCompra
                }
            }

            if (diffMeses in 0 until parcelas) {
                var somaFatura = 0.0
                for (it in ci.itens) {
                    if (ccFiltro == "ALL" || it.centroCusto == ccFiltro) {
                        val cota = it.valorLiquido / parcelas
                        somaFatura += cota
                        if (isFatura) {
                            catMap[it.categoria] = (catMap[it.categoria] ?: 0.0) + cota
                            ccMap[it.centroCusto] = (ccMap[it.centroCusto] ?: 0.0) + cota
                            prodMap[it.produto] = (prodMap[it.produto] ?: 0.0) + cota
                        }
                    }
                }
                totalFaturaMes += somaFatura
                if (isFatura && somaFatura > 0.0) {
                    mercMap[ci.compra.supermercado] = (mercMap[ci.compra.supermercado] ?: 0.0) + somaFatura
                }
            }
        }

        val evolucao = (-3..2).map { offset ->
            val ym = ymAlvo.plusMonths(offset.toLong())
            val dSum = filtradasMercado.filter { it.compra.dataIso.startsWith(ym.toString()) }.sumOf { it.compra.valorTotalLiquido }
            val fSum = filtradasMercado.sumOf { ci ->
                val ymc = runCatching { YearMonth.parse(ci.compra.dataIso.take(7)) }.getOrNull() ?: return@sumOf 0.0
                val p = ci.compra.parcelas.coerceAtLeast(1)
                val df = ChronoUnit.MONTHS.between(ymc, ym)
                if (df in 0 until p) ci.compra.valorTotalLiquido / p else 0.0
            }
            Triple(ym.toString(), dSum, fSum)
        }

        return DashboardMetrics(
            totalDataCompra = totalDataCompra,
            totalFaturaMes = totalFaturaMes,
            totalDescontos = totalDescontos,
            qtdCompras = qtdCompras,
            ticketMedio = if (qtdCompras > 0) totalDataCompra / qtdCompras else 0.0,
            gastosPorCategoria = catMap,
            gastosPorCentroCusto = ccMap,
            topProdutos = prodMap.toList().sortedByDescending { it.second }.take(10),
            topMercados = mercMap.toList().sortedByDescending { it.second },
            evolucaoMensal = evolucao
        )
    }

    fun salvarCompra(compra: CompraEntity, itens: List<ItemCompraEntity>) {
        viewModelScope.launch { dao.salvarCompraCompleta(compra, itens) }
    }

    fun deletarCompra(compra: CompraEntity) {
        viewModelScope.launch { dao.deletarCompra(compra) }
    }

    fun atualizarOrcamento(valor: Double) {
        viewModelScope.launch { dao.salvarConfig(ConfigEntity("orcamento_mensal", valor.toString())) }
    }

    fun atualizarCategorias(lista: List<String>) {
        viewModelScope.launch { dao.salvarConfig(ConfigEntity("categorias", gson.toJson(lista))) }
    }

    fun atualizarCentrosCusto(lista: List<String>) {
        viewModelScope.launch { dao.salvarConfig(ConfigEntity("centros_custo", gson.toJson(lista))) }
    }

    fun exportarBackupJson(): String = gson.toJson(todasCompras.value)

    fun exportarCsvMensal(): String {
        val mes = mesSelecionado.value
        val sb = StringBuilder("\uFEFFData;Supermercado;Produto;Categoria;CentroCusto;Qtd;Unidade;ValorUnit;Desconto;ValorLiquido\n")
        todasCompras.value.filter { it.compra.dataIso.startsWith(mes) }.forEach { ci ->
            ci.itens.forEach { it ->
                sb.append("${ci.compra.dataIso};${ci.compra.supermercado};${it.produto};${it.categoria};${it.centroCusto};${it.quantidade};${it.unidade};${it.valorUnitario};${it.descontoItem};${it.valorLiquido}\n")
            }
        }
        return sb.toString()
    }
}

class MercadoViewModelFactory(private val dao: CompraDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MercadoViewModel(dao) as T
    }
}
