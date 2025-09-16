package de.davis.keygo.processor.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RootVaultEntity(val name: String = "")

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class VaultEntity(val resString: String, val defaultIconType: String)