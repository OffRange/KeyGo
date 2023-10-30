package de.davis.passwordmanager.security.element;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import java.util.List;

import de.davis.passwordmanager.dashboard.DashboardAdapter;
import de.davis.passwordmanager.database.SecureElementDatabase;

public class SecureElementManager {

    private static SecureElementManager instance;

    private final DashboardAdapter adapter;
    private TriggerDataChanged triggerDataChanged;

    private SecureElementManager() {
        adapter = new DashboardAdapter();
    }

    public static SecureElementManager getInstance(){
        if(instance == null)
            instance = new SecureElementManager();

        return instance;
    }

    public void setTriggerDataChanged(TriggerDataChanged triggerDataChanged) {
        this.triggerDataChanged = triggerDataChanged;
    }

    public DashboardAdapter getAdapter() {
        return adapter;
    }

    public <E extends SecureElement> void switchFavoriteState(E element){
        element.setFavorite(!element.isFavorite());
        editElement(element);
    }

    public <E extends SecureElement> void editElement(E editedElement){
        doInBackground(() -> SecureElementDatabase.getInstance().getSecureElementDao().update(editedElement));
    }

    public <E extends SecureElement> void createElement(E element){
        doInBackground(() -> SecureElementDatabase.getInstance().getSecureElementDao().insert(element));

        triggerDataChanged();
    }

    public <E extends SecureElement> void update(List<E> overrideElements){
        adapter.update(overrideElements);
        triggerDataChanged();
    }

    @SuppressWarnings("unchecked")
    public <E extends SecureElement> void deleteSelected(){
        List<E> selectedElements = (List<E>) adapter.getSelectedElements();
        doInBackground(() -> selectedElements.forEach(element -> SecureElementDatabase.getInstance().getSecureElementDao().delete(element)));

        adapter.removeSelectedElements();
    }

    public <E extends SecureElement> void delete(E element){
        doInBackground(() -> SecureElementDatabase.getInstance().getSecureElementDao().delete(element));

        triggerDataChanged();
    }

    public void filter(List<SecureElement> query){
        adapter.showOnly(query);
    }

    public boolean hasElements(){
        return adapter.getItemCount() > 0;
    }

    private void triggerDataChanged(){
        if(triggerDataChanged != null)
            triggerDataChanged.triggerDataChanged(this);
    }

    public interface TriggerDataChanged{
        void triggerDataChanged(SecureElementManager manager);
    }
}
