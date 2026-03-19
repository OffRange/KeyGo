package de.davis.keygo.core.ui

interface RouteDestination {

    val graphDest: RouteDestination
        get() = this
}