package de.davis.keygo.feature.settings.presentation.changepassword

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.usecase.ChangePasswordUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.crypto.FakeBiometricAvailabilityRepository
import de.davis.keygo.rust.FakeKeyDeriver
import de.davis.keygo.rust.FakeKeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val biometricAvailability = FakeBiometricAvailabilityRepository()
    private val keyDeriver = FakeKeyDeriver()
    private val keyWrapper = FakeKeyWrapper()
    private val estimator = object : PasswordStrengthEstimator {
        override suspend fun estimate(password: String): PasswordScore = PasswordScore.None
    }
    private val changePassword = ChangePasswordUseCase(accountRepository, keyDeriver, keyWrapper)

    private val accountId = UUID.randomUUID()
    private val ark = ByteArray(32) { (it + 1).toByte() }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val salt = keyDeriver.generateSalt()
        val kek = keyDeriver.deriveRootKekFromPassword("old", salt)
        val wrapped = keyWrapper.wrapAccountRootKey(kek, ark, accountId)
        accountRepository.seed(
            Account(
                id = accountId,
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(wrapped.ciphertext, wrapped.nonce, salt),
                biometricWrappedArk = null,
            )
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChangePasswordViewModel(
        accountRepository = accountRepository,
        biometricAvailabilityRepository = biometricAvailability,
        passwordStrengthEstimator = estimator,
        changePassword = changePassword,
    )

    @Test
    fun `blank new password sets Empty error and does not change password`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("old") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(FieldError.Empty, vm.state.value.newPasswordError)
    }

    @Test
    fun `mismatched confirmation sets Mismatch error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("old") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("different") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(FieldError.Mismatch, vm.state.value.confirmPasswordError)
    }

    @Test
    fun `wrong current password sets Incorrect error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("wrong") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(FieldError.Incorrect, vm.state.value.currentPasswordError)
    }

    @Test
    fun `valid password change emits Success`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("old") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(ChangePasswordEvent.Success, vm.event.first())
    }

    @Test
    fun `biometric submit with valid new passwords emits Success`() = runTest(dispatcher) {
        // Account must have a biometric-wrapped ARK for the use case to accept biometric proof.
        val current = accountRepository.getOrNull()!!
        accountRepository.seed(
            current.copy(
                biometricWrappedArk = de.davis.keygo.core.identity.domain.model.BiometricWrappedArk(
                    key = ByteArray(48) { it.toByte() },
                    keyIV = ByteArray(12) { it.toByte() },
                )
            )
        )
        val vm = viewModel()
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }

        vm.submitWithBiometric(ark.copyOf())
        advanceUntilIdle()

        assertEquals(ChangePasswordEvent.Success, vm.event.first())
    }
}
