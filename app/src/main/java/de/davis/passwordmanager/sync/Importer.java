package de.davis.passwordmanager.sync;

import androidx.annotation.WorkerThread;

import de.davis.passwordmanager.sync.Result;

@WorkerThread
public interface Importer {

    Result importElements() throws Exception;
}
