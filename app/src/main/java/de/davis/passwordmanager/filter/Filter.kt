package de.davis.passwordmanager.filter

import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.database.entities.details.password.Strength
import de.davis.passwordmanager.database.entities.onlyCustoms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object Filter {

    private val _filterFlow = MutableStateFlow(FilterOptions())
    val filterFlow = _filterFlow.asStateFlow()

    private val _tagsFlow = MutableStateFlow(listOf<String>())
    val tagsFlow = _tagsFlow.asStateFlow()

    fun updateFilter(block: FilterOptions.() -> Unit) {
        val current = filterFlow.value
        val filter = current
            .copy(tags = mutableListOf<String>().apply { addAll(current.tags) })
            .apply(block)
        _filterFlow.value = filter
    }

    fun updateTags(tags: List<String>) {
        _tagsFlow.value = tags
    }

    data class FilterOptions(
        var password: StrengthFilter? = StrengthFilter(),
        var creditCard: Boolean = true,
        var tags: MutableList<String> = mutableListOf() //empty = all
    )

    data class StrengthFilter(
        var veryStrong: Boolean = true,
        var strong: Boolean = true,
        var moderate: Boolean = true,
        var weak: Boolean = true,
        var ridiculous: Boolean = true
    )
}

fun Collection<SecureElement>.applyFilter(filter: Filter.FilterOptions): List<SecureElement> {
    return filter { if (it.elementType == ElementType.CREDIT_CARD) filter.creditCard else true }
        .filter { if (it.elementType == ElementType.PASSWORD) filter.password != null else true }
        .filter {
            if (it.elementType != ElementType.PASSWORD)
                return@filter true

            (it.detail as PasswordDetails).let { pwdDetail ->
                filter.password?.let { pwd ->
                    pwd.veryStrong && pwdDetail.strength == Strength.VERY_STRONG
                            || pwd.strong && pwdDetail.strength == Strength.STRONG
                            || pwd.moderate && pwdDetail.strength == Strength.MODERATE
                            || pwd.weak && pwdDetail.strength == Strength.WEAK
                            || pwd.ridiculous && pwdDetail.strength == Strength.RIDICULOUS
                } ?: true
            }
        }
        .filter {
            if (filter.tags.isEmpty()) true else filter.tags.any { tag ->
                it.tags.onlyCustoms().map { it.name }.contains(tag)
            }
        }
}