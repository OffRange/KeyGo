package de.davis.keygo.core.security

import de.davis.keygo.core.security.domain.ExportArk
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.ArkCredential
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.NoHandle
import de.davisalessandro.keygo.rust.PasswordWrapped
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

class FakeSession(startUnlocked: Boolean = false) : Session {

    var failDerivation: Boolean = false
    var failUnlock: Boolean = false

    var unwrapVaultKeyFailure: SessionError? = null

    var handedOver: ByteArray? = null
        private set

    val exported: MutableList<ByteArray> = mutableListOf()

    fun onlyExported(): ByteArray = exported.singleOrNull()
        ?: error("expected exactly one exportArk call, got ${exported.size}")

    private val random = Random(SEED)

    private var ark: ByteArray? = if (startUnlocked) randomBytes(32) else null

    private val _isActive = MutableStateFlow(ark != null)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    override suspend fun createAccount(password: String): Result<NewAccount, SessionError> {
        if (failDerivation) return Result.Failure(SessionError.Derivation("forced"))

        val ark = randomBytes(32)
        val vaultKey = randomBytes(32)
        val userId = randomUUID()
        val vaultId = randomUUID()
        val salt = randomBytes(16)

        this.ark = ark
        _isActive.value = true

        return Result.Success(
            NewAccount(
                userId = userId,
                salt = salt,
                passwordWrappedArk = wrap(kek(password, salt), ark, userId),
                vaultId = vaultId,
                wrappedVaultKey = wrap(ark, vaultKey, vaultId),
            ),
        )
    }

    override suspend fun unlockWithPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): Result<Unit, SessionError> {
        if (failDerivation) return Result.Failure(SessionError.Derivation("forced"))

        val recovered = unwrap(kek(password, salt), wrapped, userId)
            ?: return Result.Failure(SessionError.KeyWrap(KeyWrapException.UnwrapFailed()))

        ark = recovered
        _isActive.value = true
        return Result.Success(Unit)
    }

    override suspend fun unlockWithArk(arkBytes: ByteArray): Result<Unit, SessionError> {
        handedOver = arkBytes
        if (failUnlock) return Result.Failure(SessionError.Locked)
        if (arkBytes.size != 32) {
            val cause = KeyWrapException.InvalidKeyLength(32uL, arkBytes.size.toULong())
            return Result.Failure(SessionError.KeyWrap(cause))
        }

        ark = arkBytes.copyOf()
        _isActive.value = true
        return Result.Success(Unit)
    }

    @ExportArk
    override fun exportArk(): Result<ByteArray, SessionError> {
        val active = ark ?: return Result.Failure(SessionError.Locked)
        return Result.Success(active.copyOf().also { exported += it })
    }

    override fun arkCredential(): ArkCredential = FakeArkCredential(this)

    override suspend fun verifyPassword(
        password: String,
        salt: ByteArray,
        wrapped: WrappedKeyBlob,
        userId: UUID,
    ): Result<Unit, SessionError> {
        val active = ark ?: return Result.Failure(SessionError.Locked)
        if (failDerivation) return Result.Failure(SessionError.Derivation("forced"))

        // Like Rust: the blob has to open to the ARK this session holds, not merely open.
        val stored = unwrap(kek(password, salt), wrapped, userId)
        return if (stored?.contentEquals(active) == true) Result.Success(Unit)
        else Result.Failure(SessionError.WrongPassword)
    }

    override fun verifyArk(arkBytes: ByteArray): Result<Boolean, SessionError> {
        val active = ark ?: return Result.Failure(SessionError.Locked)
        return Result.Success(active.contentEquals(arkBytes))
    }

    override suspend fun rewrapForNewPassword(
        newPassword: String,
        userId: UUID,
    ): Result<PasswordWrapped, SessionError> {
        val active = ark ?: return Result.Failure(SessionError.Locked)
        if (failDerivation) return Result.Failure(SessionError.Derivation("forced"))

        val salt = randomBytes(16)
        return Result.Success(
            PasswordWrapped(
                salt = salt,
                wrapped = wrap(kek(newPassword, salt), active, userId),
            ),
        )
    }

    override suspend fun wrapVaultKey(
        vaultKey: ByteArray,
        vaultId: UUID,
    ): Result<WrappedKeyBlob, SessionError> {
        val active = ark ?: return Result.Failure(SessionError.Locked)
        return Result.Success(wrap(active, vaultKey, vaultId))
    }

    override suspend fun unwrapVaultKey(
        wrapped: WrappedKeyBlob,
        vaultId: UUID,
    ): Result<ByteArray, SessionError> {
        unwrapVaultKeyFailure?.let { return Result.Failure(it) }
        val active = ark ?: return Result.Failure(SessionError.Locked)
        val recovered = unwrap(active, wrapped, vaultId)
            ?: return Result.Failure(SessionError.KeyWrap(KeyWrapException.UnwrapFailed()))
        return Result.Success(recovered)
    }

    override fun endSession() {
        ark = null
        _isActive.value = false
    }

    private fun kek(password: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray() + salt)

    /** Wraps [innerKey] under (outerKey, id). The nonce and a short tag ride together in [WrappedKeyBlob.nonce]. */
    private fun wrap(outerKey: ByteArray, innerKey: ByteArray, id: UUID): WrappedKeyBlob {
        val nonce = randomBytes(NONCE_SIZE)
        val ciphertext = xorStream(innerKey, outerKey, id, nonce)
        return WrappedKeyBlob(
            ciphertext = ciphertext,
            nonce = nonce + tagFor(outerKey, id, nonce, innerKey),
        )
    }

    /** Inverts [wrap], or returns null when [wrapped] was not sealed under this (outerKey, id). */
    private fun unwrap(outerKey: ByteArray, wrapped: WrappedKeyBlob, id: UUID): ByteArray? {
        if (wrapped.nonce.size < NONCE_SIZE + TAG_SIZE) return null

        val nonce = wrapped.nonce.copyOfRange(0, NONCE_SIZE)
        val tag = wrapped.nonce.copyOfRange(NONCE_SIZE, wrapped.nonce.size)
        val candidate = xorStream(wrapped.ciphertext, outerKey, id, nonce)

        return candidate.takeIf { tagFor(outerKey, id, nonce, it).contentEquals(tag) }
    }

    /** A short, non-cryptographic integrity tag: enough to reject a wrong key or id in tests. */
    private fun tagFor(
        outerKey: ByteArray,
        id: UUID,
        nonce: ByteArray,
        innerKey: ByteArray,
    ): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(outerKey + id.toString().toByteArray() + nonce + innerKey)
            .copyOf(TAG_SIZE)

    private fun xorStream(
        data: ByteArray,
        outerKey: ByteArray,
        id: UUID,
        nonce: ByteArray,
    ): ByteArray {
        val idBytes = id.toString().toByteArray()
        return ByteArray(data.size) { i ->
            val mask = outerKey[i % outerKey.size].toInt() xor
                    idBytes[i % idBytes.size].toInt() xor
                    nonce[i % nonce.size].toInt()
            (data[i].toInt() xor mask).toByte()
        }
    }

    private fun randomBytes(size: Int): ByteArray = random.nextBytes(size)

    private fun randomUUID(): UUID = UUID(random.nextLong(), random.nextLong())

    private companion object {
        const val SEED = 42L
        const val NONCE_SIZE = 12
        const val TAG_SIZE = 8
    }
}

class FakeArkCredential(val session: FakeSession) : ArkCredential(NoHandle)
