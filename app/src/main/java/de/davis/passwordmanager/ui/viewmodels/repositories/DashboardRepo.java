package de.davis.passwordmanager.ui.viewmodels.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.List;

import de.davis.passwordmanager.database.KeyGoDatabase;
import de.davis.passwordmanager.security.element.SecureElement;

public class DashboardRepo {

    private static volatile DashboardRepo instance;

    private final KeyGoDatabase database;
    private final MediatorLiveData<List<SecureElement>> elements;

    private DashboardRepo(KeyGoDatabase database) {
        this.database = database;

        this.elements = new MediatorLiveData<>();
        this.elements.addSource(database.secureElementDao().getAll(), this.elements::postValue);
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public LiveData<List<SecureElement>> search(String query){
        return database.secureElementDao().getByTitle(query);
    }

    public static synchronized DashboardRepo getInstance(KeyGoDatabase database){
        if(instance == null)
            instance = new DashboardRepo(database);

        return instance;
    }
}
