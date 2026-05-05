package de.davis.keygo.core.item.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.item.data.local.VaultContextSerializer
import de.davis.keygo.core.item.data.local.datasource.DatabaseModule
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [DatabaseModule::class])
@Configuration
@ComponentScan("de.davis.keygo.core.item")
object CoreItemModule {

    private const val VAULT_CONTEXT_STORE_NAME = "vault_context.pb"

    private val Context.vaultContextDataStore by dataStore(
        fileName = VAULT_CONTEXT_STORE_NAME,
        serializer = VaultContextSerializer,
    )

    @Single
    internal fun provideVaultContextDataStore(context: Context) =
        context.vaultContextDataStore

    @Single
    internal fun provideNbvcxz() = Nbvcxz()
}
