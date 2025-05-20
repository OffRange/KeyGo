package de.davis.keygo.core.domain.annotation

@RequiresOptIn(message = "This API is intended for one-time usage only. E.g. collecting a channel.")
@Retention(AnnotationRetention.BINARY)
annotation class OneTimeUsage