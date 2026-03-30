package de.davis.keygo.feature.autofill.presentation.activity

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.auth.presentation.authGraph
import de.davis.keygo.feature.autofill.presentation.model.SaveItemDestination
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.EditVaultItemScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.list_screen.presentation.itemListGraph
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutofillUi(
    navController: NavHostController,
    onItemSelected: (ItemId) -> Unit,
    onSaved: () -> Unit,
    abort: () -> Unit,
    onAuthenticationSucceeded: () -> Unit,
    showBiometricPromptIfPossible: Boolean
) {
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AuthRoute(showBiometricPromptIfPossible = showBiometricPromptIfPossible),
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            authGraph(
                onSuccess = {
                    onAuthenticationSucceeded()
                }
            )

            itemListGraph(
                onItemClick = onItemSelected,
                restrictedItemType = VaultItemType.Password,
                dockedSearchResults = false,
                enableDeletion = false,
                onCreateRequest = {},
                notFoundStrategy = NoItemStrategy.ShowMessage
            )

            composable<SaveItemDestination>(
                typeMap = mapOf(
                    typeOf<DetailPaneInformation.CreateRaw>() to serializerNavType(
                        DetailPaneInformation.CreateRaw.serializer()
                    )
                )
            ) { s ->
                val destination = s.toRoute<SaveItemDestination>()
                EditVaultItemScreen(
                    detailPaneInformation = destination.createRaw,
                    onCreated = { onSaved() },
                    navigateBack = { abort() }
                )
            }
        }
    }
}

private val JSON = Json {
    ignoreUnknownKeys = true
    // important for sealed hierarchies:
    classDiscriminator = "type"
}

private inline fun <reified T> serializerNavType(
    serializer: KSerializer<T>
): NavType<T> = object : NavType<T>(isNullableAllowed = false) {
    override fun put(bundle: Bundle, key: String, value: T) {
        bundle.putString(key, JSON.encodeToString(serializer, value))
    }

    override fun get(bundle: Bundle, key: String): T {
        val s = requireNotNull(bundle.getString(key))
        return JSON.decodeFromString(serializer, s)
    }

    override fun parseValue(value: String): T =
        JSON.decodeFromString(serializer, Uri.decode(value))

    override fun serializeAsValue(value: T): String =
        Uri.encode(JSON.encodeToString(serializer, value))
}