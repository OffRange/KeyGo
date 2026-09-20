package de.davis.keygo.feature.password_health.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.repository.BreachCheckStateRepository
import de.davis.keygo.feature.password_health.domain.usecase.PasswordHealthReportUseCase
import de.davis.keygo.feature.password_health.presentation.model.HealthSection
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.model.RunPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PasswordHealthViewModel(
    private val passwordHealth: PasswordHealthReportUseCase,
    private val breachCheckStateRepository: BreachCheckStateRepository,
) : ViewModel() {

    private val _base = MutableStateFlow(PasswordHealthUiState(phase = RunPhase.FirstLoad))
    val uiState = combine(
        _base,
        breachCheckStateRepository.observeBreachCheckState(),
    ) { base, breachCheckState ->
        base.copy(breachCheckEnabled = breachCheckState.enabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _base.value
    )

    private var run: Job? = null

    fun onEvent(event: PasswordHealthUiEvent) {
        when (event) {
            PasswordHealthUiEvent.RunHealthCheck -> runHealthCheck(RunPhase.FirstLoad)
            PasswordHealthUiEvent.RefreshHealthCheck -> runHealthCheck(RunPhase.Refresh)

            is PasswordHealthUiEvent.OnBreachCheckChanged -> viewModelScope.launch {
                breachCheckStateRepository.setBreachEnabled(event.enabled)
                if (event.enabled) runHealthCheck(RunPhase.FirstLoad)
            }

            PasswordHealthUiEvent.DismissGeneratePassword -> {}
            is PasswordHealthUiEvent.PasswordGenerated -> {}
        }
    }

    private fun runHealthCheck(phase: RunPhase) {
        if (run?.isActive == true) return

        run = viewModelScope.launch {
            _base.update { it.copy(phase = phase) }

            val report = passwordHealth()
            _base.update { state ->
                report.fold(
                    onSuccess = { state.withReport(it) },
                    onFailure = { state.withError(it) },
                )
            }
        }
    }
}

private fun PasswordHealthUiState.withReport(report: PasswordHealthReport) = copy(
    phase = RunPhase.Idle,
    error = null,
    totalPasswordCount = report.totalPasswordsScanned,
    healthSections = report.toSections(),
    checkGaps = report.gaps,
    unreadable = report.unreadable,
)

private fun PasswordHealthUiState.withError(error: PasswordHealthReportError) = copy(
    phase = RunPhase.Idle,
    error = error,
    totalPasswordCount = 0,
    healthSections = emptyList(),
    checkGaps = emptyMap(),
    unreadable = emptySet(),
)

private fun PasswordHealthReport.toSections(): List<HealthSection> {
    val groupsBySeverity = groups.groupBy { it.maxSeverity }
    val standaloneBySeverity = standalone.groupBy { requireNotNull(it.maxSeverity) }

    return FindingSeverity.entries.sortedDescending().mapNotNull { severity ->
        val g = groupsBySeverity[severity].orEmpty().sortedByDescending { it.members.size }
        val s = standaloneBySeverity[severity].orEmpty().sortedByDescending { it.issues.size }
        if (g.isEmpty() && s.isEmpty()) null else HealthSection(severity, g, s)
    }
}
