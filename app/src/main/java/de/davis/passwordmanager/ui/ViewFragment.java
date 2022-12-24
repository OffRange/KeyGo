package de.davis.passwordmanager.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.os.HandlerCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.databinding.FragmentViewBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.views.AddBottomSheet;
import de.davis.passwordmanager.ui.views.OptionBottomSheet;

public class ViewFragment extends Fragment implements SearchView.OnQueryTextListener {

    private FragmentViewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentViewBinding.inflate(inflater, container, false);
        binding.recyclerView.setHasFixedSize(true);

        SecureElementManager manager = SecureElementManager.createNew(m -> {
            boolean hasElements = m.hasElements();

            binding.recyclerView.setVisibility(hasElements ? View.VISIBLE : View.GONE);
            binding.viewToShow.setVisibility(hasElements ? View.GONE : View.VISIBLE);
            binding.progress.setVisibility(View.GONE);
        });
        manager.getAdapter().applyWithTracker(binding.recyclerView);

        SecureElementDatabase db = SecureElementDatabase.createAndGet(requireContext());
        db.getSecureElementDao().getAll().observe(getViewLifecycleOwner(), secureElements -> manager.update(secureElements, null));

        ActivityResultManager handler = ActivityResultManager.getOrCreateManager(getClass(), this);
        handler.registerCreate();
        handler.registerEdit(null);

        binding.add.setOnClickListener(v -> showBottomSheet());
        binding.viewAddFirst.setOnClickListener(v -> showBottomSheet());

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.view_menu, menu);
                SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setOnQueryTextListener(ViewFragment.this);

                manager.getAdapter().setStateChangeHandler((key, selected) -> menu.findItem(R.id.more).setVisible(manager.getAdapter().getTracker().hasSelection()));
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if(menuItem.getItemId() == R.id.more){
                    OptionBottomSheet optionBottomSheet = new OptionBottomSheet(requireContext(), null);
                    optionBottomSheet.show();
                }
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        return binding.getRoot();
    }

    private void showBottomSheet(){
        new AddBottomSheet().show(getParentFragmentManager(), "add-bottom-sheet");
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        Executor executor = Executors.newCachedThreadPool();
        Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());
        executor.execute(() -> {
            List<SecureElement> elements = SecureElementDatabase.getInstance().filterByTitle(newText);
            handler.post(() -> SecureElementManager.getInstance().filter(elements));
        });
        return true;
    }
}
