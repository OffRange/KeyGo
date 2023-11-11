package de.davis.passwordmanager.ui.viewmodels.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.List;

import de.davis.passwordmanager.database.SecureElementManager;
import de.davis.passwordmanager.database.dto.SecureElement;

public class DashboardRepo {

    private static volatile DashboardRepo instance;

    private final MediatorLiveData<List<SecureElement>> elements;

    private DashboardRepo() {
        this.elements = new MediatorLiveData<>();
        this.elements.addSource(SecureElementManager.getSecureElementsLiveData(null), this.elements::postValue);
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public List<SecureElement> search(String query){
        return SecureElementManager.getByTitleSync(query);
    }

    public static synchronized DashboardRepo getInstance(){
        if(instance == null)
            instance = new DashboardRepo();

        return instance;
    }
}
