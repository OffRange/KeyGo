package de.davis.keygo.core.item.domain.model

class Tag private constructor(val display: String) {

    val normalized: String = display.lowercase()

    override fun equals(other: Any?): Boolean =
        this === other || (other is Tag && other.normalized == normalized)

    override fun hashCode(): Int = normalized.hashCode()

    override fun toString(): String = display

    companion object {
        fun of(raw: String): Tag? = raw.trim().takeIf { it.isNotEmpty() }?.let(::Tag)

        fun normalize(raw: String): String = raw.trim().lowercase()
    }
}
