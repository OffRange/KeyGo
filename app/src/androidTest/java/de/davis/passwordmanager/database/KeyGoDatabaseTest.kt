package de.davis.passwordmanager.database

import android.content.Context
import androidx.room.Room.inMemoryDatabaseBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.davis.passwordmanager.database.daos.SecureElementWithTagDao
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.details.creditcard.CreditCardDetails
import de.davis.passwordmanager.database.entities.details.creditcard.Name
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.database.entities.onlyCustoms
import de.davis.passwordmanager.database.entities.wrappers.CombinedElement
import de.davis.passwordmanager.utils.GeneratorUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyGoDatabaseTest {
    private lateinit var db: KeyGoDatabase
    private lateinit var secureElementWithTagDao: SecureElementWithTagDao

    @Before
    fun createDB() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = inMemoryDatabaseBuilder(context, KeyGoDatabase::class.java).build()
        secureElementWithTagDao = db.combinedDao()
    }

    @Test
    fun testInsertAndRetrievePasswordElement() = runTest {
        val title = "Password Element"
        val password = generateTestPassword()
        val origin = "origin"
        val username = "username"
        val passwordElement = SecureElement(title, PasswordDetails(password, origin, username))
        val element = writeAndRead(passwordElement)
        element.run {
            secureElementEntity.run {
                (secureElementEntity.detail as PasswordDetails).run {
                    assertEquals(title, secureElementEntity.title)
                    assertEquals(password, this.password)
                    assertEquals(origin, this.origin)
                    assertEquals(username, this.username)
                }

                assertEquals(title, secureElementEntity.title)
                assertEquals(ElementType.PASSWORD, secureElementEntity.type)
            }

            assertEquals(ElementType.PASSWORD.tag.name, tags.first().name)
        }

    }

    @Test
    fun testInsertAndRetrieveCreditCardElement() = runTest {
        val title = "Credit Card Element"
        val expirationDate = "05/12"
        val cardNumber = "0000000000000000"
        val cvv = "222"
        val name = Name.fromFullName("cardholder")
        val passwordElement =
            SecureElement(title, CreditCardDetails(name, expirationDate, cardNumber, cvv))
        val element = writeAndRead(passwordElement)

        element.run {
            secureElementEntity.run {
                (secureElementEntity.detail as CreditCardDetails).run {
                    assertEquals(name, this.cardholder)
                    assertEquals(expirationDate, this.expirationDate)
                    assertEquals(cardNumber, this.cardNumber)
                    assertEquals(cvv, this.cvv)
                }

                assertEquals(title, secureElementEntity.title)
                assertEquals(ElementType.CREDIT_CARD, secureElementEntity.type)
            }

            assertEquals(ElementType.CREDIT_CARD.tag.name, tags.first().name)
        }
    }

    @Test
    fun testMergeTags() = runTest {
        val tag1 = "TAG 1"
        val tag2 = "TAG 2"
        val tag32 = "TAG 32"

        val tagIgnore = "TAG IGN"
        val mergeTag = "TAG 1"

        var creditCard = writeAndRead(
            SecureElement(
                "C",
                CreditCardDetails(Name.fromFullName(""), "", "0000000000000000", ""),
                listOf(Tag(tag1), Tag(tag2))
            )
        )

        var password = writeAndRead(
            SecureElement(
                "P",
                PasswordDetails("pwd", "", ""),
                listOf(Tag(tag32), Tag(tagIgnore))
            )
        )

        secureElementWithTagDao.mergeTags(
            creditCard.tags.onlyCustoms().filter { it.name != tagIgnore } +
                    password.tags.onlyCustoms().filter { it.name != tagIgnore },
            mergeTag
        )

        creditCard =
            secureElementWithTagDao.getCombinedElementById(creditCard.secureElementEntity.id)
        password = secureElementWithTagDao.getCombinedElementById(password.secureElementEntity.id)

        val creditCardTags = creditCard.tags.map { it.name }
        val passwordTags = password.tags.map { it.name }

        assertTrue(
            creditCardTags.size == 2 && creditCardTags.containsAll(
                listOf(
                    ElementType.CREDIT_CARD.tag.name,
                    mergeTag
                )
            )
        )
        assertTrue(
            passwordTags.size == 3 && passwordTags.containsAll(
                listOf(
                    ElementType.PASSWORD.tag.name,
                    tagIgnore,
                    mergeTag
                )
            )
        )
    }

    private suspend fun writeAndRead(element: SecureElement): CombinedElement {
        val id: Long = secureElementWithTagDao.insert(element.toEntity())

        return secureElementWithTagDao.getCombinedElementById(id);
    }

    private fun generateTestPassword(): String {
        return GeneratorUtil.generatePassword(
            15000, GeneratorUtil.USE_DIGITS or
                    GeneratorUtil.USE_LOWERCASE or
                    GeneratorUtil.USE_PUNCTUATION or
                    GeneratorUtil.USE_UPPERCASE
        )
    }

    @After
    fun cleanUp() {
        db.close()
    }
}