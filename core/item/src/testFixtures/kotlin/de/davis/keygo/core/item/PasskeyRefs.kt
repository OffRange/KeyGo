package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.model.PasskeyRef

/**
 * A [PasskeyRef] for [rp] with a credential id derived from [discriminator].
 *
 * Two calls with the same [rp] and different discriminators produce the two distinct credentials a
 * login can hold for one site, which is the case that distinguishes deleting by credential id from
 * deleting by relying party.
 */
fun passkeyRef(rp: String, discriminator: String = ""): PasskeyRef = PasskeyRef(
    credentialId = "$rp/$discriminator".toByteArray(),
    rp = rp,
)
