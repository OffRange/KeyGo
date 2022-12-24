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

public class Strength implements Serializable {

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
    @interface StrengthType {
    }

    private final int type;

    private final String warning;

    private final List<String> suggestions;

    public Strength(@StrengthType int type, String warning, List<String> suggestions) {
        this.type = type;
        this.warning = warning;
        this.suggestions = suggestions;
    }

    @StrengthType
    public int getType() {
        return type;
    }

    public String getWarning() {
        return warning;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    @StringRes
    public int getString() {
        switch (type) {
            case RIDICULOUS:
                return R.string.ridiculous;
            case WEAK:
                return R.string.weak;
            case MODERATE:
                return R.string.moderate;
            case STRONG:
                return R.string.strong;
            default:
                return R.string.very_strong;
        }
    }

    @AttrRes
    public int getColor() {
        switch (type) {
            case RIDICULOUS:
                return R.attr.colorRidiculous;
            case WEAK:
                return R.attr.colorWeak;
            case MODERATE:
                return R.attr.colorModerate;
            case STRONG:
                return R.attr.colorStrong;
            default:
                return R.attr.colorVeryStrong;
        }
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
