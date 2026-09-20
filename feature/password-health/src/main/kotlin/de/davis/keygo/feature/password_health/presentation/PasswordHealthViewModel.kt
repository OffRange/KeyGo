package de.davis.keygo.feature.password_health.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.usecase.PasswordHealthReportUseCase
import de.davis.keygo.feature.password_health.presentation.model.HealthSection
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PasswordHealthViewModel(
    private val passwordHealth: PasswordHealthReportUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordHealthUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    private var run: Job? = null

    fun onEvent(event: PasswordHealthUiEvent) {
        when (event) {
            PasswordHealthUiEvent.RunHealthCheck -> runHealthCheck()

            PasswordHealthUiEvent.DismissGeneratePassword -> {}
            is PasswordHealthUiEvent.OnBreachCheckChanged -> {}
            is PasswordHealthUiEvent.PasswordGenerated -> {}
        }
    }

    private fun runHealthCheck() {
        if (run?.isActive == true) return

        run = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val report = passwordHealth()
            _uiState.update { state ->
                report.fold(
                    onSuccess = { state.withReport(it) },
                    onFailure = { state.withError(it) },
                )
            }
        }
    }
}

private fun PasswordHealthUiState.withReport(report: PasswordHealthReport) = copy(
    isLoading = false,
    error = null,
    totalPasswordCount = report.totalPasswordsScanned,
    healthSections = report.toSections(),
    checkGaps = report.gaps,
    unreadable = report.unreadable,
)

private fun PasswordHealthUiState.withError(error: PasswordHealthReportError) = copy(
    isLoading = false,
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
