package de.davis.keygo.dashboard.domain.model

sealed interface Filter {

    sealed interface Direction {
        data object Ascending : Direction
        data object Descending : Direction
    }

    data class Alphanumerical(val direction: Direction = Direction.Ascending) : Filter
}