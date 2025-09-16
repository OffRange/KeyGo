package de.davis.keygo.automation.processor.model

import de.davis.keygo.automation.processor.exception.NotFoundException

@JvmInline
value class Options(private val options: Map<String, String>) {

    operator fun get(key: String) = options[key]

    fun getOrDefault(key: String, defaultValue: String) = options[key] ?: defaultValue

    fun require(key: String) = options[key] ?: throw NotFoundException("Option $key not found")

    companion object {
        const val KEY_AUTOMATION_ANDROID_NAMESPACE = "automation.android_namespace"
        const val KEY_AUTOMATION_ANDROID_NAMESPACE_SUFFIX = "automation.android_namespace_suffix"
    }
}