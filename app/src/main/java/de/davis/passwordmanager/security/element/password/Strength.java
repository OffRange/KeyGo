package de.davis.passwordmanager.security.element.password;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.StringRes;

import com.google.android.material.color.MaterialColors;

import java.io.Serializable;
import java.util.List;

import de.davis.passwordmanager.R;
import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.resources.Feedback;
import me.gosimple.nbvcxz.scoring.Result;

public record Strength(@StrengthType int type, String warning, List<String> suggestions) implements Serializable {

    private static final long serialVersionUID = 3096723233113390206L;
    private static final Nbvcxz nbvcxz = new Nbvcxz();

    public static final int RIDICULOUS = 0;
    public static final int WEAK = 1;
    public static final int MODERATE = 2;
    public static final int STRONG = 3;
    public static final int VERY_STRONG = 4;

    @IntDef({
            RIDICULOUS,
            WEAK,
            MODERATE,
            STRONG,
            VERY_STRONG
    })
    @interface StrengthType {}

    @StringRes
    public int getString() {
        return switch (type) {
            case RIDICULOUS -> R.string.ridiculous;
            case WEAK -> R.string.weak;
            case MODERATE -> R.string.moderate;
            case STRONG -> R.string.strong;
            default -> R.string.very_strong;
        };
    }

    @AttrRes
    private int getColor() {
        return switch (type) {
            case RIDICULOUS -> R.attr.colorRidiculous;
            case WEAK -> R.attr.colorWeak;
            case MODERATE -> R.attr.colorModerate;
            case STRONG -> R.attr.colorStrong;
            default -> R.attr.colorVeryStrong;
        };
    }

    @ColorInt
    public int getColor(Context context) {
        return MaterialColors.getColor(context, getColor(), Color.BLACK);
    }

    public static synchronized Strength estimateStrength(String password) {
        final Result result = nbvcxz.estimate(password);
        final Feedback feedback = result.getFeedback();
        return new Strength(result.getBasicScore(), feedback.getWarning(), feedback.getSuggestion());
    }
}
