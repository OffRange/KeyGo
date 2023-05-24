package de.davis.passwordmanager.ui.viewmodels.repositories;

import androidx.lifecycle.LiveData;

import java.util.List;

import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;

public class HighlightsRepo {

    private static volatile HighlightsRepo instance;

    private final SecureElementDatabase database;

    public HighlightsRepo(SecureElementDatabase database) {
        this.database = database;
    }

    public LiveData<List<SecureElement>> getLastAdded(){
        return database.getSecureElementDao().getLastCreated(5);
    }

    public LiveData<List<SecureElement>> getLastModified(){
        return database.getSecureElementDao().getLastModified(5);
    }

    public LiveData<List<SecureElement>> getFavorites(){
        return database.getSecureElementDao().getFavorites(5);
    }

    public static synchronized HighlightsRepo getInstance(SecureElementDatabase database){
        if(instance == null)
            instance = new HighlightsRepo(database);

        return instance;
    }
}
