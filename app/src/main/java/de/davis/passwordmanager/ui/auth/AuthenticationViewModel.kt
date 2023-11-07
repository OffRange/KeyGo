package de.davis.passwordmanager.ui.auth

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.passwordmanager.R
import de.davis.passwordmanager.security.Cryptography
import de.davis.passwordmanager.security.mainpassword.MainPassword
import de.davis.passwordmanager.security.mainpassword.MainPasswordManager
import kotlinx.coroutines.launch

class AuthenticationViewModel : ViewModel() {

    private val _authenticationResult = MutableLiveData<AuthenticationResult>()
    val authenticationResult: MutableLiveData<AuthenticationResult> = _authenticationResult

    fun authenticate(password: String) {
        viewModelScope.launch {
            val equal = MainPasswordManager.getPassword().checkPassword(password)
            _authenticationResult.value =
                if (equal) AuthenticationResult.Success else AuthenticationResult.Error(
                    AuthenticationState(
                        passwordError = R.string.password_incorrect
                    )
                )
        }
    }

    fun registerNewMainPassword(password: String, newPassword: String, confirmedPassword: String) {
        viewModelScope.launch {
            val mainPassword = MainPasswordManager.getPassword()
            val isCorrectPassword = mainPassword.checkPassword(password)
            val isNewEqualsConfirmed = newPassword == confirmedPassword

            if (newPassword.isBlank()) {
                // We do not need to check if the confirmed password is also blank since this case
                // is covered if the new and the confirmed password are the same

                _authenticationResult.value = AuthenticationResult.Error(
                    AuthenticationState(newPasswordError = R.string.is_not_filled_in)
                )
                return@launch
            }

            if ((mainPassword == MainPassword.EMPTY || isCorrectPassword) && isNewEqualsConfirmed) {
                MainPasswordManager.updatePassword(MainPassword(Cryptography.bcrypt(newPassword)))
                _authenticationResult.value = AuthenticationResult.Success
                return@launch
            }

            _authenticationResult.value = AuthenticationResult.Error(
                AuthenticationState(
                    if (isCorrectPassword) null else R.string.password_incorrect,
                    confirmPasswordError = if (isNewEqualsConfirmed) null else R.string.password_does_not_match,
                )
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun MainPassword.checkPassword(password: String): Boolean {
        return Cryptography.checkBcryptHash(
            password,
            hexHash.hexToByteArray()
        )
    }
}