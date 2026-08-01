package de.davis.keygo.core.item.domain.model

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// "yy" parses into the 2000-2099 range, which is correct for card expirations.
private val EXPIRATION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")

fun String.toYearMonthOrNull(): YearMonth? = try {
    YearMonth.parse(this, EXPIRATION_FORMATTER)
} catch (_: DateTimeParseException) {
    null
}
