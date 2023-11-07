package de.davis.passwordmanager.ui.viewmodels.repositories;

import androidx.lifecycle.LiveData;

import java.util.List;

import de.davis.passwordmanager.database.KeyGoDatabase;
import de.davis.passwordmanager.security.element.SecureElement;

public class HighlightsRepo {

    private static volatile HighlightsRepo instance;

    private final KeyGoDatabase database;

    public HighlightsRepo(KeyGoDatabase database) {
        this.database = database;
    }

    public LiveData<List<SecureElement>> getLastAdded(){
        return database.secureElementDao().getLastCreated(5);
    }

    public LiveData<List<SecureElement>> getLastModified(){
        return database.secureElementDao().getLastModified(5);
    }

    public LiveData<List<SecureElement>> getFavorites(){
        return database.secureElementDao().getFavorites(5);
    }

    public static synchronized HighlightsRepo getInstance(KeyGoDatabase database){
        if(instance == null)
            instance = new HighlightsRepo(database);

        return instance;
    }
}
