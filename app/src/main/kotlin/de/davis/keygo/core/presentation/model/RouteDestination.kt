package de.davis.keygo.core.presentation.model

import kotlinx.serialization.Serializable

sealed interface RouteDestination {

    val graphDest: RouteDestination
        get() = this

    @Serializable
    data object TopLevelAppGraph : RouteDestination

    sealed interface Home : RouteDestination {

        override val graphDest: RouteDestination
            get() = NavGraph

        @Serializable
        data object NavGraph : Home

        @Serializable
        data class Root(val totpUri: String? = null) : Home

        @Serializable
        data object SelectItem : Home
    }

    @Serializable
    data object Connectivity : RouteDestination {
        override val graphDest: RouteDestination
            get() = Connectivity
    }

    @Serializable
    data object Settings : RouteDestination {
        override val graphDest: RouteDestination
            get() = Settings
    }

    @Serializable
    data class Auth(val totpInfo: String? = null, val queries: String? = null) : RouteDestination {
        val uri
            get() = if (!totpInfo.isNullOrBlank() && !queries.isNullOrBlank())
                "otpauth://totp/$totpInfo?$queries"
            else null
    }
}