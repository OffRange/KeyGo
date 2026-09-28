package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.item.core.domain.model.FieldUpdate
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.NewPasskey
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davis.keygo.feature.item.core.domain.model.getValue
import de.davis.keygo.feature.item.core.domain.model.on
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.withoutClearingOn
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

@Single
class CreateNewOrUpdateLoginUseCase(
    cryptographicScopeProvider: CryptographicScopeProvider,
    private val loginRepository: LoginRepository,
    vaultRepository: VaultRepository,
    upsertVaultItem: UpsertVaultItemUseCase,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val totpService: TotpService,
    private val registrableDomainResolver: RegistrableDomainResolver,
) : CreateOrUpdateItemUseCase<UpsertLogin, Login>(
    cryptographicScopeProvider = cryptographicScopeProvider,
    vaultRepository = vaultRepository,
    upsertVaultItem = upsertVaultItem,
) {

    private fun isValid(field: FieldUpdate<String>, allowKeep: Boolean = false): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> false
            is FieldUpdate.Set<String> -> field.value.isNotBlank()
        }

    override fun validate(upsert: UpsertLogin): Set<ItemUpsertError> {
        val errors = mutableSetOf<ItemUpsertError>()
        val allowKeep = upsert.upsertType is UpsertType.Update

        if (!isValid(field = upsert.name, allowKeep = allowKeep))
            errors.add(ItemUpsertError.BlankName)

        return errors
    }

    override suspend fun fetchExisting(id: ItemId): Login? = loginRepository.getLoginById(id)

    override fun isEmpty(item: Login, upsert: UpsertLogin): Boolean = !item.hasAnyContent

    override fun relocate(item: Login, vaultId: VaultId, keyInformation: KeyInformation): Login =
        item.copy(vaultId = vaultId, keyInformation = keyInformation)

    override fun touch(item: Login, timestamp: Timestamp): Login = item.copy(timestamp = timestamp)

    // Sealed here rather than in buildCreate/buildUpdate because the sealed rows have to reach the
    // repository next to the login, and a Login only carries passkey refs.
    override suspend fun persist(
        item: Login,
        upsert: UpsertLogin,
    ): Result<ItemId, ItemUpsertError> = resultBinding {
        val sealed =
            if (upsert.addedPasskeys.isEmpty()) emptyList()
            else itemScope(item) { upsert.addedPasskeys.map { it.seal(item.id) } }.bind()

        loginRepository.createOrUpdateLogin(item, sealed).bind(ItemUpsertError::DatabaseError)
    }

    override suspend fun CryptographicScope.buildCreate(
        upsert: UpsertLogin,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): Login = coroutineScope {
        val newPasswordCredential = when (val pw = upsert.password) {
            FieldUpdate.Keep,
            FieldUpdate.Clear -> null

            is FieldUpdate.Set<String> -> {
                val encrypted = async { PasswordSecret.encrypt(pw.value) }
                val strength = async { passwordStrengthEstimator(pw.value) }
                PasswordCredential(secret = encrypted.await(), score = strength.await())
            }
        }
        val totp = upsert.totpUriOrSecret.onSet { uriOrSecret ->
            async { uriOrSecret.convertTotpUriOrSecretToUri(itemId) }
        }

        Login(
            id = itemId,
            name = upsert.name.getValue()!!,
            username = upsert.username.getValue(),
            domainInfos = upsert.addedDomains.toDomainInfos(itemId),
            tags = upsert.tags.getValue().orEmpty(),
            passwordCredential = newPasswordCredential,
            totp = totp?.await(),
            passkeys = upsert.addedPasskeys.mapTo(mutableSetOf()) { it.ref },
            note = upsert.note.getValue(),
            pinned = false,
            keyInformation = keyInformation,
            vaultId = vaultId,
            timestamp = Timestamp(),
        )
    }

    override suspend fun CryptographicScope.buildUpdate(
        upsert: UpsertLogin,
        existing: Login
    ): Login = coroutineScope {
        val newPasswordCredential = when (val pw = upsert.password) {
            is FieldUpdate.Keep -> existing.passwordCredential
            is FieldUpdate.Clear -> null
            is FieldUpdate.Set<String> -> {
                val encrypted = async { PasswordSecret.encrypt(pw.value) }
                val strength = async { passwordStrengthEstimator(pw.value) }
                PasswordCredential(secret = encrypted.await(), score = strength.await())
            }
        }
        val totp = upsert.totpUriOrSecret.onSet { uriOrSecret ->
            async { uriOrSecret.convertTotpUriOrSecretToUri(existing.id) }
        }

        existing.copy(
            name = upsert.name.withoutClearingOn(existing.name),
            username = upsert.username.on(existing.username),
            domainInfos = existing.domainInfos
                .filterNotTo(mutableSetOf()) { it.value in upsert.removedDomains || it.value in upsert.addedDomains }
                    + upsert.addedDomains.toDomainInfos(existing.id),
            tags = upsert.tags.on(existing.tags).orEmpty(),
            passwordCredential = newPasswordCredential,
            totp = upsert.totpUriOrSecret.on(existing.totp, totp),
            // Resolved against what the table holds now, not against what the caller last saw, so
            // a passkey attached from elsewhere since then survives this save.
            passkeys = existing.passkeys - upsert.removedPasskeys + upsert.addedPasskeys.map { it.ref },
            note = upsert.note.on(existing.note),
        )
    }

    private fun Set<String>.toDomainInfos(loginId: ItemId): Set<DomainInfo> =
        mapTo(mutableSetOf()) { domain ->
            DomainInfo(
                loginId = loginId,
                value = domain,
                eTLD1 = registrableDomainResolver.resolve(domain),
            )
        }

    context(scope: CryptographicScope)
    private suspend fun NewPasskey.seal(loginId: ItemId) = with(scope) {
        Passkey(
            credentialId = credentialId,
            rp = rp,
            privateKey = Passkey.PrivateKey.encrypt(privateKey),
            loginId = loginId,
            user = user,
        )
    }

    context(scope: CryptographicScope)
    private suspend fun String.convertTotpUriOrSecretToUri(itemId: ItemId) = with(scope) {
        totpService.getInfoFromUriWithResult(this@convertTotpUriOrSecretToUri).fold(
            onSuccess = { secrets ->
                Totp(
                    loginId = itemId,
                    secret = Totp.Secret.encrypt(secrets.secret),
                    accountName = secrets.accountName,
                    issuer = secrets.issuer,
                    algorithm = secrets.algorithm.name.lowercase(),
                    digits = secrets.digits,
                    period = secrets.period,
                )
            },
            onFailure = {
                // not valid uri - treat it as secret
                Totp(
                    loginId = itemId,
                    secret = Totp.Secret.encrypt(this@convertTotpUriOrSecretToUri),
                )
            },
        )
    }
}
