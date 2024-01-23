package de.davis.passwordmanager.utils;

import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import de.davis.passwordmanager.utils.card.Card;
import de.davis.passwordmanager.utils.card.CardFactory;

public class CreditCardUtil {

    public static boolean isValidDateFormat(String formatted){
        if(formatted == null)
            return false;

        try{
            YearMonth.parse(formatted, DateTimeFormatter.ofPattern("MM/yy"));
            return true;
        }catch (Exception e){
            return false;
        }
    }

    public static boolean isValidCardNumberLength(String cardNumber){
        if(cardNumber == null)
            return false;

        Card card = CardFactory.INSTANCE.createFromCardNumber(cardNumber);

        return card.isValidLength();
    }

    public static boolean isValidCheckSum(String cardNumber){
        if(cardNumber == null)
            return false;

        Card card = CardFactory.INSTANCE.createFromCardNumber(cardNumber);

        return card.isValidLuhnNumber();
    }

    public static String formatNumber(String s){
        Card card = CardFactory.INSTANCE.createFromCardNumber(s);

        return card.getCardNumber();
    }

    public static String formatDate(Date date){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());
        return simpleDateFormat.format(date);
    }
}
