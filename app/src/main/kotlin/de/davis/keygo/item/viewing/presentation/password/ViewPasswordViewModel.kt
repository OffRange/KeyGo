package de.davis.keygo.item.viewing.presentation.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.presentation.model.InputFieldError
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.generated.item.VaultItemType
import de.davis.keygo.item.core.domain.model.PasswordError
import de.davis.keygo.item.core.domain.model.Upsert
import de.davis.keygo.item.core.domain.usecase.CreateNewOrUpdatePassword
import de.davis.keygo.item.viewing.domain.WebsiteHandler
import de.davis.keygo.item.viewing.domain.usecase.IsValidUrlUseCase
import de.davis.keygo.item.viewing.presentation.password.model.FieldType
import de.davis.keygo.item.viewing.presentation.password.model.ModificationDialog
import de.davis.keygo.item.viewing.presentation.password.model.ViewPasswordState
import de.davis.keygo.item.viewing.presentation.password.model.ViewPasswordUiEvent
import de.davis.keygo.item.viewing.presentation.password.model.asObfuscatedString
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
import kotlin.properties.Delegates

@KoinViewModel
class ViewPasswordViewModel(
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val updatePassword: CreateNewOrUpdatePassword,
    private val isValidUrl: IsValidUrlUseCase,
    private val websiteHandler: WebsiteHandler
) : ViewModel() {

    private val _state = MutableStateFlow(ViewPasswordState())
    val state = _state.asStateFlow()

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    private var itemId by Delegates.notNull<ItemId>()

    fun init(itemId: ItemId) {
        this.itemId = itemId
        passwordRepository.observeVaultPasswordById(itemId)
            .onEach { password ->
                val obfuscatedString = cryptographicScopeProvider.scope {
                    password.encryptedData.decrypt().decodeToString()
                }.asObfuscatedString()

                _state.update {
                    it.copy(
                        name = password.name,
                        password = obfuscatedString,
                        passwordStrengthScore = password.score,
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

            ViewPasswordUiEvent.OnEditRequest -> {
                viewModelScope.launch {
                    navigationEventChannel.send(
                        NavigationEvent.NavigateToEdit(
                            VaultItemType.Password,
                            itemId
                        )
                    )
                }
            }

            ViewPasswordUiEvent.OnCloseDialog -> {
                _state.update { it.copy(modificationDialog = null) }
            }

            is ViewPasswordUiEvent.OnModifyFieldRequest -> {
                val fieldType = event.fieldType
                val textFieldState = when (fieldType) {
                    FieldType.Name -> _state.value.name
                    FieldType.Password -> _state.value.password.raw
                    FieldType.Username -> _state.value.username
                    FieldType.Website -> _state.value.website
                    FieldType.Note -> _state.value.note
                }

                _state.update {
                    it.copy(
                        modificationDialog = ModificationDialog(
                            fieldType = fieldType,
                            textFieldState = TextFieldState(textFieldState),
                        )
                    )
                }
            }

            ViewPasswordUiEvent.OnSubmitModification -> {
                val dialog = _state.value.modificationDialog ?: return
                val newText = dialog.textFieldState.text.toString()

                viewModelScope.launch {
                    updatePassword(
                        when (dialog.fieldType) {
                            FieldType.Name -> Upsert.Update(
                                vaultId = itemId,
                                name = newText
                            )


                            FieldType.Password -> Upsert.Update(
                                vaultId = itemId,
                                password = newText
                            )

                            FieldType.Username -> Upsert.Update(
                                vaultId = itemId,
                                username = newText
                            )

                            FieldType.Website -> Upsert.Update(
                                vaultId = itemId,
                                website = newText
                            )

                            FieldType.Note -> Upsert.Update(
                                vaultId = itemId,
                                note = newText
                            )
                        }
                    ).onFailure { failure ->
                        _state.update {
                            it.copy(
                                modificationDialog = it.modificationDialog?.copy(
                                    error = if (failure.contains(PasswordError.BlankPassword)
                                        || failure.contains(PasswordError.BlankName)
                                    ) InputFieldError.Empty else null
                                ),
                            )
                        }
                    }.onSuccess {
                        _state.update { it.copy(modificationDialog = null) }
                    }
                }
            }
        }
    }
}