package de.davis.keygo.processor.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Route(val name: String = "", val parent: String = "")