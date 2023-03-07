package de.davis.passwordmanager.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.color.MaterialColors;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dashboard.DashboardAdapter;
import de.davis.passwordmanager.databinding.FragmentDashboardBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.callbacks.SearchViewBackPressedHandler;
import de.davis.passwordmanager.ui.viewmodels.DashboardViewModel;
import de.davis.passwordmanager.ui.views.AddBottomSheet;
import de.davis.passwordmanager.ui.views.OptionBottomSheet;

public class DashboardFragment extends Fragment implements SearchView.OnQueryTextListener {

    private FragmentDashboardBinding binding;

    private DashboardViewModel viewModel;

    private DashboardAdapter searchResultsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        binding.recyclerView.setHasFixedSize(true);

        SecureElementManager manager = SecureElementManager.createNew(m -> {
            boolean hasElements = m.hasElements();
            binding.progress.setVisibility(View.GONE);

            binding.recyclerView.setVisibility(hasElements ? View.VISIBLE : View.GONE);
            binding.viewToShow.setVisibility(hasElements ? View.GONE : View.VISIBLE);
        });
        manager.getAdapter().applyWithTracker(binding.recyclerView);

        ActivityResultManager handler = ActivityResultManager.getOrCreateManager(getClass(), this);
        handler.registerCreate();
        handler.registerEdit(null);

        binding.add.setOnClickListener(v -> showBottomSheet());

        ((AppCompatActivity)requireActivity()).setSupportActionBar(binding.searchBar);

        searchResultsAdapter = new DashboardAdapter();
        binding.recyclerViewResults.setAdapter(searchResultsAdapter);

        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.filter(s.toString());
            }
        });

        binding.viewAddFirst.setOnClickListener(v -> showBottomSheet());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new SearchViewBackPressedHandler(binding.searchView));

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.view_menu, menu);
                manager.getAdapter().setStateChangeHandler(selectedItems -> {
                    menu.findItem(R.id.more).setVisible(selectedItems > 0);
                    binding.searchBar.setHint(selectedItems > 0 ? getString(R.string.selected_items, selectedItems) : getString(android.R.string.search_go));
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

    public static class ScrollingViewBehavior extends AppBarLayout.ScrollingViewBehavior {

        private boolean initialized = false;

        public ScrollingViewBehavior() {}

        public ScrollingViewBehavior(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean onDependentViewChanged(
                @NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
            boolean changed = super.onDependentViewChanged(parent, child, dependency);
            if (!initialized && dependency instanceof AppBarLayout) {
                initialized = true;
                AppBarLayout appBarLayout = (AppBarLayout) dependency;
                setAppBarLayoutColor(appBarLayout);
            }
            return changed;
        }

        private void setAppBarLayoutColor(AppBarLayout appBarLayout) {
            appBarLayout.setBackgroundColor(MaterialColors.getColor(appBarLayout, com.google.android.material.R.attr.colorSurface));

            // Remove AppBarLayout elevation shadow
            appBarLayout.setElevation(0);
        }

        @Override
        protected boolean shouldHeaderOverlapScrollingChild() {
            return false;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this, ViewModelProvider.Factory.from(DashboardViewModel.initializer)).get(DashboardViewModel.class);
        viewModel.getElements().observe(getViewLifecycleOwner(), secureElements -> SecureElementManager.getInstance().update(secureElements, null));
        viewModel.getFiltered().observe(getViewLifecycleOwner(), secureElements -> {
            searchResultsAdapter.update(secureElements, null);
            searchResultsAdapter.setFilter(viewModel.getQuery());
            if(!TextUtils.isEmpty(viewModel.getQuery()) && secureElements.isEmpty()){
                binding.noResults.setVisibility(View.VISIBLE);
                return;
            }

            binding.noResults.setVisibility(View.GONE);
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        SecureElementManager.getInstance().getAdapter().getTracker().onSaveInstanceState(outState);
        outState.putCharSequence("searchbar_hint", binding.searchBar.getHint());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        SecureElementManager.getInstance().getAdapter().getTracker().onRestoreInstanceState(savedInstanceState);
        if(savedInstanceState == null)
            return;

        binding.searchBar.setHint(savedInstanceState.getCharSequence("searchbar_hint", getString(android.R.string.search_go)));
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
