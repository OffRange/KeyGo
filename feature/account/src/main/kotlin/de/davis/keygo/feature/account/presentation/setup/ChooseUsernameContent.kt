package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.components.AccountSetupScaffold
import de.davis.keygo.feature.account.presentation.setup.model.ChooseUsernameState
import de.davis.keygo.feature.account.presentation.setup.model.UsernameCheck
import de.davis.keygo.feature.account.presentation.setup.transformation.UsernameInputTransformation
import de.davis.keygo.rust.username.UsernameError

@Composable
internal fun ChooseUsernameContent(
    state: ChooseUsernameState,
    usernameInputTransformation: InputTransformation,
) {
    val usernameError = (state.usernameCheck as? UsernameCheck.Invalid)?.error

    AccountSetupScaffold(
        title = stringResource(R.string.choose_username_title),
        description = descriptionWithHost(host = state.serverHost),
        icon = Icons.Default.Person,
    ) {
        OutlinedTextField(
            state = state.usernameTextFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.NewUsername },
            label = {
                Text(text = stringResource(R.string.username))
            },
            supportingText = {
                if (usernameError != null) Text(text = usernameError.message)
                else Text(text = allowedCharsHint(chars = state.allowedUsernameChars))
            },
            isError = usernameError != null,
            inputTransformation = usernameInputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = if (state.serverInviteRequired) ImeAction.Next else ImeAction.Done,
            ),
        )

        if (state.serverInviteRequired) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = state.inviteTextFieldState,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = stringResource(R.string.invite_code))
                },
                placeholder = {
                    Text(text = stringResource(R.string.invite_code_placeholder))
                },
                supportingText = {
                    Text(
                        text = stringResource(
                            if (state.inviteEditable) R.string.invite_code_helper
                            else R.string.invite_code_from_link_helper,
                        ),
                    )
                },
                readOnly = !state.inviteEditable,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                ),
                inputTransformation = InviteCodeTransformation
                    .then(InputTransformation.maxLength(INVITE_CODE_LENGTH)),
                outputTransformation = InviteCodeGrouping,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}

@Composable
private fun descriptionWithHost(host: String): AnnotatedString = templateWithStyledArgument(
    // A word joiner between every character stops the line breaker from splitting the host at
    // `-`, `.` or `:`; a host wider than the line still breaks as a last resort.
    template = stringResource(R.string.choose_username_description),
    argument = host.toList().joinToString(separator = WORD_JOINER),
    style = SpanStyle(color = MaterialTheme.colorScheme.primary),
)

@Composable
private fun allowedCharsHint(chars: List<String>): AnnotatedString = templateWithStyledArgument(
    template = stringResource(R.string.choose_username_helper),
    argument = chars.joinToString(separator = ""),
    style = SpanStyle(fontFamily = FontFamily.Monospace),
)

/**
 * Fills the `%1$s` of [template] with [argument] in [style]. Expects the resource read without
 * arguments, which keeps its placeholder in place.
 */
@Composable
private fun templateWithStyledArgument(
    template: String,
    argument: String,
    style: SpanStyle,
): AnnotatedString = remember(template, argument, style) {
    buildAnnotatedString {
        append(template.substringBefore(PLACEHOLDER))
        withStyle(style) { append(argument) }
        append(template.substringAfter(PLACEHOLDER, missingDelimiterValue = ""))
    }
}

private const val PLACEHOLDER = $$"%1$s"
private const val WORD_JOINER = "\u2060"

private const val INVITE_CODE_LENGTH = 12
private const val INVITE_CODE_GROUP = 4

/**
 * Crockford Base32 input: uppercases, maps the look-alikes `O→0` and `I`/`L→1` so a code read
 * aloud or retyped from paper still matches, and drops everything else (spaces, hyphens, `U`).
 */
private object InviteCodeTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        // Go backwards so deletes don't shift the positions still to come
        for (i in length - 1 downTo 0) {
            val c = charAt(i).uppercaseChar()
            val mapped = when (c) {
                'O' -> '0'
                'I', 'L' -> '1'
                'U' -> null
                in 'A'..'Z', in '0'..'9' -> c
                else -> null
            }
            if (mapped != charAt(i))
                replace(i, i + 1, mapped?.toString().orEmpty())
        }
    }
}

private val InviteCodeGrouping = OutputTransformation {
    for (index in (INVITE_CODE_GROUP until length step INVITE_CODE_GROUP).reversed())
        insert(index, "-")
}

private val UsernameError.message: String
    @Composable
    get() = when (this) {
        is UsernameError.TooShort -> stringResource(R.string.username_too_short, minLength)

        is UsernameError.TooLong -> stringResource(R.string.username_too_long, maxLength)

        is UsernameError.InvalidCharacter -> stringResource(
            R.string.username_invalid_character,
            character
        )

        UsernameError.InvalidStart -> stringResource(R.string.username_invalid_start)
        UsernameError.InvalidEnd -> stringResource(R.string.username_invalid_end)
        UsernameError.ConsecutiveSeparators -> stringResource(R.string.username_consecutive_separators)

        UsernameError.OnlyDigits -> stringResource(R.string.username_only_digits)
        UsernameError.Reserved -> stringResource(R.string.username_reserved)
    }

private class ChooseUsernameStateProvider : PreviewParameterProvider<ChooseUsernameState> {

    override val values = sequenceOf(
        ChooseUsernameState(
            usernameTextFieldState = TextFieldState("ada"),
            inviteTextFieldState = TextFieldState(),
            serverHost = "vault.example.org",
            serverInviteRequired = true,
            inviteEditable = true,
            allowedUsernameChars = PreviewAllowedChars,
        ),
        ChooseUsernameState(
            usernameTextFieldState = TextFieldState("ada"),
            inviteTextFieldState = TextFieldState("K7QM2XRA9F3T"),
            serverHost = "vault.example.org",
            serverInviteRequired = true,
            inviteEditable = false,
            allowedUsernameChars = PreviewAllowedChars,
        ),
        ChooseUsernameState(
            usernameTextFieldState = TextFieldState(),
            inviteTextFieldState = TextFieldState(),
            serverHost = "vault.example.org",
            serverInviteRequired = false,
            inviteEditable = true,
            allowedUsernameChars = PreviewAllowedChars,
        ),
        ChooseUsernameState(
            usernameTextFieldState = TextFieldState("ad"),
            inviteTextFieldState = TextFieldState(),
            serverHost = "vault.example.org",
            serverInviteRequired = false,
            inviteEditable = true,
            allowedUsernameChars = PreviewAllowedChars,
            usernameCheck = UsernameCheck.Invalid(UsernameError.TooShort(3)),
        ),
    )

    companion object {
        private val PreviewAllowedChars = listOf("a-z", "0-9", ".", "_", "-")
    }
}

@Preview
@Composable
private fun ChooseUsernameContentPreview(
    @PreviewParameter(ChooseUsernameStateProvider::class) state: ChooseUsernameState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChooseUsernameContent(
                state = state,
                usernameInputTransformation = UsernameInputTransformation(String::lowercase),
            )
        }
    }
}
