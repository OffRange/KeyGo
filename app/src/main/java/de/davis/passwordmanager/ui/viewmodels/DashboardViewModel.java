package de.davis.passwordmanager.ui.viewmodels;

import static androidx.lifecycle.SavedStateHandleSupport.createSavedStateHandle;
import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.List;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.database.KeyGoDatabase;
import de.davis.passwordmanager.filter.Filter;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.ui.viewmodels.repositories.DashboardRepo;

public class DashboardViewModel extends ViewModel {

    private static final String QUERY = "query";

    private final LiveData<List<SecureElement>> searchResults;
    private final SavedStateHandle savedStateHandle;

    private final MutableLiveData<List<SecureElement>> elements;


    public DashboardViewModel(DashboardRepo dashboardRepo, SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;

        this.searchResults = Transformations.switchMap(savedStateHandle.getLiveData(QUERY, ""), input -> {
            if(TextUtils.isEmpty(input))
                return dashboardRepo.getElements();

            return dashboardRepo.search("%"+ input +"%");
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

        return new DashboardViewModel(DashboardRepo.getInstance(KeyGoDatabase.getInstance()), createSavedStateHandle(creationExtras));
    });
}
