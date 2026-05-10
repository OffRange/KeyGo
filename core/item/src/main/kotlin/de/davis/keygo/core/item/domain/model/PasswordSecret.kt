package de.davis.keygo.core.item.domain.model

data class PasswordSecret(
    override val payload: EncryptedPayload,
) : SecretField<String> {
    override val blueprint: SecretBlueprint<String, out SecretField<String>> = PasswordSecret

    companion object : SecretBlueprint<String, PasswordSecret>() {
        override val label: String = "password"
        override val codec: SecretCodec<String> = SecretCodec.StringCodec

        override fun createField(payload: EncryptedPayload): PasswordSecret =
            PasswordSecret(payload)
    }
}