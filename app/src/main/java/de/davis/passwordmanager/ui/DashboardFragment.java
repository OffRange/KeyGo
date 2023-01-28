package de.davis.passwordmanager.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.FragmentViewBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.viewmodels.DashboardViewModel;
import de.davis.passwordmanager.ui.views.AddBottomSheet;
import de.davis.passwordmanager.ui.views.OptionBottomSheet;

public class DashboardFragment extends Fragment implements SearchView.OnQueryTextListener {

    private FragmentViewBinding binding;

    private DashboardViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentViewBinding.inflate(inflater, container, false);
        binding.recyclerView.setHasFixedSize(true);

        SecureElementManager manager = SecureElementManager.createNew(m -> {
            boolean hasElements = m.hasElements();
            binding.progress.setVisibility(View.GONE);

            if(!TextUtils.isEmpty(viewModel.getQuery()) && !hasElements){
                binding.noResults.setVisibility(View.VISIBLE);
                return;
            }

            binding.noResults.setVisibility(View.GONE);

            binding.recyclerView.setVisibility(hasElements ? View.VISIBLE : View.GONE);
            binding.viewToShow.setVisibility(hasElements ? View.GONE : View.VISIBLE);
        });
        manager.getAdapter().applyWithTracker(binding.recyclerView);

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
                searchView.setOnQueryTextListener(DashboardFragment.this);
                searchView.setQuery(viewModel.getQuery(), false);
                searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
                    if(!hasFocus && TextUtils.isEmpty(searchView.getQuery()))
                        searchView.onActionViewCollapsed();
                });

                manager.getAdapter().setStateChangeHandler(selectedItems -> {
                    menu.findItem(R.id.more).setVisible(selectedItems > 0);
                    requireActivity().setTitle(selectedItems > 0 ? getString(R.string.selected_items, selectedItems) : getString(R.string.your_dashboard));
                });
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this, ViewModelProvider.Factory.from(DashboardViewModel.initializer)).get(DashboardViewModel.class);
        viewModel.getElements().observe(getViewLifecycleOwner(), secureElements -> SecureElementManager.getInstance().update(secureElements, null));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        SecureElementManager.getInstance().getAdapter().getTracker().onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        SecureElementManager.getInstance().getAdapter().getTracker().onRestoreInstanceState(savedInstanceState);
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
        viewModel.filter(newText);
        return true;
    }
}
