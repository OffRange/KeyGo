package de.davis.keygo.rust.totp

import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpServiceInterface
import de.davisalessandro.keygo.rust.algorithmFromString

typealias TotpService = TotpServiceInterface

fun Algorithm.Companion.fromString(value: String) = algorithmFromString(value)
