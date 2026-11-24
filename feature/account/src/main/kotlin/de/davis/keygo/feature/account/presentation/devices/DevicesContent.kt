package de.davis.keygo.feature.account.presentation.devices

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.core.ui.components.settings.SettingsListDefaults
import de.davis.keygo.core.ui.components.settings.SettingsSectionHeader
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.devices.model.DeviceActivity
import de.davis.keygo.feature.account.presentation.devices.model.DeviceItem
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiEvent
import de.davis.keygo.feature.account.presentation.preview.DevicesStateProvider

@Composable
internal fun DevicesContent(
    state: DevicesState,
    onEvent: (DevicesUiEvent) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.your_devices))
                },
                navigationIcon = {
                    IconButton(
                        onClick = navigateUp,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // Loading and Failed don't scroll, so they sit inside the bars; the list scrolls under them.
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (state) {
            DevicesState.Loading -> Loading(modifier = modifier)
            DevicesState.Failed -> LoadFailed(
                onRetry = { onEvent(DevicesUiEvent.OnRetryClicked) },
                modifier = modifier,
            )

            is DevicesState.Loaded -> DeviceList(
                state = state,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DeviceList(
    state: DevicesState.Loaded,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 8.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        deviceSection(
            key = "current",
            title = R.string.devices_this_device,
            items = listOfNotNull(state.current),
        )
        deviceSection(
            key = "others",
            title = R.string.devices_other_devices,
            items = state.others,
        )
    }
}

private fun LazyListScope.deviceSection(
    key: String,
    @StringRes title: Int,
    items: List<DeviceItem>,
) {
    if (items.isEmpty()) return

    item(key = "header_$key") {
        SettingsSectionHeader(
            title = title,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .animateItem(),
        )
    }

    itemsIndexed(
        items = items,
        key = { _, item -> item.device.id },
    ) { index, item ->
        DeviceRow(
            item = item,
            shapes = SettingsListDefaults.shapes(index, items.size),
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun DeviceRow(
    item: DeviceItem,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (device, activity) = item

    SegmentedListItem(
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(
            containerColor = colorScheme.surfaceContainerHigh,
            contentColor = if (activity == DeviceActivity.SignedOut) colorScheme.onSurfaceVariant
            else colorScheme.onSurface,
        ),
        supportingContent = {
            Text(
                text = detailLine(
                    listOfNotNull(activity.label(), device.platform, device.appVersion),
                ),
            )
        },
        leadingContent = {
            DeviceBadge(activity = activity)
        },
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(text = device.name)
    }
}

@Composable
private fun DeviceBadge(activity: DeviceActivity) {
    val colorScheme = MaterialTheme.colorScheme

    if (activity == DeviceActivity.SignedOut) IconBadge(
        icon = Icons.Default.MobileOff,
        containerColor = colorScheme.surfaceContainerHighest,
        contentColor = colorScheme.onSurfaceVariant,
    )
    else {
        val containerColor =
            if (activity == DeviceActivity.ThisDevice) colorScheme.primary
            else colorScheme.primaryContainer
        IconBadge(
            icon = Icons.Default.Smartphone,
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor),
        )
    }
}

@Composable
private fun DeviceActivity.label(): String? = when (this) {
    DeviceActivity.ThisDevice -> null
    DeviceActivity.ActiveNow -> stringResource(R.string.device_active_now)
    is DeviceActivity.ActiveAt -> stringResource(
        R.string.device_active_ago,
        DateUtils.getRelativeTimeSpanString(
            lastSeenAt.toEpochMilliseconds(),
            now.toEpochMilliseconds(),
            DateUtils.MINUTE_IN_MILLIS,
        ),
    )

    DeviceActivity.SignedOut -> stringResource(R.string.device_signed_out)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun LoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.devices_load_failed),
            style = MaterialTheme.typography.bodyLarge,
        )
        FilledTonalButton(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Preview
@Composable
private fun DevicesContentPreview(
    @PreviewParameter(DevicesStateProvider::class) state: DevicesState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DevicesContent(
                state = state,
                onEvent = {},
                navigateUp = {},
            )
        }
    }
}
