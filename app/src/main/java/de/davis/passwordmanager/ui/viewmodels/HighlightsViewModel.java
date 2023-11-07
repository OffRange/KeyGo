package de.davis.passwordmanager.ui.viewmodels;

import static androidx.lifecycle.SavedStateHandleSupport.createSavedStateHandle;
import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.List;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.database.KeyGoDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.ui.viewmodels.repositories.HighlightsRepo;

public class HighlightsViewModel extends ViewModel {

    private static final String STATE = "state";

    private final SavedStateHandle savedStateHandle;

    private final LiveData<List<SecureElement>> elements;
    private final HighlightsRepo highlightsRepo;

    public HighlightsViewModel(HighlightsRepo highlightsRepo, SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;
        this.highlightsRepo = highlightsRepo;

        this.elements = Transformations.switchMap(savedStateHandle.getLiveData(STATE, true), input -> {
            if(input)
                return highlightsRepo.getLastAdded();

            return highlightsRepo.getLastModified();
        });
    }

    public void setState(boolean lastAdded){
        savedStateHandle.set(STATE, lastAdded);
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public LiveData<List<SecureElement>> getFavorites() {
        return highlightsRepo.getFavorites();
    }

    public static final ViewModelInitializer<HighlightsViewModel> initializer = new ViewModelInitializer<>(HighlightsViewModel.class, creationExtras ->
    {
        PasswordManagerApplication app = (PasswordManagerApplication) creationExtras.get(APPLICATION_KEY);
        if(app == null)
            throw new RuntimeException("app is null");

        return new HighlightsViewModel(HighlightsRepo.getInstance(KeyGoDatabase.getInstance()), createSavedStateHandle(creationExtras));
    });
}
