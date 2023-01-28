package de.davis.passwordmanager.ui.viewmodels;

import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;
import static androidx.lifecycle.SavedStateHandleSupport.createSavedStateHandle;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.List;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.ui.viewmodels.repositories.DashboardRepo;

public class DashboardViewModel extends ViewModel {

    private static final String QUERY = "query";

    private final LiveData<List<SecureElement>> elements;
    private final MutableLiveData<String> query = new MutableLiveData<>();
    private final SavedStateHandle savedStateHandle;

    public DashboardViewModel(DashboardRepo dashboardRepo, SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;

        this.elements = Transformations.switchMap(query, input -> {
            if(TextUtils.isEmpty(input))
                return dashboardRepo.getElements();

            return dashboardRepo.filter("%"+ input +"%");
        });
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public void filter(String query){
        this.query.setValue(query);
        savedStateHandle.set(QUERY, query);
    }

    public String getQuery(){
        return savedStateHandle.get(QUERY);
    }

    public static final ViewModelInitializer<DashboardViewModel> initializer = new ViewModelInitializer<>(DashboardViewModel.class, creationExtras ->
    {
        PasswordManagerApplication app = (PasswordManagerApplication) creationExtras.get(APPLICATION_KEY);
        if(app == null)
            throw new RuntimeException("app is null");

        return new DashboardViewModel(DashboardRepo.getInstance(app.getDatabase()), createSavedStateHandle(creationExtras));
    });
}
