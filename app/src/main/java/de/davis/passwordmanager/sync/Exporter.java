package de.davis.passwordmanager.sync;

import de.davis.passwordmanager.sync.Result;

public interface Exporter {

    Result exportElements() throws Exception;
}
