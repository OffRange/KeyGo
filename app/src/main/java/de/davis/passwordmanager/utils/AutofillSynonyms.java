package de.davis.passwordmanager.utils;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AutofillSynonyms {

    private static final Gson GSON = new Gson();

    public static Synonyms getSynonyms(Context context){
        try {
            return GSON.fromJson(new InputStreamReader(context.getResources().getAssets().open("autofill_synonyms.json")), Synonyms.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static class Synonyms {

        private Language german;
        private Language english;

        public Language getGerman() {
            return german;
        }

        public Language getEnglish() {
            return english;
        }

        public void iterateLanguages(Consumer<Language> consumer){
            List<Language> languages = new ArrayList<>();
            languages.add(getGerman());
            languages.add(getEnglish());

            languages.forEach(consumer);
        }

        public List<String> getAllPasswords(){
            List<String> passwords = new ArrayList<>();
            passwords.addAll(getGerman().getPasswords());
            passwords.addAll(getEnglish().getPasswords());

            return passwords;
        }

        public List<String> getAllUsernames(){
            List<String> usernames = new ArrayList<>();
            usernames.addAll(getGerman().getUsernames());
            usernames.addAll(getEnglish().getUsernames());

            return usernames;
        }

        public static class Language {

            private List<String> usernames;
            private List<String> passwords;

            public List<String> getUsernames() {
                return usernames;
            }

            public List<String> getPasswords() {
                return passwords;
            }
        }
    }
}
