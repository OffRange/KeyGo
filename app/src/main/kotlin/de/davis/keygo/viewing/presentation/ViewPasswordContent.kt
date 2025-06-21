package de.davis.keygo.viewing.presentation

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.core.presentation.LocalIsInSinglePaneMode
import de.davis.keygo.core.presentation.component.KeyGoCard
import de.davis.keygo.core.presentation.component.StrengthIndicator
import de.davis.keygo.viewing.presentation.model.ObfuscatedString
import de.davis.keygo.viewing.presentation.model.ViewPasswordState
import de.davis.keygo.viewing.presentation.model.ViewPasswordUiEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewPasswordContent(state: ViewPasswordState, onEvent: (ViewPasswordUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = state.name)
                },
                subtitle = {
                    Text(text = stringResource(R.string.password))
                },
                navigationIcon = {
                    if (LocalIsInSinglePaneMode.current) {
                        IconButton(onClick = { onEvent(ViewPasswordUiEvent.OnBackClick) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_content_description)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val name = stringResource(R.string.name)
        val password = stringResource(R.string.password)
        val username = stringResource(R.string.username)
        val website = stringResource(R.string.website)
        val note = stringResource(R.string.note)

        var isPasswordHidden by rememberSaveable { mutableStateOf(true) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .consumeWindowInsets(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entry(
                title = name,
                leadingIcon = Icons.Default.Badge
            ) {
                Text(text = state.name)
            }
            entry(
                title = password,
                leadingIcon = Icons.Default.Password,
                modifier = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isPasswordHidden = false

                        val pointerId = down.id
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || change.changedToUpIgnoreConsumed()) {
                                break
                            }

                            change.consume()
                        } while (true)

                        isPasswordHidden = true
                    }
                },
                trailingIcon = {
                    val clipboard = LocalClipboard.current
                    val scope = rememberCoroutineScope()
                    IconButton(
                        onClick = {
                            val clipData =
                                ClipData.newPlainText(state.password.raw, state.password.raw)
                                    .apply {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                            description.extras = PersistableBundle().apply {
                                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                            }
                                    }
                            scope.launch {
                                clipboard.setClipEntry(clipData.toClipEntry())
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_password_content_description)
                        )
                    }
                }
            ) {
                val scrollState = rememberScrollState()
                Text(
                    text = if (isPasswordHidden) state.password.hidden else state.password.raw,
                    maxLines = 1,
                    modifier = Modifier.horizontalScroll(scrollState)
                )
                StrengthIndicator(
                    score = state.passwordStrengthScore,
                    forceCompact = true
                )
            }

            entry(
                title = username,
                leadingIcon = Icons.Default.Person
            ) {
                Text(text = state.username)
            }

            entry(
                title = website,
                leadingIcon = Icons.Default.Link,
                trailingIcon = if (state.canOpenWebsite) {
                    {
                        IconButton(onClick = { onEvent(ViewPasswordUiEvent.OpenWebsite) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.OpenInNew,
                                contentDescription = stringResource(R.string.open_website_content_description)
                            )
                        }
                    }
                } else null
            ) {
                Text(text = state.website)
            }

            entry(
                title = note,
                leadingIcon = Icons.AutoMirrored.Default.Notes,
            ) {
                Text(text = state.note)
            }
        }
    }
}

private fun LazyListScope.entry(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        KeyGoCard(
            title = {
                Text(text = title)
            },
            leadingItem = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                )
            },
            trailingItem = trailingIcon,
            modifier = modifier
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun ViewPasswordContentPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides true
        ) {
            ViewPasswordContent(
                state = ViewPasswordState(
                    name = "Password 1",
                    password = ObfuscatedString("Password"),
                    passwordStrengthScore = Score.Ridiculous,
                    username = "Username 1",
                    website = "example.com",
                    note = "Note about the password or any additional information that might be useful.",
                    canOpenWebsite = true,
                ),
                onEvent = {}
            )
        }
    }
}