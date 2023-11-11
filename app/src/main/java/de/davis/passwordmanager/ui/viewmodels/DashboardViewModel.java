package de.davis.passwordmanager.ui.viewmodels;

import static androidx.lifecycle.SavedStateHandleSupport.createSavedStateHandle;
import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.List;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.database.dto.SecureElement;
import de.davis.passwordmanager.filter.Filter;
import de.davis.passwordmanager.ui.viewmodels.repositories.DashboardRepo;

public class DashboardViewModel extends ViewModel {

    private static final String QUERY = "query";

    private final MediatorLiveData<List<SecureElement>> searchResults = new MediatorLiveData<>();
    private final SavedStateHandle savedStateHandle;

    private final MutableLiveData<List<SecureElement>> elements;


    public DashboardViewModel(DashboardRepo dashboardRepo, SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;

        searchResults.addSource(savedStateHandle.getLiveData(QUERY, ""), query -> {
            searchResults.postValue(dashboardRepo.search(query));
        });

        elements = (MutableLiveData<List<SecureElement>>) Transformations.map(dashboardRepo.getElements(), Filter.DEFAULT::filter);
        Filter.DEFAULT.setUpdater(() -> elements.setValue(Filter.DEFAULT.filter(dashboardRepo.getElements().getValue())));
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public void search(String query){
        savedStateHandle.set(QUERY, query);
    }

    public LiveData<List<SecureElement>> getSearchResults(){
        return searchResults;
    }

    public String getSearchQuery(){
        return savedStateHandle.get(QUERY);
    }

    public static final ViewModelInitializer<DashboardViewModel> initializer = new ViewModelInitializer<>(DashboardViewModel.class, creationExtras ->
    {
        PasswordManagerApplication app = (PasswordManagerApplication) creationExtras.get(APPLICATION_KEY);
        if(app == null)
            throw new RuntimeException("app is null");

        return new DashboardViewModel(DashboardRepo.getInstance(), createSavedStateHandle(creationExtras));
    });
}
