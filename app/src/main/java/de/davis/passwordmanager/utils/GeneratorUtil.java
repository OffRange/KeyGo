package de.davis.passwordmanager.utils;

import android.util.SparseArray;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GeneratorUtil {


    static final String DIGITS = "0123456789";
    static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    public static final int USE_DIGITS = 0xf;
    public static final int USE_LOWERCASE = 0xf0;
    public static final int USE_UPPERCASE = 0xf00;
    public static final int USE_PUNCTUATION = 0xf000;

    @IntDef({
            USE_DIGITS,
            USE_LOWERCASE,
            USE_UPPERCASE,
            USE_PUNCTUATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Types{}

    static final SparseArray<List<Character>> replacements = new SparseArray<>();

    static {
        replacements.put('a', List.of('4'));
        replacements.put('b', List.of('8'));
        replacements.put('e', List.of('3'));
        replacements.put('f', List.of('7'));
        replacements.put('g', List.of('9', '6'));
        replacements.put('i', List.of('1', '!'));
        replacements.put('o', List.of('0'));
        replacements.put('s', List.of('5', '$', '$'));
        replacements.put('t', List.of('7'));
        replacements.put('z', List.of('2'));
    }

    public static String generatePassword(int length, @Types int types){
        Random random = new SecureRandom();
        return generateRandomResult(length, "", random, getTypes(types), new PasswordPolicy());
    }

    private static String generateRandomResult(int length, String initialValue, Random random, List<Integer> typesList, GenerationPolicy generationPolicy){
        StringBuilder builder = new StringBuilder(initialValue);
        for (int i = 0; i < length; i++) {
            switch (typesList.get(random.nextInt(typesList.size()))) {
                case USE_DIGITS -> generationPolicy.generateDigits(builder, i, random);
                case USE_UPPERCASE -> generationPolicy.generateUppercase(builder, i, random);
                case USE_LOWERCASE -> generationPolicy.generateLowercase(builder, i, random);
                case USE_PUNCTUATION -> generationPolicy.generatePunctuation(builder, i, random);
            }
        }

        return builder.toString();
    }

    @NonNull
    public static String generatePassphrase(@IntRange(from = 1) int length, @NonNull List<String> words, @Types int types){
        SecureRandom random = new SecureRandom();

        List<Integer> typesList = getTypes(types);
        GenerationPolicy policy = new PassphrasePolicy();

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String word = words.get(random.nextInt(words.size()));
            builder.append(generateRandomResult(word.length(), word, random, typesList, policy));
            builder.append('-');
        }

        builder.deleteCharAt(builder.length()-1);

        return builder.toString();
    }

    private static boolean checkUses(@Types int generationCharacters, @Types int checkAgainst){
        return (generationCharacters & checkAgainst) != 0;
    }

    private static List<Integer> getTypes(@Types int types){
        List<Integer> list = new ArrayList<>();
        if (checkUses(types, USE_DIGITS))
            list.add(USE_DIGITS);

        if (checkUses(types, USE_LOWERCASE))
            list.add(USE_LOWERCASE);

        if (checkUses(types, USE_UPPERCASE))
            list.add(USE_UPPERCASE);

        if (checkUses(types, USE_PUNCTUATION))
            list.add(USE_PUNCTUATION);

        return list;
    }

    private interface GenerationPolicy{
        void generateDigits(StringBuilder stringBuilder, int index, Random random);
        void generateUppercase(StringBuilder stringBuilder, int index, Random random);
        void generateLowercase(StringBuilder stringBuilder, int index, Random random);
        void generatePunctuation(StringBuilder stringBuilder, int index, Random random);
    }

    private static class PasswordPolicy implements GenerationPolicy{

        @Override
        public void generateDigits(StringBuilder stringBuilder, int index, Random random) {
            stringBuilder.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }

        @Override
        public void generateUppercase(StringBuilder stringBuilder, int index, Random random) {
            stringBuilder.append(UPPER_CASE.charAt(random.nextInt(UPPER_CASE.length())));
        }

        @Override
        public void generateLowercase(StringBuilder stringBuilder, int index, Random random) {
            stringBuilder.append(LOWER_CASE.charAt(random.nextInt(LOWER_CASE.length())));
        }

        @Override
        public void generatePunctuation(StringBuilder stringBuilder, int index, Random random) {
            stringBuilder.append(PUNCTUATION.charAt(random.nextInt(PUNCTUATION.length())));
        }
    }

    private static class PassphrasePolicy implements GenerationPolicy{

        @Override
        public void generateDigits(StringBuilder stringBuilder, int index, Random random) {
            char replacementKey = stringBuilder.charAt(index);
            List<Character> replacementValues = replacements.get(replacementKey);
            if(replacementValues == null)
                return;

            stringBuilder.setCharAt(index, replacementValues.get(random.nextInt(replacementValues.size())));
        }

        @Override
        public void generateUppercase(StringBuilder stringBuilder, int index, Random random) {
            stringBuilder.setCharAt(index, Character.toUpperCase(stringBuilder.charAt(index)));
        }

        @Override
        public void generateLowercase(StringBuilder stringBuilder, int index, Random random) {
            stringBuilder.setCharAt(index, Character.toLowerCase(stringBuilder.charAt(index)));
        }

        @Override
        public void generatePunctuation(StringBuilder stringBuilder, int index, Random random) {}
    }
}
