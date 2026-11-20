package br.com.mercadofinancas.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompraDao {
    @Transaction
    @Query("SELECT * FROM compras ORDER BY dataIso DESC, id DESC")
    fun observarTodasCompras(): Flow<List<CompraComItens>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirCompra(compra: CompraEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirItens(itens: List<ItemCompraEntity>)

    @Transaction
    suspend fun salvarCompraCompleta(compra: CompraEntity, itens: List<ItemCompraEntity>) {
        if (compra.id > 0) {
            deletarItensDaCompra(compra.id)
            inserirCompra(compra)
            inserirItens(itens.map { it.copy(compraId = compra.id) })
        } else {
            val novoId = inserirCompra(compra)
            inserirItens(itens.map { it.copy(compraId = novoId) })
        }
    }

    @Query("DELETE FROM itens_compra WHERE compraId = :compraId")
    suspend fun deletarItensDaCompra(compraId: Long)

    @Delete
    suspend fun deletarCompra(compra: CompraEntity)

    @Query("SELECT * FROM configuracoes WHERE chave = :chave LIMIT 1")
    fun observarConfig(chave: String): Flow<ConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarConfig(config: ConfigEntity)
}
