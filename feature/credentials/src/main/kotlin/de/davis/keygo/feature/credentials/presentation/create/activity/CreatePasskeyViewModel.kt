package de.davis.keygo.feature.credentials.presentation.create.activity

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
import de.davis.keygo.feature.credentials.presentation.auth.UnlockOutcome
import de.davis.keygo.feature.credentials.presentation.auth.mapUnlockError
import de.davis.keygo.feature.item.core.domain.model.NewPasskey
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.getPasskeyInformation
import de.davis.keygo.rust.passkey.registerWithResult
import de.davisalessandro.keygo.rust.RegistrationResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreatePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val createNewOrUpdateLogin: CreateNewOrUpdateLoginUseCase,
    private val passkeyManager: PasskeyManager,
    private val accountRepository: AccountRepository,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
) : ViewModel() {

    private val _event = Channel<CreatePasskeyEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val _authState = MutableStateFlow<SessionAuthState>(SessionAuthState.TryBiometric)
    val authState = _authState.asStateFlow()

    private val _excluded = MutableStateFlow(false)
    val excluded = _excluded.asStateFlow()

    private val biometricChannel = Channel<Unit>(Channel.BUFFERED)
    val biometricFlow = biometricChannel.receiveAsFlow()

    /**
     * The request's own `rp.id`, which the relying party may leave empty, until registration
     * replaces it with the id the authenticator resolved.
     */
    private val _rp = MutableStateFlow("")
    val rp = _rp.asStateFlow()

    /** The registered credential, for the login screen to save with a new login. */
    private val _pendingPasskey = MutableStateFlow<NewPasskey?>(null)
    val pendingPasskey = _pendingPasskey.asStateFlow()

    /** Completed by [onUnlocked]. */
    private val unlocked = CompletableDeferred<Unit>()

    /** Completed by [associatePasskeyAndFinish] or [onLoginCreated], whichever comes first. */
    private val target = CompletableDeferred<PasskeyTarget>()

    private var started = false

    /**
     * Parses [requestJson] and starts the flow. False means the request is unusable.
     *
     * The activity calls this again on every configuration change; only the first call does
     * anything.
     */
    fun setRequest(requestJson: String): Boolean {
        if (started) return true

        val information = passkeyManager.getPasskeyInformation(requestJson).getOrNull()
            ?: return false

        started = true
        _rp.update { information.rp }
        start(requestJson, information.excludeCredentials.toSet())

        return true
    }

    /**
     * The whole flow as one coroutine, so the order of the steps is the order of the lines and the
     * two waits on the user are plain suspension points.
     */
    private fun start(requestJson: String, excludeCredentials: Set<ByteArray>) {
        viewModelScope.launch {
            // The passkey table is not gated behind the session and credential ids are not secrets,
            // so an excluded request is answered without making the user authenticate for a
            // registration that can never succeed.
            if (passkeyRepository.doCredentialIdsExist(excludeCredentials))
                return@launch _excluded.update { true }

            requestUnlock()
            unlocked.await()

            // Before the item screen, so a failure aborts while there is nothing to leave behind.
            val response = passkeyManager.registerWithResult(requestJson)
                .orAbort("Failed to register passkey") ?: return@launch

            val passkey = response.toNewPasskey()
            _rp.update { response.rp }
            _pendingPasskey.update { passkey }
            _authState.update { SessionAuthState.Authenticated }

            when (val target = target.await()) {
                PasskeyTarget.NewLogin -> Unit
                is PasskeyTarget.ExistingLogin -> createNewOrUpdateLogin(
                    UpsertLogin.update(itemId = target.itemId, addedPasskeys = setOf(passkey))
                ).orAbort("Failed to store passkey") ?: return@launch
            }

            _event.send(CreatePasskeyEvent.Finish(response.response))
        }
    }

    private suspend fun requestUnlock() {
        val biometricUsable = biometricAvailabilityRepository.availability()
                && accountRepository.getOrNull()?.biometricWrappedArk != null

        if (biometricUsable) {
            _authState.update { SessionAuthState.TryBiometric }
            biometricChannel.send(Unit)
        } else _authState.update { SessionAuthState.NeedsPassword }
    }

    private fun RegistrationResponse.toNewPasskey() = NewPasskey(
        credentialId = credentialId,
        rp = rp,
        user = PasskeyUser(name = userName, displayName = userDisplayName),
        privateKey = privateKey,
    )

    fun onUnlocked() {
        unlocked.complete(Unit)
    }

    fun onUnlockFailed(error: UnlockError) {
        when (mapUnlockError(error)) {
            UnlockOutcome.Abort -> viewModelScope.launch { abort("biometric $error") }
            UnlockOutcome.NeedsPassword -> _authState.update { SessionAuthState.NeedsPassword }
        }
    }

    /**
     * Names the existing login the passkey belongs to. Only the first call counts: a second tap
     * landing before the dialog recomposes away would otherwise store the credential twice.
     */
    fun associatePasskeyAndFinish(itemId: ItemId) {
        target.complete(PasskeyTarget.ExistingLogin(itemId))
    }

    /** The login screen already saved [pendingPasskey] together with the new login. */
    fun onLoginCreated() {
        target.complete(PasskeyTarget.NewLogin)
    }

    fun onItemClicked(itemId: ItemId) {
        _event.trySend(
            CreatePasskeyEvent.OpenConfirmationDialog(
                itemId = itemId,
                itemName = "N/A",
                rp = _rp.value,
            )
        )
    }

    private suspend fun <S : Any, E> Result<S, E>.orAbort(reason: String): S? = fold(
        onSuccess = { it },
        onFailure = {
            abort("$reason: $it")
            null
        },
    )

    private suspend fun abort(msg: String) {
        Log.w(TAG, "Aborting: $msg")
        _event.send(CreatePasskeyEvent.Abort)
    }

    private sealed interface PasskeyTarget {
        data class ExistingLogin(val itemId: ItemId) : PasskeyTarget
        data object NewLogin : PasskeyTarget
    }

    companion object {
        private const val TAG = "CreatePasskeyViewModel"
    }
}
