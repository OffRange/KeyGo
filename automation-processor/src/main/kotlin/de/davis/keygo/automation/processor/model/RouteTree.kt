package de.davis.keygo.automation.processor.model


sealed interface RouteTree {

    data class Root(
        val name: String,
        val routes: Set<RouteTree>
    ) : RouteTree

    data class Endpoint(
        val name: String
    ) : RouteTree
}
