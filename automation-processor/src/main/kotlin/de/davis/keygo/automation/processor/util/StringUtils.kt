package de.davis.keygo.automation.processor.util

object StringUtils {

    fun String.camelToSnakeCase(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
            .lowercase()

    fun String.isCamelCase(): Boolean =
        matches(Regex("^[a-z][a-z0-9]*(?:[A-Z][a-z0-9]*)+$"))
}
