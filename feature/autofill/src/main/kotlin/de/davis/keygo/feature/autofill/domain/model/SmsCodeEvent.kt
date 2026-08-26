package de.davis.keygo.feature.autofill.domain.model

sealed interface SmsCodeEvent {
    data class SmsCodeReceived(val code: String) : SmsCodeEvent
    data class Failed(val cause: Throwable) : SmsCodeEvent

    data object Timeout : SmsCodeEvent
}
