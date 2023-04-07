package de.davis.passwordmanager.utils;

import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

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

        String formatted = cardNumber.replaceAll("\\s", "");

        return formatted.length() == 16;
    }

    public static String formatNumber(String s){
        String f = s.replaceAll("\\s", "").replaceAll("\\d{4}", "$0 ");
        return f.endsWith(" ") ? f.substring(0, f.length() -1) : f;
    }

    public static String formatDate(Date date){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());
        return simpleDateFormat.format(date);
    }
}
