package de.davis.keygo.feature.account.presentation.setup.model

internal enum class ConnectServerStep {
    EnterDomain,

    //ConfirmServer,
    SignInOrOut,
    ChooseUsername,
    ConfirmPassword,
    SignIn,
}

private val signInSteps = listOf(
    ConnectServerStep.EnterDomain,
    ConnectServerStep.SignInOrOut,
    ConnectServerStep.SignIn,
)

private val createAccountSteps = listOf(
    ConnectServerStep.EnterDomain,
    ConnectServerStep.SignInOrOut,
    ConnectServerStep.ChooseUsername,
    ConnectServerStep.ConfirmPassword,
)

internal fun stepsFor(choice: AccountChoice?): List<ConnectServerStep> = when (choice) {
    AccountChoice.SignIn -> signInSteps
    AccountChoice.CreateAccount,
    null -> createAccountSteps
}
