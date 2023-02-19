package de.davis.passwordmanager.dashboard;

public class Header implements Item{

    private final char header;

    public Header(char header) {
        this.header = header;
    }

    public char getHeader() {
        return header;
    }

    @Override
    public long getId() {
        return -header;
    }
}
