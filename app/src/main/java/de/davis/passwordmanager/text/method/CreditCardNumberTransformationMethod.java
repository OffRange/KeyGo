package de.davis.passwordmanager.text.method;

import android.text.method.PasswordTransformationMethod;
import android.view.View;

import androidx.annotation.NonNull;

public class CreditCardNumberTransformationMethod extends PasswordTransformationMethod {

    private static final char DOT = '•';

    @Override
    public CharSequence getTransformation(CharSequence source, View view) {
        return new CreditCardCharSequence(source);
    }

    private record CreditCardCharSequence(CharSequence source) implements CharSequence {

        @Override
            public int length() {
                return source.length();
            }

            @Override
            public char charAt(int index) {
                char c = source.charAt(index);
                if (!Character.isWhitespace(c)) {
                    return DOT;
                }
                return c;
            }

            @NonNull
            @Override
            public CharSequence subSequence(int start, int end) {
                return source.subSequence(start, end);
            }
        }

    public static PasswordTransformationMethod getInstance() {
        if (sInstance != null)
            return sInstance;

        sInstance = new CreditCardNumberTransformationMethod();
        return sInstance;
    }
    private static PasswordTransformationMethod sInstance;
}
