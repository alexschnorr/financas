package br.com.mercadofinancas.ocr

import br.com.mercadofinancas.data.ItemCompraEntity
import java.time.LocalDate

data class NotaExtraidaResult(
    val dataIso: String,
    val supermercado: String,
    val itens: List<ItemCompraEntity>
)

object NotaFiscalParser {
    private val ignoreRegex = Regex(
        "total|subtotal|troco|dinheiro|cart[aã]o|cr[eé]dito|d[eé]bito|pix|cnpj|cpf|tributos|icms|consumidor|chave|consulta|protocolo|valor pago|desconto",
        RegexOption.IGNORE_CASE
    )
    private val priceRegex = Regex("(\\d{1,4}[,.]\\d{2})")
    private val qtyXPriceRegex = Regex("(\\d+[,.]?\\d*)\\s*[xX*]\\s*(\\d+[,.]\\d{2})")
    private val dateRegex = Regex("(\\d{2})/(\\d{2})/(20\\d{2})")

    fun parseTextoOcr(rawText: String, centroCustoPadrao: String = "Casa"): NotaExtraidaResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var mercado = ""
        var dataIso = LocalDate.now().toString()

        for (i in 0 until minOf(5, lines.size)) {
            val l = lines[i]
            if (l.length >= 4 && !ignoreRegex.containsMatchIn(l)) {
                mercado = l.replace(Regex("[^a-zA-ZÀ-ÿ0-9\\s]"), "").trim()
                if (mercado.isNotEmpty()) break
            }
        }

        dateRegex.find(rawText)?.let { m ->
            val (dd, mm, yyyy) = m.destructured
            dataIso = "$yyyy-$mm-$dd"
        }

        val itens = mutableListOf<ItemCompraEntity>()
        for (line in lines) {
            if (ignoreRegex.containsMatchIn(line)) continue
            val matches = priceRegex.findAll(line).toList()
            if (matches.isEmpty()) continue

            val totalLinha = matches.last().groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
            if (totalLinha <= 0.0 || totalLinha > 8000.0) continue

            var qtd = 1.0
            var unitPrice = totalLinha
            qtyXPriceRegex.find(line)?.let { qm ->
                qtd = qm.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
                unitPrice = qm.groupValues[2].replace(",", ".").toDoubleOrNull() ?: totalLinha
            }

            val nomeLimpo = line
                .replace(Regex("^\\d{3,14}\\s+"), "")
                .replace(qtyXPriceRegex, "")
                .replace(Regex("(\\d{1,4}[,.]\\d{2})\\s*$"), "")
                .replace(Regex("\\b(UN|KG|PCT|LT|ML|GR)\\b", RegexOption.IGNORE_CASE), "")
                .trim()

            if (nomeLimpo.length >= 2) {
                itens.add(
                    ItemCompraEntity(
                        compraId = 0,
                        produto = nomeLimpo,
                        categoria = classificarCategoria(nomeLimpo),
                        centroCusto = centroCustoPadrao,
                        quantidade = qtd,
                        unidade = "un",
                        valorUnitario = unitPrice,
                        descontoItem = 0.0,
                        valorLiquido = totalLinha
                    )
                )
            }
        }
        return NotaExtraidaResult(dataIso, mercado, itens)
    }

    fun classificarCategoria(nome: String): String {
        val n = nome.lowercase()
        return when {
            Regex("carne|frango|bife|picanha|lingui[cç]a|presunto|queijo|mussarela|peixe|til[aá]pia|ovo").containsMatchIn(n) -> "Carnes e Frios"
            Regex("banana|ma[cç][aã]|tomate|cebola|batata|alface|laranja|lim[aã]o|mam[aã]o|cenoura").containsMatchIn(n) -> "Hortifrúti"
            Regex("leite|suco|refrigerante|coca|cerveja|[aá]gua|caf[eé]|achocolatado").containsMatchIn(n) -> "Bebidas"
            Regex("p[aã]o|bolo|biscoito|bolacha|torrada|salgadinho").containsMatchIn(n) -> "Padaria e Lanches"
            Regex("sab[aã]o|detergente|[aá]gua sanit[aá]ria|amaciante|desinfetante|esponja").containsMatchIn(n) -> "Limpeza"
            Regex("sabonete|shampoo|creme dental|papel higi[eê]nico|desodorante").containsMatchIn(n) -> "Higiene Pessoal"
            Regex("ra[cç][aã]o|petisco|saco|pilha|l[aâ]mpada").containsMatchIn(n) -> "Utilidades"
            else -> "Mercearia e Básicos"
        }
    }
}
