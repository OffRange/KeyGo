package de.davis.keygo.core.sync.data

import androidx.datastore.core.DataStore
import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.sync.data.local.model.ProtoSessionStore
import de.davis.keygo.core.sync.data.local.model.protoSessionStore
import de.davis.keygo.core.sync.data.local.model.protoStoredSession
import de.davis.keygo.core.sync.di.annotation.SessionStoreQualifier
import de.davisalessandro.keygo.rust.StoredSession
import de.davisalessandro.keygo.rust.TokenStore
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single
internal class TokenStore(
    @SessionStoreQualifier
    private val dataStore: DataStore<ProtoSessionStore>
) : TokenStore {

    override suspend fun load(): StoredSession? {
        val store = dataStore.data.first()
        if (!store.hasSession()) return null

        val session = store.session
        return StoredSession(
            deviceId = Uuid.fromByteArray(session.deviceId.toByteArray()).toJavaUuid(),
            refreshToken = session.refreshToken,
            refreshExpiresAt = session.refreshTokenExpiresAt.toULong(),
        )
    }

    override suspend fun save(session: StoredSession) {
        // TODO: decide whether we encrypt the tokens. Theoretically an attacker cant use the tokens
        //  as the server verifies the DPoP-Proof which the attacker would need to calculate based
        //  on the device's signing key, which is not exportable.
        dataStore.updateData {
            protoSessionStore {
                this.session = protoStoredSession {
                    deviceId = session.deviceId.toKotlinUuid().toByteArray().toByteString()
                    refreshToken = session.refreshToken
                    refreshTokenExpiresAt = session.refreshExpiresAt.toLong()
                }
            }
        }
    }

    override suspend fun clear() {
        dataStore.updateData { it.toBuilder().clearSession().build() }
    }
}