package de.davis.passwordmanager.ui.viewmodels;

import static androidx.lifecycle.SavedStateHandleSupport.createSavedStateHandle;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.List;

import de.davis.passwordmanager.database.SecureElementManager;
import de.davis.passwordmanager.database.dtos.SecureElement;

public class HighlightsViewModel extends ViewModel {

    private static final String STATE = "state";

    private final SavedStateHandle savedStateHandle;

    private final MediatorLiveData<List<SecureElement>> elements = new MediatorLiveData<>();

    public HighlightsViewModel(SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;

        elements.addSource(savedStateHandle.getLiveData(STATE, true), last ->{
            if(last)
                elements.postValue(SecureElementManager.getLastCreatedSync(5));
            else
                elements.postValue(SecureElementManager.getLastModifiedSync(5));
        });
    }

    public void setState(boolean lastAdded){
        savedStateHandle.set(STATE, lastAdded);
    }

    public LiveData<List<SecureElement>> getElements() {
        return elements;
    }

    public List<SecureElement> getFavorites() {
        return SecureElementManager.getFavoritesSync(10);
    }

    public static final ViewModelInitializer<HighlightsViewModel> initializer = new ViewModelInitializer<>(HighlightsViewModel.class, creationExtras ->
            new HighlightsViewModel(createSavedStateHandle(creationExtras)));
}
