package de.davis.passwordmanager.dashboard;

public record Header(char header) implements Item {

    @Override
    public long getId() {
        return -header;
    }
}
