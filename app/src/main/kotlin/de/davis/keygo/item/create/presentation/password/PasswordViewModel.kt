package de.davis.keygo.item.create.presentation.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import de.davis.keygo.R
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.alias.ItemIdNone
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.presentation.UIText
import de.davis.keygo.core.presentation.model.InputFieldError
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.item.core.domain.model.PasswordError
import de.davis.keygo.item.core.domain.model.Upsert
import de.davis.keygo.item.core.domain.model.fieldUpdate
import de.davis.keygo.item.core.domain.usecase.CreateNewOrUpdatePassword
import de.davis.keygo.item.create.domain.PasswordGenerator
import de.davis.keygo.item.create.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.PasswordUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
class PasswordViewModel(
    passwordGenerator: PasswordGenerator,
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val createNewOrUpdatePassword: CreateNewOrUpdatePassword,
    private val snackbarManager: SnackbarManager
) : GeneratePasswordViewModel(passwordGenerator, passwordStrengthEstimator) {

    private val passwordTextFieldState = TextFieldState()
    private val _uiState =
        MutableStateFlow(PasswordUiState(passwordTextFieldState = passwordTextFieldState))

    val state = _uiState
        .onStart {
            observePasswordTextField()
            observeGenerator()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PasswordUiState(passwordTextFieldState = passwordTextFieldState)
        )

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    private var itemId by Delegates.notNull<ItemId>()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordTextField() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { passwordStrengthEstimator(it.toString()) }
            .distinctUntilChanged()
            .onEach { score ->
                _uiState.update {
                    it.copy(strengthScore = score)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun observeGenerator() {
        generationState.onEach { state ->
            _uiState.update {
                it.copy(generatePasswordState = state)
            }
        }.launchIn(viewModelScope)

        finalPassword.onEach { password ->
            passwordTextFieldState.setTextAndPlaceCursorAtEnd(password)
            _uiState.update {
                it.copy(generatePasswordBottomSheetVisible = false)
            }
        }.launchIn(viewModelScope)
    }

    private fun navigateUp() {
        viewModelScope.launch {
            navigationEventChannel.send(NavigationEvent.NavigateBack)
        }
    }

    fun init(itemId: ItemId) {
        this.itemId = itemId
        if (itemId == ItemIdNone) return

        passwordRepository.observeVaultPasswordById(itemId)
            .onEach { password ->
                coroutineScope {
                    val pwdDeferred = async {
                        cryptographicScopeProvider.scope {
                            password.encryptedData.decrypt().decodeToString()
                        }
                    }

                    val totpSecret = password.totpSecret?.let { totpSecret ->
                        async {
                            cryptographicScopeProvider.scope {
                                totpSecret.encodedSecret.decrypt().decodeToString()
                            }
                        }
                    }

                    passwordTextFieldState.setTextAndPlaceCursorAtEnd(pwdDeferred.await())

                    _uiState.update {
                        it.copy(
                            nameTextFieldState = TextFieldState(password.name),
                            totpTextFieldState = TextFieldState(totpSecret?.await() ?: ""),
                            usernameTextFieldState = TextFieldState(password.username ?: ""),
                            websiteTextFieldState = TextFieldState(password.website ?: ""),
                            notesTextFieldState = TextFieldState(password.note ?: "")
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: PasswordUiEvent) {
        when (event) {
            is PasswordUiEvent.OnSubmit -> {
                viewModelScope.launch {
                    val state = _uiState.value
                    createNewOrUpdatePassword(
                        upsert = when (itemId == ItemIdNone) {
                            true -> Upsert.Create(
                                name = fieldUpdate(state.nameTextFieldState.text.toString()),
                                username = fieldUpdate(state.usernameTextFieldState.text.toString()),
                                website = fieldUpdate(state.websiteTextFieldState.text.toString()),
                                password = fieldUpdate(state.passwordTextFieldState.text.toString()),
                                totpSecret = fieldUpdate(state.totpTextFieldState.text.toString()),
                                note = fieldUpdate(state.notesTextFieldState.text.toString())
                            )

                            false -> Upsert.Update(
                                vaultId = itemId,
                                name = fieldUpdate(state.nameTextFieldState.text.toString()),
                                username = fieldUpdate(state.usernameTextFieldState.text.toString()),
                                website = fieldUpdate(state.websiteTextFieldState.text.toString()),
                                password = fieldUpdate(state.passwordTextFieldState.text.toString()),
                                totpSecret = fieldUpdate(state.totpTextFieldState.text.toString()),
                                note = fieldUpdate(state.notesTextFieldState.text.toString())
                            )
                        }
                    ).onSuccess {
                        navigateUp()
                    }.onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                nameError = if (failure.contains(PasswordError.BlankName)) InputFieldError.Empty else null,
                                passwordError = if (failure.contains(PasswordError.BlankPassword)) InputFieldError.Empty else null
                            )
                        }

                        if (failure.any { it is PasswordError.InvalidVaultId }) {
                            snackbarManager.sendMessage(
                                message = SnackbarMessage(
                                    message = UIText.ResourceString(R.string.invalid_vault_id)
                                )
                            )
                        }

                        if (failure.any { it is PasswordError.DatabaseError }) {
                            failure.filterIsInstance<PasswordError.DatabaseError>()
                                .first()
                                .let { dbError ->
                                    snackbarManager.sendMessage(
                                        message = SnackbarMessage(
                                            message = UIText.ResourceString(
                                                R.string.database_error,
                                                dbError.throwable.message ?: "no message"
                                            ),
                                        )
                                    )
                                }
                        }
                    }
                }
            }

            is PasswordUiEvent.OnGeneratePasswordClick -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = true) }
            }

            is PasswordUiEvent.OnBackClick -> navigateUp()

            is PasswordUiEvent.OnCloseBottomSheet -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = false) }
            }

            is GeneratePasswordUiEvent -> super.onEvent(event)
        }
    }
}