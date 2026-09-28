package de.davis.keygo.feature.password_health.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.util.data.serializer.DefaultProtoSerializer
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthNotificationState
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthReportStore
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthSettings
import de.davis.keygo.feature.password_health.di.annotation.BreachedQualifier
import de.davis.keygo.feature.password_health.di.annotation.HealthNotificationStateQualifier
import de.davis.keygo.feature.password_health.di.annotation.HealthReportStore
import de.davis.keygo.feature.password_health.di.annotation.HealthSettingsQualifier
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

    private val LOOKUP_TIMEOUT = 3.seconds
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

    private val Context.healthSettingsDataStore by dataStore(
        "health_settings.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoHealthSettings.getDefaultInstance(),
            parser = ProtoHealthSettings.parser()
        )
    )

    private val Context.healthReportStoreDataStore by dataStore(
        "password_health_report_store.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoHealthReportStore.getDefaultInstance(),
            parser = ProtoHealthReportStore.parser()
        )
    )

    private val Context.healthNotificationStateDataStore by dataStore(
        "health_notification_state.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoHealthNotificationState.getDefaultInstance(),
            parser = ProtoHealthNotificationState.parser()
        )
    )

    @Single
    @HealthSettingsQualifier
    internal fun provideHealthSettingsDataStore(context: Context) =
        context.healthSettingsDataStore

    @Single
    @HealthReportStore
    internal fun provideHealthReportStoreDataStore(context: Context) =
        context.healthReportStoreDataStore

    @Single
    @HealthNotificationStateQualifier
    internal fun provideHealthNotificationStateDataStore(context: Context) =
        context.healthNotificationStateDataStore

    private fun Context.versionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
}
