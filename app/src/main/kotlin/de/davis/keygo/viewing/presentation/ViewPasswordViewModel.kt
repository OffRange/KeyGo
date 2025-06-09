package de.davis.keygo.viewing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.navigation.Navigator
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.viewing.presentation.model.ViewPasswordState
import de.davis.keygo.viewing.presentation.model.ViewPasswordUiEvent
import de.davis.keygo.viewing.presentation.model.asObfuscatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

@KoinViewModel
class ViewPasswordViewModel(
    passwordRepository: PasswordRepository,
    @InjectedParam
    private val itemId: Long,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val estimatePasswordStrength: EstimatePasswordStrengthUseCase, /* TODO store in db or make it a core use-case*/
    private val navigator: Navigator,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewPasswordState())
    val state = _state.asStateFlow()

    init {
        passwordRepository.observeVaultPasswordById(itemId)
            .onEach { password ->
                val obfuscatedString = cryptographicScopeProvider.scope {
                    password.encryptedData.decrypt().decodeToString()
                }.asObfuscatedString()

                _state.update {
                    it.copy(
                        name = password.name,
                        password = obfuscatedString,
                        passwordStrengthScore = estimatePasswordStrength(obfuscatedString.raw),
                        username = password.username ?: "",
                        website = password.website ?: "",
                        note = password.note ?: "",
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ViewPasswordUiEvent) {
        when (event) {
            ViewPasswordUiEvent.CopyPassword -> TODO()
            ViewPasswordUiEvent.OnBackClick -> viewModelScope.launch {
                navigator.navigateUp(detail = true)
            }

            ViewPasswordUiEvent.OpenWebsite -> TODO()
        }
    }
}