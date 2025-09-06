package de.davis.keygo.autofill.presentation.activity

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.davis.keygo.auth.presentation.authGraph
import de.davis.keygo.autofill.presentation.model.Request
import de.davis.keygo.autofill.presentation.model.SaveItemDestination
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.dashboard.presentation.dashboardGraph
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.item.core.presentation.model.DetailType
import de.davis.keygo.item.create.presentation.EditVaultItemScreen
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AutofillUi(
    request: Request<*>,
    onItemSelected: (ItemId) -> Unit,
    onSaved: () -> Unit
) {
    Scaffold { innerPadding ->
        val navController = rememberNavController()
        val listPaneNavigator = rememberListDetailPaneScaffoldNavigator<DetailType>()

        NavHost(
            navController = navController,
            startDestination = RouteDestination.Auth(),
            modifier = Modifier.Companion
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            authGraph(
                onSuccess = {
                    navController.navigate(request.destination) {
                        popUpTo<RouteDestination.Auth> { inclusive = true }
                    }
                }
            )

            dashboardGraph(
                listNavigator = listPaneNavigator,
                onItemClicked = onItemSelected,
                autoSelect = false
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
                    navigate = {
                        onSaved()
                    }
                )
            }
        }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    // important for sealed hierarchies:
    classDiscriminator = "type"
}

private inline fun <reified T> serializerNavType(
    serializer: KSerializer<T>
): NavType<T> = object : NavType<T>(isNullableAllowed = false) {
    override fun put(bundle: Bundle, key: String, value: T) {
        bundle.putString(key, json.encodeToString(serializer, value))
    }

    override fun get(bundle: Bundle, key: String): T {
        val s = requireNotNull(bundle.getString(key))
        return json.decodeFromString(serializer, s)
    }

    override fun parseValue(value: String): T =
        json.decodeFromString(serializer, Uri.decode(value))

    override fun serializeAsValue(value: T): String =
        Uri.encode(json.encodeToString(serializer, value))
}