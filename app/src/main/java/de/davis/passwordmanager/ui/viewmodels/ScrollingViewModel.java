package de.davis.passwordmanager.ui.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ScrollingViewModel extends ViewModel {

    private final MutableLiveData<Integer> consumedY = new MutableLiveData<>();
    private final MutableLiveData<Boolean> fabVisibility = new MutableLiveData<>();

    public void setConsumedY(int consumed){
        consumedY.setValue(consumed);
    }

    public void setFabVisibility(boolean visible){
        fabVisibility.setValue(visible);
    }

    public LiveData <Integer> getConsumedY() {
        return consumedY;
    }

    public MutableLiveData<Boolean> getFabVisibility() {
        return fabVisibility;
    }
}
