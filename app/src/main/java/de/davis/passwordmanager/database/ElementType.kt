package de.davis.passwordmanager.database

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.details.ElementDetail
import de.davis.passwordmanager.database.entities.details.creditcard.CreditCardDetails
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.ui.elements.CreateSecureElementActivity
import de.davis.passwordmanager.ui.elements.creditcard.CreateCreditCardActivity
import de.davis.passwordmanager.ui.elements.creditcard.ViewCreditCardFragment
import de.davis.passwordmanager.ui.elements.password.CreatePasswordActivity
import de.davis.passwordmanager.ui.elements.password.ViewPasswordFragment

const val TAG_PREFIX: String = "elementType:"

enum class ElementType(
    val typeId: Int,
    val elementDetailClass: Class<out ElementDetail>,
    val createActivityClass: Class<out CreateSecureElementActivity>,
    @IdRes val viewFragmentId: Int,
    @StringRes var title: Int,
    @DrawableRes val icon: Int,
    var tag: Tag
) {
    PASSWORD(
        0x1,
        PasswordDetails::class.java,
        CreatePasswordActivity::class.java,
        ViewPasswordFragment.ID,
        R.string.password,
        R.drawable.ic_baseline_password_24,
        Tag("${TAG_PREFIX}password")
    ),
    CREDIT_CARD(
        0x11,
        CreditCardDetails::class.java,
        CreateCreditCardActivity::class.java,
        ViewCreditCardFragment.ID,
        R.string.credit_card,
        R.drawable.ic_baseline_credit_card_24,
        Tag("${TAG_PREFIX}credit_card")
    );

    companion object {
        @JvmStatic
        fun getTypeByTypeId(id: Int): ElementType {
            return entries.first { it.typeId == id }
        }
    }
}