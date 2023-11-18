package de.davis.passwordmanager.database.entities.details.password

import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import com.google.android.material.color.MaterialColors
import de.davis.passwordmanager.R
import me.gosimple.nbvcxz.Nbvcxz
import java.io.Serializable

enum class Strength(@ColorRes val string: Int, @AttrRes val color: Int) : Serializable {

    RIDICULOUS(R.string.ridiculous, R.attr.colorRidiculous),
    WEAK(R.string.weak, R.attr.colorWeak),
    MODERATE(R.string.moderate, R.attr.colorModerate),
    STRONG(R.string.strong, R.attr.colorStrong),
    VERY_STRONG(R.string.very_strong, R.attr.colorVeryStrong);


    fun getColor(context: Context): Int {
        return MaterialColors.getColor(context, color, Color.BLACK)
    }
}

object EstimationHandler {

    private val nbvcxz = Nbvcxz()

    @JvmStatic
    fun estimate(password: String): Strength {
        val result = nbvcxz.estimate(password)
        return Strength.entries[result.basicScore]
    }

    @JvmStatic
    fun estimateWrapper(password: String): Wrapper {
        val result = nbvcxz.estimate(password)
        return Wrapper(Strength.entries[result.basicScore], result.feedback.warning)
    }
}

class Wrapper(val strength: Strength, val warning: String?)