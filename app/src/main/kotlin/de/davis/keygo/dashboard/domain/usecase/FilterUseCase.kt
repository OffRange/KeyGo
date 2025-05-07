package de.davis.keygo.dashboard.domain.usecase

import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.dashboard.domain.model.Filter

class FilterUseCase {

    operator fun <I : VaultItem> invoke(filter: Filter, vaultItems: List<I>): List<I> {
        return when (filter) {
            is Filter.Alphanumerical -> {
                when (filter.direction) {
                    is Filter.Direction.Ascending -> vaultItems.sortedWith(compareByAlphanumeric { it.name })
                    is Filter.Direction.Descending -> vaultItems.sortedWith(
                        compareByDescendingAlphanumeric { it.name }
                    )
                }
            }
        }
    }

    private fun <T> compareByAlphanumeric(selector: (T) -> String): Comparator<T> =
        Comparator { a, b ->
            alphanumericCompare(selector(a), selector(b))
        }

    private fun <T> compareByDescendingAlphanumeric(selector: (T) -> String): Comparator<T> =
        Comparator { a, b ->
            alphanumericCompare(selector(b), selector(a))
        }

    private fun alphanumericCompare(s1: String, s2: String): Int {
        val parts1 = s1.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val parts2 = s2.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))

        val len = minOf(parts1.size, parts2.size)
        for (i in 0 until len) {
            val p1 = parts1[i]
            val p2 = parts2[i]

            val cmp = when {
                p1.all(Char::isDigit) && p2.all(Char::isDigit) -> {
                    p1.toBigInteger().compareTo(p2.toBigInteger())
                }

                else -> p1.compareTo(p2, ignoreCase = true)
            }

            if (cmp != 0) return cmp
        }

        return parts1.size.compareTo(parts2.size)
    }
}