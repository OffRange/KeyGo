package de.davis.keygo.feature.account.presentation.setup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.IconBadge

@Composable
internal fun AccountSetupScaffold(
    title: String,
    description: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = AccountSetupScaffold(
    title = title,
    description = description?.let { AnnotatedString(it) },
    icon = icon,
    modifier = modifier,
    content = content,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountSetupScaffold(
    title: String,
    description: AnnotatedString?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        IconBadge(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
            shape = MaterialShapes.Cookie9Sided.toShape(),
            size = 100.dp,
            iconSize = 48.dp,
        )

        Text(
            text = title,
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.headlineLargeEmphasized,
        )

        Spacer(modifier = Modifier.height(8.dp))

        description?.let {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        content()
    }
}