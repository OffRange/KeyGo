package de.davis.keygo.core.feature.settings

import de.davis.keygo.feature.settings.domain.repository.AppVersionRepository

class FakeAppVersionRepository(
    override val versionName: String = "2.0.0-test",
) : AppVersionRepository
