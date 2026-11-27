package de.davis.keygo.feature.account.presentation.devices

private const val NO_BREAK_SPACE = ' '
private const val SEPARATOR = "$NO_BREAK_SPACE• "

internal fun detailLine(segments: List<String>): String =
    segments.joinToString(SEPARATOR) { it.replace(' ', NO_BREAK_SPACE) }
