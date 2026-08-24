package de.davis.keygo.feature.totp.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.security.domain.usecase.GetTdlMatchedLoginsUseCase
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.totp.domain.model.resolveTotpDomain
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import de.davisalessandro.keygo.rust.TotpInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

/**
 * Backs the screen that asks which login a scanned code belongs to.
 *
 * The code arrives as a uri rather than as parsed info because it travels through a navigation
 * argument, and navigation carries primitives. A code that cannot be read gets no suggestions and
 * no error, because the redirect that starts the import already refused those.
 */
@KoinViewModel
internal class SelectItemForTotpViewModel(
    @InjectedParam private val totpUri: String,
    private val totpService: TotpService,
    private val getTdlMatchedLogins: GetTdlMatchedLoginsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectItemForTotpUiState())
    val state: StateFlow<SelectItemForTotpUiState> = _state.asStateFlow()

    init {
        totpService.getInfoFromUriWithResult(totpUri).onFailure { failure ->
            Log.e(TAG, "Error parsing TOTP URI: $failure")
        }.onSuccess { info ->
            viewModelScope.launch {
                val suggested = suggestedItemIdsFor(info)
                _state.update { it.copy(suggestedItemIds = suggested) }
            }
        }
    }

    /**
     * The logins whose registrable domain matches the code's own.
     *
     * [resolveTotpDomain] is what fills the domain field for a new item, so the suggestions agree
     * with it: a code that carries no issuer still matches on the domain in `user@example.com`.
     */
    private suspend fun suggestedItemIdsFor(info: TotpInfo): Set<ItemId> {
        val domain = resolveTotpDomain(
            issuer = info.issuer,
            accountName = info.accountName,
        ) ?: return emptySet()

        return getTdlMatchedLogins(domain).mapTo(mutableSetOf()) { it.id }
    }

    companion object {
        private const val TAG = "SelectItemForTotpVM"
    }
}
