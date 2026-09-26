package de.davis.keygo.feature.password_health.domain

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.di.annotation.AppScopeQualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

@Single
class PasswordHealthAttention(
    session: Session,
    @AppScopeQualifier appScope: CoroutineScope,
) {

    private val count = MutableStateFlow(0)

    // A scan that finishes after locking can still publish, so the lock state gates the value
    // rather than a clear() that might run first.
    val needsAttention: StateFlow<Int> =
        combine(count, session.isActive) { count, active -> if (active) count else 0 }
            .stateIn(appScope, SharingStarted.Eagerly, 0)

    internal fun update(count: Int) {
        this.count.update { count }
    }

    internal fun clear() = update(0)
}
