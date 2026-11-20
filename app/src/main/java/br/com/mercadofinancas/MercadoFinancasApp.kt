package br.com.mercadofinancas

import android.app.Application
import br.com.mercadofinancas.data.AppDatabase

class MercadoFinancasApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
