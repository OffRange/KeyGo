package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.ArkCredential
import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.NoHandle

class FakeArkCredential(val session: ArkSession) : ArkCredential(NoHandle)
