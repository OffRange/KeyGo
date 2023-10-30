package de.davis.passwordmanager.backup;

import static de.davis.passwordmanager.backup.DataBackup.TYPE_IMPORT;

public class Result {
    public static class Success extends Result {

        @DataBackup.Type
        private int type;

        public Success(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }

    public static class Error extends Result {

        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class Duplicate extends Success {

        private final int count;

        public Duplicate(int count) {
            super(TYPE_IMPORT);
            this.count = count;
        }

        public int getCount() {
            return count;
        }
    }
}
