package br.com.mercadofinancas.data

import androidx.room.*

@Entity(tableName = "compras")
data class CompraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dataIso: String,
    val supermercado: String,
    val centroCustoPadrao: String,
    val formaPagamento: String,
    val parcelas: Int = 1,
    val valorTotalLiquido: Double,
    val valorTotalDesconto: Double
)

@Entity(
    tableName = "itens_compra",
    foreignKeys = [
        ForeignKey(
            entity = CompraEntity::class,
            parentColumns = ["id"],
            childColumns = ["compraId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("compraId")]
)
data class ItemCompraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val compraId: Long,
    val produto: String,
    val categoria: String,
    val centroCusto: String,
    val quantidade: Double,
    val unidade: String,
    val conteudoEmbalagem: Double? = null,
    val unidadeEmbalagem: String? = null,
    val valorUnitario: Double,
    val descontoItem: Double,
    val valorLiquido: Double
) {
    fun precoPadronizado(): Pair<Double, String> {
        val pack = conteudoEmbalagem ?: 1.0
        return when {
            unidade == "kg" || unidadeEmbalagem == "kg" -> Pair(valorLiquido / (quantidade * pack), "R$/kg")
            unidade == "g" || unidadeEmbalagem == "g" -> Pair(valorLiquido / ((quantidade * pack) / 1000.0), "R$/kg")
            unidade == "L" || unidadeEmbalagem == "L" -> Pair(valorLiquido / (quantidade * pack), "R$/L")
            unidade == "ml" || unidadeEmbalagem == "ml" -> Pair(valorLiquido / ((quantidade * pack) / 1000.0), "R$/L")
            else -> Pair(valorLiquido / quantidade.coerceAtLeast(1.0), "R$/un")
        }
    }
}

data class CompraComItens(
    @Embedded val compra: CompraEntity,
    @Relation(parentColumn = "id", entityColumn = "compraId")
    val itens: List<ItemCompraEntity>
)

@Entity(tableName = "configuracoes")
data class ConfigEntity(
    @PrimaryKey val chave: String,
    val valor: String
)
