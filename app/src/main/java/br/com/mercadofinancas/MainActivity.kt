package br.com.mercadofinancas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.mercadofinancas.data.CompraEntity
import br.com.mercadofinancas.data.ItemCompraEntity
import br.com.mercadofinancas.ocr.NotaExtraidaResult
import br.com.mercadofinancas.ui.*
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MercadoFinancasApp
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0F766E),
                    secondary = Color(0xFF0369A1),
                    background = Color(0xFFF8FAFC)
                )
            ) {
                val vm: MercadoViewModel = viewModel(factory = MercadoViewModelFactory(app.database.compraDao()))
                MercadoFinancasRoot(vm)
            }
        }
    }
}

fun formatBrl(v: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercadoFinancasRoot(vm: MercadoViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var draftNota by remember { mutableStateOf<NotaExtraidaResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MercadoFinanças", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F766E),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Painel") })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.CameraAlt, null) }, label = { Text("Foto Nota") })
                NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Icon(Icons.Default.AddCircle, null) }, label = { Text("Lançar") })
                NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Histórico") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                0 -> PainelMensalScreen(vm)
                1 -> CameraScannerScreen(centroCustoPadrao = "Casa") { extraida ->
                    draftNota = extraida
                    currentTab = 2
                }
                2 -> LancamentoCompraScreen(vm = vm, notaPrevia = draftNota) {
                    draftNota = null
                    currentTab = 0
                }
                3 -> HistoricoPrecosScreen(vm)
            }
        }
    }
}

@Composable
fun PainelMensalScreen(vm: MercadoViewModel) {
    val metrics by vm.dashboardMetrics.collectAsState()
    val budget by vm.orcamentoMensal.collectAsState()
    val isFatura by vm.modoVisaoFatura.collectAsState()
    val mesAtual by vm.mesSelecionado.collectAsState()

    val gastoAtivo = if (isFatura) metrics.totalFaturaMes else metrics.totalDataCompra
    val pct = if (budget > 0) (gastoAtivo / budget).toFloat() else 0f
    val barColor = when {
        pct <= 0.8f -> Color(0xFF16A34A)
        pct <= 1.0f -> Color(0xFFD97706)
        else -> Color(0xFFDC2626)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mês: $mesAtual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = isFatura,
                    onClick = { vm.modoVisaoFatura.value = !isFatura },
                    label = { Text(if (isFatura) "Visão: Fatura/Parcelas" else "Visão: Data Compra") }
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Meta Mensal: ${formatBrl(budget)}", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { pct.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = barColor)
                    Text("Gasto: ${formatBrl(gastoAtivo)} (${String.format("%.1f", pct * 100)}%)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Total Compras", style = MaterialTheme.typography.labelSmall)
                        Text(formatBrl(metrics.totalDataCompra), fontWeight = FontWeight.Bold)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Fatura Mês", style = MaterialTheme.typography.labelSmall)
                        Text(formatBrl(metrics.totalFaturaMes), fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    }
                }
            }
        }

        item {
            Text("Gastos por Categoria", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        items(metrics.gastosPorCategoria.toList()) { (cat, valor) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cat)
                Text(formatBrl(valor), fontWeight = FontWeight.Bold)
            }
        }

        item {
            Text("Top 10 Produtos Mais Comprados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        items(metrics.topProdutos) { (prod, valor) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(prod, modifier = Modifier.weight(1f))
                Text(formatBrl(valor), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LancamentoCompraScreen(vm: MercadoViewModel, notaPrevia: NotaExtraidaResult?, onSalvo: () -> Unit) {
    var dataIso by remember(notaPrevia) { mutableStateOf(notaPrevia?.dataIso ?: LocalDate.now().toString()) }
    var mercado by remember(notaPrevia) { mutableStateOf(notaPrevia?.supermercado ?: "") }
    var ccPadrao by remember { mutableStateOf("Casa") }
    var pagamento by remember { mutableStateOf("Pix") }
    var parcelasStr by remember { mutableStateOf("1") }
    val itens = remember(notaPrevia) {
        mutableStateListOf<ItemCompraEntity>().apply {
            if (notaPrevia != null && notaPrevia.itens.isNotEmpty()) addAll(notaPrevia.itens)
        }
    }

    var novoProd by remember { mutableStateOf("") }
    var novaQtd by remember { mutableStateOf("1") }
    var novoUnit by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Lançamento / Conferência de Nota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(value = dataIso, onValueChange = { dataIso = it }, label = { Text("Data (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = mercado, onValueChange = { mercado = it }, label = { Text("Supermercado") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pagamento, onValueChange = { pagamento = it }, label = { Text("Pagamento") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = parcelasStr, onValueChange = { parcelasStr = it }, label = { Text("Parcelas") }, modifier = Modifier.width(100.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = novoProd, onValueChange = { novoProd = it }, label = { Text("Produto") }, modifier = Modifier.weight(2f))
                OutlinedTextField(value = novaQtd, onValueChange = { novaQtd = it }, label = { Text("Qtd") }, modifier = Modifier.weight(0.8f))
                OutlinedTextField(value = novoUnit, onValueChange = { novoUnit = it }, label = { Text("R$") }, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val q = novaQtd.replace(",", ".").toDoubleOrNull() ?: 1.0
                    val p = novoUnit.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (novoProd.isNotBlank() && p > 0) {
                        itens.add(ItemCompraEntity(compraId = 0, produto = novoProd.trim(), categoria = "Mercearia e Básicos", centroCusto = ccPadrao, quantidade = q, unidade = "un", valorUnitario = p, descontoItem = 0.0, valorLiquido = q * p))
                        novoProd = ""; novaQtd = "1"; novoUnit = ""
                    }
                }) { Icon(Icons.Default.AddCircle, contentDescription = "Adicionar", tint = Color(0xFF0F766E)) }
            }
        }
        items(itens) { it ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.produto, fontWeight = FontWeight.Bold)
                        Text("${it.quantidade} ${it.unidade} x ${formatBrl(it.valorUnitario)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatBrl(it.valorLiquido), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { itens.remove(it) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                }
            }
        }
        item {
            val total = itens.sumOf { it.valorLiquido }
            Button(
                onClick = {
                    if (mercado.isNotBlank() && itens.isNotEmpty()) {
                        val parc = parcelasStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        vm.salvarCompra(
                            CompraEntity(dataIso = dataIso, supermercado = mercado.trim(), centroCustoPadrao = ccPadrao, formaPagamento = pagamento, parcelas = parc, valorTotalLiquido = total, valorTotalDesconto = itens.sumOf { it.descontoItem }),
                            itens.toList()
                        )
                        onSalvo()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar Compra (${formatBrl(total)})")
            }
        }
    }
}

@Composable
fun HistoricoPrecosScreen(vm: MercadoViewModel) {
    val compras by vm.todasCompras.collectAsState()
    var busca by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(value = busca, onValueChange = { busca = it }, label = { Text("Buscar produto (Comparador R$/kg ou R$/L) ou Mercado") }, modifier = Modifier.fillMaxWidth())
        }
        items(compras.filter {
            busca.isBlank() || it.compra.supermercado.contains(busca, true) || it.itens.any { i -> i.produto.contains(busca, true) }
        }) { ci ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${ci.compra.supermercado} (${ci.compra.dataIso})", fontWeight = FontWeight.Bold)
                        Text("${ci.itens.size} itens • ${ci.compra.formaPagamento} (${ci.compra.parcelas}x)", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatBrl(ci.compra.valorTotalLiquido), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.deletarCompra(ci.compra) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.Red)
                    }
                }
            }
        }
    }
}
