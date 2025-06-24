package de.davis.keygo.viewing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.viewing.domain.WebsiteHandler
import de.davis.keygo.viewing.domain.usecase.IsValidUrlUseCase
import de.davis.keygo.viewing.presentation.model.ViewPasswordState
import de.davis.keygo.viewing.presentation.model.ViewPasswordUiEvent
import de.davis.keygo.viewing.presentation.model.asObfuscatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ViewPasswordViewModel(
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val estimatePasswordStrength: EstimatePasswordStrengthUseCase, /* TODO store in db or make it a core use-case*/
    private val isValidUrl: IsValidUrlUseCase,
    private val websiteHandler: WebsiteHandler
) : ViewModel() {

    private val _state = MutableStateFlow(ViewPasswordState())
    val state = _state.asStateFlow()

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun init(itemId: ItemId) {
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
                        canOpenWebsite = isValidUrl(password.website ?: "")
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ViewPasswordUiEvent) {
        when (event) {
            ViewPasswordUiEvent.OnBackClick -> viewModelScope.launch {
                navigationEventChannel.send(NavigationEvent.NavigateBack)
            }

            ViewPasswordUiEvent.OpenWebsite -> {
                val url = _state.value.website
                if (!isValidUrl(url))
                    return

                websiteHandler.openWebsite(url)
            }
        }
    }
}