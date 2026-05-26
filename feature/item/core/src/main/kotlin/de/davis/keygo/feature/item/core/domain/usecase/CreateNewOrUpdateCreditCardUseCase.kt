package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.feature.item.core.domain.model.CreditCardUpsertError
import de.davis.keygo.feature.item.core.domain.model.FieldUpdate
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertCreditCard
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davis.keygo.feature.item.core.domain.model.getValue
import de.davis.keygo.feature.item.core.domain.model.on
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.withoutClearingOn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Single
class CreateNewOrUpdateCreditCardUseCase(
    cryptographicScopeProvider: CryptographicScopeProvider,
    private val creditCardRepository: CreditCardRepository,
    vaultRepository: VaultRepository,
    upsertVaultItem: UpsertVaultItemUseCase,
) : CreateOrUpdateItemUseCase<UpsertCreditCard, CreditCard>(
    cryptographicScopeProvider = cryptographicScopeProvider,
    vaultRepository = vaultRepository,
    upsertVaultItem = upsertVaultItem,
) {

    override fun validate(upsert: UpsertCreditCard): Set<ItemUpsertError> {
        val errors = mutableSetOf<ItemUpsertError>()
        val allowKeep = upsert.upsertType is UpsertType.Update

        if (!isPresent(upsert.name, allowKeep))
            errors.add(ItemUpsertError.BlankName)

        if (!isValidExpiration(upsert.expirationDate, allowKeep))
            errors.add(CreditCardUpsertError.InvalidExpiration)

        return errors
    }

    private fun isPresent(field: FieldUpdate<String>, allowKeep: Boolean): Boolean = when (field) {
        is FieldUpdate.Keep -> allowKeep
        is FieldUpdate.Clear -> false
        is FieldUpdate.Set<String> -> field.value.isNotBlank()
    }

    private fun isValidExpiration(field: FieldUpdate<String>, allowKeep: Boolean): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> false
            is FieldUpdate.Set<String> -> field.value.toYearMonthOrNull() != null
        }

    override suspend fun fetchExisting(id: ItemId): CreditCard? =
        creditCardRepository.getCreditCardById(id)

    override fun isEmpty(item: CreditCard, upsert: UpsertCreditCard): Boolean =
        item.lastNumbers.isBlank()

    override fun relocate(
        item: CreditCard,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): CreditCard = item.copy(vaultId = vaultId, keyInformation = keyInformation)

    context(scope: CryptographicScope)
    override suspend fun buildCreate(
        upsert: UpsertCreditCard,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): CreditCard = coroutineScope {
        val number = upsert.cardNumber.getValue().orEmpty()
        val encryptedNumber = async { CreditCard.CardNumber.encrypt(number) }
        val encryptedCvv = upsert.cvv.onSet { cvv -> async { CreditCard.CVV.encrypt(cvv) } }

        CreditCard(
            id = itemId,
            vaultId = vaultId,
            name = upsert.name.getValue()!!,
            keyInformation = keyInformation,
            tags = upsert.tags.getValue().orEmpty(),
            note = upsert.note.getValue(),
            pinned = false,
            holder = upsert.holder.getValue(),
            lastNumbers = number.toLastNumbers(),
            cardNumber = encryptedNumber.await(),
            cvv = encryptedCvv?.await(),
            expirationDate = upsert.expirationDate.getValue()!!.toYearMonthOrNull()!!,
        )
    }

    context(scope: CryptographicScope)
    override suspend fun buildUpdate(
        upsert: UpsertCreditCard,
        existing: CreditCard,
    ): CreditCard = coroutineScope {
        val encryptedNumber = upsert.cardNumber.onSet { num ->
            async { CreditCard.CardNumber.encrypt(num) }
        }
        val encryptedCvv = upsert.cvv.onSet { cvv -> async { CreditCard.CVV.encrypt(cvv) } }

        existing.copy(
            name = upsert.name.withoutClearingOn(existing.name),
            note = upsert.note.on(existing.note),
            tags = upsert.tags.on(existing.tags).orEmpty(),
            holder = upsert.holder.on(existing.holder),
            cardNumber = encryptedNumber?.await() ?: existing.cardNumber,
            lastNumbers = upsert.cardNumber.getValue()?.toLastNumbers() ?: existing.lastNumbers,
            cvv = upsert.cvv.on(existing.cvv, encryptedCvv),
            expirationDate = upsert.expirationDate.getValue()?.toYearMonthOrNull()
                ?: existing.expirationDate,
        )
    }

    private fun String.toLastNumbers(): String = filter(Char::isDigit).takeLast(4)

    private fun String.toYearMonthOrNull(): YearMonth? = try {
        YearMonth.parse(this, EXPIRATION_FORMATTER)
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        // "yy" parses into the 2000-2099 range, which is correct for card expirations.
        private val EXPIRATION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")
    }
}
