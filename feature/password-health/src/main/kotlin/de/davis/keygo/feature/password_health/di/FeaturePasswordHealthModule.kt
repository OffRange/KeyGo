package de.davis.keygo.feature.password_health.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.util.data.serializer.DefaultProtoSerializer
import de.davis.keygo.feature.backup.data.local.model.ProtoBreachCheckState
import de.davis.keygo.feature.password_health.di.annotation.BreachedQualifier
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

@Module
@Configuration
@ComponentScan("de.davis.keygo.feature.password_health")
object FeaturePasswordHealthModule {

    private val LOOKUP_TIMEOUT = 15.seconds
    private const val MAX_PARALLEL_LOOKUPS = 8

    @Single
    @BreachedQualifier
    internal fun provideBreachedHttpClient(applicationContext: Context): OkHttpClient {
        val userAgent = "KeyGo/${applicationContext.versionName()}"

        return OkHttpClient.Builder()
            .cache(null)
            .callTimeout(LOOKUP_TIMEOUT)
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_PARALLEL_LOOKUPS })
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", userAgent).build(),
                )
            }
            .build()
    }

    private val Context.breachCheckStateDataStore by dataStore(
        "breach_check_state.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoBreachCheckState.getDefaultInstance(),
            parser = ProtoBreachCheckState.parser()
        )
    )

    @Single
    @BreachedQualifier
    internal fun provideBreachCheckStateDataStore(context: Context) =
        context.breachCheckStateDataStore

    private fun Context.versionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
}
