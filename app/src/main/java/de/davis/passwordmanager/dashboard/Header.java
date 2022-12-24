package de.davis.passwordmanager.dashboard;

import java.util.UUID;

public class Header implements Item{

    private final String header;
    private final int id;

    public Header(String header) {
        int id = (int) (UUID.randomUUID().getMostSignificantBits() % Long.MAX_VALUE);
        this.id = Math.min(id, -id);

        this.header = header;
    }

    public String getHeader() {
        return header;
    }

    @Override
    public int getId() {
        return id;
    }
}
