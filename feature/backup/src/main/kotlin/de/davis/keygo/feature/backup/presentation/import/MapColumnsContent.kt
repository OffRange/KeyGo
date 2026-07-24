package de.davis.keygo.feature.backup.presentation.import

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.MappingConfidence
import de.davis.keygo.feature.backup.presentation.component.IconBadge
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.icon
import de.davis.keygo.feature.backup.presentation.import.model.ColumnMappingRow

@Composable
internal fun MapColumnsContent(
    columns: List<ColumnMappingRow>,
    duplicateTypes: Set<CsvColumnType>,
    fileName: String?,
    onTypeChange: (columnIndex: Int, type: CsvColumnType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(COLUMN_GAP),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item(key = "intro") {
            MapColumnsIntroCard(columnCount = columns.size, fileName = fileName)
        }

        items(columns, key = { it.index }) { column ->
            val selected = column.selectedType
            ColumnMappingGroup(
                column = column,
                isDuplicate = selected != null && selected in duplicateTypes,
                onTypeChange = { onTypeChange(column.index, it) },
            )
        }
    }
}

private val TYPE_OPTIONS: List<CsvColumnType?> = CsvColumnType.entries + null


/**
 * Gap between one column's group of segments and the next. Deliberately several times
 * [ListItemDefaults.SegmentedGap] (2.dp), which separates the segments *within* a group — that
 * ratio is what tells the reader where one column ends and the next begins.
 */
private val COLUMN_GAP = 8.dp

/**
 * Container behind every segment. Must not be left to [ListItemDefaults.segmentedColors]'s default,
 * which resolves to `colorScheme.surface` — identical to the wizard Scaffold's background in both
 * KeyGo themes, so the groups would have no visible edge at all. Every other `SegmentedListItem`
 * call site in the app pins this for the same reason.
 */
private val segmentContainerColor
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

/** How many segments make up one column's group; drives [ListItemDefaults.segmentedShapes]. */
private const val SEGMENTS_PER_COLUMN = 3

/**
 * Colours for a segment that exists only to display something.
 *
 * [SegmentedListItem] has no non-interactive overload — every overload carrying `ListItemShapes`
 * takes a click — so a display-only segment has to be a disabled one, and the disabled palette is
 * mapped back onto the enabled colours so it does not read as greyed out. Same workaround as
 * `BackupHubContent`.
 *
 * The cost is that TalkBack still announces these rows as *disabled*: the clickable node emits its
 * `OnClick` action regardless of `enabled` and merely marks itself unavailable, so the row leaves
 * the actionable set but keeps announcing as a control. It stays readable, and removing the
 * announcement entirely would mean hand-drawing the container this refactor exists to stop drawing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun displaySegmentColors(): ListItemColors {
    val base = ListItemDefaults.segmentedColors()
    return ListItemDefaults.segmentedColors(
        containerColor = segmentContainerColor,
        disabledContainerColor = segmentContainerColor,
        disabledContentColor = base.contentColor,
        disabledOverlineContentColor = base.overlineContentColor,
        disabledLeadingContentColor = base.leadingContentColor,
        disabledSupportingContentColor = base.supportingContentColor,
        disabledTrailingContentColor = base.trailingContentColor,
    )
}

/**
 * Explains what this step is for and which file it is about. Left-aligned rather than a centered
 * hero like `ReviewHeroCard`, since it scrolls above a list and must not consume the viewport.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MapColumnsIntroCard(columnCount: Int, fileName: String?) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer

    ListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        leadingContent = {
            IconBadge(
                icon = Icons.Default.TableChart,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
        supportingContent = {
            Text(
                text = if (fileName != null) pluralStringResource(
                    R.plurals.map_columns_intro_subtitle,
                    columnCount,
                    columnCount,
                    fileName,
                )
                else pluralStringResource(
                    R.plurals.map_columns_intro_subtitle_no_file,
                    columnCount,
                    columnCount,
                ),
                color = content.copy(alpha = 0.8f),
            )
        },
    ) {
        Text(
            text = stringResource(R.string.map_columns_intro_title),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * One CSV column, rendered as a group of three segments: where it came from, what it maps to, and
 * what is actually in it. Segmented shapes plus [ListItemDefaults.SegmentedGap] group them, which
 * is the same construction the export wizard's pick-one steps use.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnMappingGroup(
    column: ColumnMappingRow,
    isDuplicate: Boolean,
    onTypeChange: (CsvColumnType?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SourceSegment(index = column.index, header = column.header)

        TypeSegment(
            selectedType = column.selectedType,
            isDuplicate = isDuplicate,
            showVerify = column.needsVerification,
            onTypeChange = onTypeChange,
        )

        SamplesSegment(samples = column.samples)
    }
}

/**
 * The file side of the group: a neutral badge and the column's raw CSV header. Neutral colouring is
 * the signal that this text came from the user's file rather than being something they chose.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceSegment(index: Int, header: String) {
    SegmentedListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.segmentedShapes(0, SEGMENTS_PER_COLUMN),
        colors = displaySegmentColors(),
        leadingContent = {
            IconBadge(
                icon = Icons.Default.TableChart,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        overlineContent = {
            Text(text = stringResource(R.string.csv_column_source_label, index + 1))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = header, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The KeyGo side of the group: a primary-coloured badge and the chosen type, tappable to open the
 * type menu. The colour contrast against [SourceSegment] is what tells the two apart at a glance,
 * and this is the only segment of the three that is genuinely interactive.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TypeSegment(
    selectedType: CsvColumnType?,
    isDuplicate: Boolean,
    showVerify: Boolean,
    onTypeChange: (CsvColumnType?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val duplicateMessage = stringResource(R.string.csv_type_duplicate_error)
    val selectedName = selectedType.displayName

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        SegmentedListItem(
            onClick = { },
            shapes = ListItemDefaults.segmentedShapes(1, SEGMENTS_PER_COLUMN),
            colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
            leadingContent = {
                IconBadge(
                    icon = selectedType.icon,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            overlineContent = { Text(text = stringResource(R.string.csv_type_row_label)) },
            supportingContent = if (isDuplicate) {
                {
                    Text(
                        text = duplicateMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (showVerify) {
                {
                    Text(
                        text = stringResource(R.string.csv_type_verify_hint),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else null,
            trailingContent = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .typePickerSemantics(
                    isError = isDuplicate,
                    message = duplicateMessage,
                ),
        ) {
            Text(
                text = selectedName,
                color = if (isDuplicate) MaterialTheme.colorScheme.error
                else Color.Unspecified,
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MenuDefaults.groupStandardContainerColor,
            shape = MenuDefaults.standaloneGroupShape,
        ) {
            TYPE_OPTIONS.forEachIndexed { index, option ->
                DropdownMenuItem(
                    selected = selectedType == option,
                    onClick = {
                        onTypeChange(option)
                        expanded = false
                    },
                    text = { Text(text = option.displayName) },
                    leadingIcon = {
                        Icon(imageVector = option.icon, contentDescription = null)
                    },
                    selectedLeadingIcon = {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    },
                    shapes = MenuDefaults.itemShape(index, TYPE_OPTIONS.size)
                )
            }
        }
    }
}

/**
 * Restores what the old read-only `OutlinedTextField` announced for free, and which
 * [SegmentedListItem] does not: that this row is a picker, and whether its setting is invalid.
 *
 * - [Role.DropdownList] — the segment's `onClick` otherwise marks it a generic button.
 * - [error] — `SegmentedListItem` has no `isError`, so a duplicated type currently announces as
 *   valid even though the supporting text says otherwise.
 *
 * Deliberately does not set `stateDescription`: the headline `Text` already renders the selected
 * type, and `SegmentedListItem` merges its descendants into one semantics node, so adding it here
 * would make TalkBack announce the type name twice.
 */
private fun Modifier.typePickerSemantics(
    isError: Boolean,
    message: String,
) = semantics {
    role = Role.DropdownList
    if (isError) error(message)
}

/**
 * Real values read from the user's CSV, rendered monospaced so they read as file data rather than
 * as UI copy. The analyzer already caps how many it returns, so this renders all of them.
 *
 * No leading content, so the text sits at the list item's own start padding rather than aligning
 * past a badge it does not have.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SamplesSegment(samples: List<String>) {
    SegmentedListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.segmentedShapes(2, SEGMENTS_PER_COLUMN),
        colors = displaySegmentColors(),
        overlineContent = { Text(text = stringResource(R.string.csv_samples_label)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Toned down from the headline slot's default rather than shrunk: these are raw file
        // values, and they should not outrank the CSV header or the chosen type. onSurfaceVariant
        // carries that on its own, so the size stays at bodyMedium — monospace runs visually
        // smaller than the sans face at the same size, and these are the values the whole step
        // asks the user to read.
        if (samples.isEmpty()) Text(
            text = stringResource(R.string.csv_samples_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            samples.forEach { sample ->
                Text(
                    text = sample,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal val previewColumnRows = listOf(
    ColumnMappingRow(
        index = 0,
        header = "name",
        samples = listOf("Email", "Bank"),
        suggestedType = CsvColumnType.Title,
        confidence = MappingConfidence.High,
        selectedType = CsvColumnType.Title,
    ),
    ColumnMappingRow(
        index = 1,
        header = "login_uri",
        samples = listOf("https://example.com", "https://bank.example.com/login"),
        suggestedType = CsvColumnType.Url,
        confidence = MappingConfidence.High,
        selectedType = CsvColumnType.Url,
    ),
    ColumnMappingRow(
        index = 2,
        header = "field_a",
        // A full DISPLAY_SAMPLES-sized set: the samples segment has to stay legible at the
        // most values the analyzer can return, not just at the one or two shorter columns.
        samples = listOf(
            "s3cr3t",
            "hunter2",
            "correct-horse-battery-staple",
            "pa55w0rd!",
            "letmein"
        ),
        suggestedType = CsvColumnType.Password,
        confidence = MappingConfidence.Low,
        selectedType = CsvColumnType.Password,
    ),
    // Guessed Password, corrected by the user to Totp: the verify hint must NOT show here even
    // though the confidence is Medium, because the confidence describes a guess no longer in use.
    ColumnMappingRow(
        index = 3,
        header = "otp_seed",
        samples = listOf("otpauth://totp/Example:me@example.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Example"),
        suggestedType = CsvColumnType.Password,
        confidence = MappingConfidence.Medium,
        selectedType = CsvColumnType.Totp,
    ),
    // No suggestion at all, and the user has pointed it at a type column 1 already claims.
    ColumnMappingRow(
        index = 4,
        header = "extra_unmapped_legacy_export_column_name",
        samples = emptyList(),
        suggestedType = null,
        confidence = null,
        selectedType = CsvColumnType.Url,
    ),
)

/**
 * Covers the cases this screen exists to communicate: a settled high-confidence column, a
 * low-confidence one showing the verify hint, a corrected column whose hint is therefore
 * suppressed, an over-long sample value, a duplicate type in its error state, a column with no
 * sample values at all, and a header long enough to exercise the single-line ellipsis.
 */
@Preview(showBackground = true)
@Composable
private fun MapColumnsContentPreview() {
    MapColumnsContentPreviewContent()
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapColumnsContentPreviewDark() {
    MapColumnsContentPreviewContent()
}

@Composable
private fun MapColumnsContentPreviewContent() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MapColumnsContent(
                columns = previewColumnRows,
                duplicateTypes = setOf(CsvColumnType.Url),
                fileName = "passwords.csv",
                onTypeChange = { _, _ -> },
            )
        }
    }
}
