package de.davis.keygo.feature.settings.data.repository

import android.content.Context
import de.davis.keygo.feature.settings.domain.repository.AppVersionRepository
import org.koin.core.annotation.Single

@Single
internal class AppVersionRepositoryImpl(
    private val applicationContext: Context,
) : AppVersionRepository {

    private val packageInfo by lazy {
        applicationContext.packageManager
            .getPackageInfo(applicationContext.packageName, 0)
    }

    override val versionName: String by lazy {
        packageInfo.versionName.orEmpty()
    }
}
