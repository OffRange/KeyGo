package de.davis.passwordmanager.sync;

public class Result {
    public static class Success extends Result {

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
