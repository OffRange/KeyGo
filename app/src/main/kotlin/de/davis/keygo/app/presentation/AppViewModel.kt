package de.davis.keygo.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AppViewModel(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _hasAccess = MutableStateFlow<Boolean?>(null)
    val hasAccess = _hasAccess.asStateFlow()

    init {
        viewModelScope.launch {
            _hasAccess.update { accountRepository.getOrNull() != null }
        }
    }
}