package de.davis.keygo.feature.password_health.domain

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.di.annotation.AppScopeQualifier
import de.davis.keygo.feature.password_health.domain.usecase.PasswordHealthReportUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(createdAtStart = true)
internal class UnlockHealthScan(
    session: Session,
    passwordHealth: PasswordHealthReportUseCase,
    attention: PasswordHealthAttention,
    @AppScopeQualifier appScope: CoroutineScope,
) {

    init {
        appScope.launch {
            session.isActive.collectLatest { active ->
                if (active) passwordHealth(force = false)
                else attention.clear()
            }
        }
    }
}
