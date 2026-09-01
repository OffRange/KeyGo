package de.davis.keygo.app.presentation.navigation

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import de.davis.keygo.R
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.dashboard.presentation.dashboardEntries
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.auth.presentation.authEntries
import de.davis.keygo.feature.backup.presentation.BackupHubRoute
import de.davis.keygo.feature.backup.presentation.backupEntries
import de.davis.keygo.feature.item.create.presentation.totp.AssignTotpRoute
import de.davis.keygo.feature.item.create.presentation.totp.assignTotpEntries
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.onboarding.presentation.onboardingEntries
import de.davis.keygo.feature.settings.presentation.ChangePasswordRoute
import de.davis.keygo.feature.settings.presentation.settingsEntries
import de.davis.keygo.feature.totp.presentation.SelectItemForTotpRoute
import de.davis.keygo.feature.totp.presentation.selectItemForTotpEntries
import de.davis.keygo.feature.totp.presentation.totpImportRedirectEntries
import de.davis.keygo.item.dialog.SelectItemContent

private const val TAG = "KeyGoEntryProvider"

@Composable
fun keyGoEntryProvider(navigator: AppNavigator, hasAccess: Boolean): (NavKey) -> NavEntry<NavKey> {
    val activity = LocalActivity.current

    return entryProvider {
        totpImportRedirectEntries(
            metadata = WindowOwning,
            onValidated = { pending -> navigator.openGateFor(hasAccess, pending) },
            // The app was launched only to import this code, so the Activity is what closes.
            onRejected = {
                if (activity != null) activity.finish()
                else Log.w(TAG, "No activity to finish after rejecting an invalid TOTP deep link")
            },
        )

        selectItemForTotpEntries(
            metadata = WindowOwning,
            onItemSelected = { totpUri, itemId ->
                navigator.navigate(AssignTotpRoute(totpUri, itemId.toString()))
            },
            onCreateNew = { totpUri -> navigator.navigate(AssignTotpRoute(totpUri)) },
        )

        assignTotpEntries(
            metadata = WindowOwning,
            onImportFinished = { navigator.finishLaunchFlow() },
            navigateUp = { navigator.goBack() },
        )

        authEntries(
            metadata = WindowOwning,
            onSuccess = { totpUri -> navigator.finishUnlock(totpUri) },
        )

        onboardingEntries(
            metadata = WindowOwning,
            onSuccess = { totpUri -> navigator.finishUnlock(totpUri) },
        )

        dashboardEntries(navigator = navigator)

        entry<RouteDestination.SelectItemType>(
            // The sheet sits over the dashboard, which keeps its shell while the sheet is open.
            metadata = DialogSceneStrategy.dialog() + appShell(ShellVisibility.Always),
        ) {
            SelectItemContent(
                onSelect = { type ->
                    navigator.goBack()
                    navigator.showDetail(RouteDestination.CreateItem(type))
                },
            )
        }

        settingsEntries(
            metadata = NavigationOnly,
            onOpenChangePassword = { navigator.navigate(ChangePasswordRoute) },
            onShowLibraries = { navigator.navigate(RouteDestination.Libraries) },
            onOpenBackup = { navigator.navigate(BackupHubRoute) },
            onUp = { navigator.goBack() },
        )

        backupEntries(
            metadata = WindowOwning,
            navigateToDestination = { navigator.navigate(it) },
            navigateUp = { navigator.goBack() },
        )

        entry<RouteDestination.Connectivity>(metadata = NavigationOnly) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.coming_soon),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }

        entry<RouteDestination.Libraries>(metadata = WindowOwning) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                val libs by produceLibraries()
                LibrariesContainer(
                    libraries = libs,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

/** Replaces the launch flow, so back from the gate leaves the app rather than a consumed link. */
internal fun AppNavigator.openGateFor(hasAccess: Boolean, pending: PendingTotpImport) {
    replaceLaunchFlow(
        if (hasAccess) AuthRoute(totpInfo = pending.totpInfo, queries = pending.queries)
        else OnboardingRoute(totpInfo = pending.totpInfo, queries = pending.queries),
    )
}

private fun AppNavigator.finishUnlock(totpUri: String?) {
    if (totpUri == null) finishLaunchFlow()
    else replaceLaunchFlow(SelectItemForTotpRoute(totpUri))
}
