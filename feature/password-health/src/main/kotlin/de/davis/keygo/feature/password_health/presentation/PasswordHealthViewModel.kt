package de.davis.keygo.feature.password_health.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.item.view.domain.WebsiteHandler
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.PasswordFixError
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.repository.BreachCheckStateRepository
import de.davis.keygo.feature.password_health.domain.usecase.PasswordHealthReportUseCase
import de.davis.keygo.feature.password_health.presentation.model.FixFlow
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
    private val createNewOrUpdateLogin: CreateNewOrUpdateLoginUseCase,
    private val breachCheckStateRepository: BreachCheckStateRepository,
    private val websiteHandler: WebsiteHandler,
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

            is PasswordHealthUiEvent.FixClicked ->
                _base.update { it.copy(fixFlow = FixFlow.Generating(event.itemId)) }

            is PasswordHealthUiEvent.PasswordGenerated -> _base.update { state ->
                val target = state.fixFlow ?: return@update state
                state.copy(
                    fixFlow = FixFlow.Pending(itemId = target.itemId, password = event.password),
                )
            }

            PasswordHealthUiEvent.DismissGeneratePassword -> _base.update {
                if (it.fixFlow is FixFlow.Generating) it.copy(fixFlow = null) else it
            }

            is PasswordHealthUiEvent.OpenSite -> websiteHandler.openWebsite(event.url)

            PasswordHealthUiEvent.DiscardFix -> _base.update { it.copy(fixFlow = null) }

            PasswordHealthUiEvent.ConfirmPasswordChanged -> applyPendingFix()
        }
    }

    private fun applyPendingFix() {
        val pending = _base.value.pendingFix ?: return
        if (pending.applying) return

        viewModelScope.launch {
            _base.update {
                it.copy(
                    fixFlow = pending.copy(applying = true, error = null),
                    optimisticallyFixed = it.optimisticallyFixed + pending.itemId,
                )
            }

            createNewOrUpdateLogin(
                UpsertLogin.update(itemId = pending.itemId, password = set(pending.password)),
            ).fold(
                onSuccess = {
                    _base.update { it.copy(fixFlow = null) }
                    runHealthCheck(RunPhase.Refresh, restartInFlight = true)
                },
                onFailure = { errors ->
                    val error =
                        if (errors.any { it is ItemUpsertError.CryptoError }) PasswordFixError.Locked
                        else PasswordFixError.Save
                    _base.update {
                        it.copy(
                            fixFlow = pending.copy(applying = false, error = error),
                            optimisticallyFixed = it.optimisticallyFixed - pending.itemId,
                        )
                    }
                },
            )
        }
    }

    private fun runHealthCheck(phase: RunPhase, restartInFlight: Boolean = false) {
        if (run?.isActive == true) {
            if (!restartInFlight) return
            run?.cancel()
        }

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
    reportedSections = report.toSections(),
    checkGaps = report.gaps,
    unreadable = report.unreadable,
    optimisticallyFixed = emptySet(),
)

private fun PasswordHealthUiState.withError(error: PasswordHealthReportError) = copy(
    phase = RunPhase.Idle,
    error = error,
    totalPasswordCount = 0,
    reportedSections = emptyList(),
    checkGaps = emptyMap(),
    unreadable = emptySet(),
    optimisticallyFixed = emptySet(),
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
