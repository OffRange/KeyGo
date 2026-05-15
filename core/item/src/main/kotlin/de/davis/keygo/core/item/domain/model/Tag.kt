package de.davis.keygo.core.item.domain.model

typealias Tag = String

fun Tag.normalize() = trim().lowercase()