package de.davis.keygo.feature.account.presentation.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.core.sync.domain.model.ServerLink
import java.util.UUID

internal class ServerLinkProvider : PreviewParameterProvider<ServerLink> {

    override val values = sequenceOf(
        ServerLink.None,
        ServerLink.Connected(
            userId = UUID.randomUUID(),
            serverUrl = "https://vault.example.com/",
            username = "ada",
        ),
    )
}
