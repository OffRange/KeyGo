package de.davis.passwordmanager.sync;

public class Result {
    public static class Success extends Result {

        @DataTransfer.Type
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
}
