package de.davis.passwordmanager.ui.viewmodels.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.List;

import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;

public class DashboardRepo {

    private static volatile DashboardRepo instance;

    private final SecureElementDatabase database;
    private final MediatorLiveData<List<SecureElement>> elements;

    private DashboardRepo(SecureElementDatabase database) {
        this.database = database;

        this.elements = new MediatorLiveData<>();
        this.elements.addSource(database.getSecureElementDao().getAll(), this.elements::postValue);
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public LiveData<List<SecureElement>> filter(String query){
        return database.getSecureElementDao().getByTitle(query);
    }

    public static synchronized DashboardRepo getInstance(SecureElementDatabase database){
        if(instance == null)
            instance = new DashboardRepo(database);

        return instance;
    }
}
