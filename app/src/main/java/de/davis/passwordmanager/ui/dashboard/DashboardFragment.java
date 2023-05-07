package de.davis.passwordmanager.ui.dashboard;

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
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.color.MaterialColors;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dashboard.DashboardAdapter;
import de.davis.passwordmanager.dashboard.viewholders.BasicViewHolder;
import de.davis.passwordmanager.databinding.FragmentDashboardBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.callbacks.SearchViewBackPressedHandler;
import de.davis.passwordmanager.ui.callbacks.SlidingBackPaneManager;
import de.davis.passwordmanager.ui.viewmodels.DashboardViewModel;
import de.davis.passwordmanager.ui.viewmodels.ScrollingViewModel;
import de.davis.passwordmanager.ui.views.AddBottomSheet;
import de.davis.passwordmanager.ui.views.FilterBottomSheet;
import de.davis.passwordmanager.ui.views.OptionBottomSheet;

public class DashboardFragment extends Fragment implements SearchView.OnQueryTextListener {

    private FragmentDashboardBinding binding;

    private DashboardViewModel viewModel;
    private ScrollingViewModel scrollingViewModel;

    private boolean oldState = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.listPane.recyclerView.setHasFixedSize(true);

        NavHostFragment navHostFragment = (NavHostFragment) getChildFragmentManager().findFragmentById(R.id.elementContainer);
        if(navHostFragment == null)
            return;

        NavController navController = navHostFragment.getNavController();
        binding.getRoot().setLockMode(SlidingPaneLayout.LOCK_MODE_LOCKED);

        ((AppCompatActivity)requireActivity()).setSupportActionBar(binding.listPane.searchBar);

        binding.listPane.viewAddFirst.setOnClickListener(v -> showBottomSheet());

        ActivityResultManager arm = ActivityResultManager.getOrCreateManager(getClass(), this);
        arm.registerCreate();
        arm.registerEdit(null);

        SecureElementManager manager = SecureElementManager.createNew(sem -> {
            boolean hasElements = sem.hasElements();
            binding.listPane.progress.setVisibility(View.GONE);

            binding.listPane.recyclerView.setVisibility(hasElements ? View.VISIBLE : View.GONE);
            binding.listPane.viewToShow.setVisibility(hasElements ? View.GONE : View.VISIBLE);
        });

        DashboardAdapter dashboardAdapter = manager.getAdapter();
        dashboardAdapter.applyWithTracker(binding.listPane.recyclerView);

        BasicViewHolder.OnItemClickedListener onItemClickedListener = element -> {
            scrollingViewModel.setVisibility(false);
            binding.listPane.searchView.hide();
            Bundle bundle = new Bundle();
            bundle.putSerializable("element", element);
            navController.popBackStack();
            navController.navigate(SecureElementDetail.getFor(element).getViewFragmentId(), bundle);

            binding.getRoot().open();
        };

        dashboardAdapter.setOnItemClickedListener(onItemClickedListener);

        DashboardAdapter searchResultAdapter = new DashboardAdapter();
        searchResultAdapter.setOnItemClickedListener(onItemClickedListener);
        binding.listPane.recyclerViewResults.setAdapter(searchResultAdapter);


        addMenu(dashboardAdapter);

        binding.listPane.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.search(s.toString());
            }
        });


        viewModel = new ViewModelProvider(this, ViewModelProvider.Factory.from(DashboardViewModel.initializer)).get(DashboardViewModel.class);
        viewModel.getElements().observe(getViewLifecycleOwner(), secureElements -> SecureElementManager.getInstance().update(secureElements));
        viewModel.getSearchResults().observe(getViewLifecycleOwner(), secureElements -> {
            searchResultAdapter.update(secureElements);
            searchResultAdapter.setFilter(viewModel.getSearchQuery());
            if(!TextUtils.isEmpty(viewModel.getSearchQuery()) && secureElements.isEmpty()){
                binding.listPane.noResults.setVisibility(View.VISIBLE);
                return;
            }

            binding.listPane.noResults.setVisibility(View.GONE);
        });


        scrollingViewModel = new ViewModelProvider(requireActivity()).get(ScrollingViewModel.class);

        SlidingBackPaneManager slidingBackPaneManager = new SlidingBackPaneManager(binding.slidingPaneLayout, scrollingViewModel);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), slidingBackPaneManager);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new SearchViewBackPressedHandler(binding.listPane.searchView));


        //Animation for fab and bottom nav bar
        binding.listPane.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                scrollingViewModel.setConsumedY(dy);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        oldState = Boolean.TRUE.equals(scrollingViewModel.getVisibility().getValue());
        scrollingViewModel.setVisibility(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        scrollingViewModel.setVisibility(oldState);
    }

    private void addMenu(DashboardAdapter dashboardAdapter){
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void  onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.view_menu, menu);
                dashboardAdapter.setStateChangeHandler(selectedItems -> {
                    requireActivity().invalidateMenu();
                    binding.listPane.searchBar.setHint(selectedItems > 0 ? getString(R.string.selected_items, selectedItems) : getString(android.R.string.search_go));
                });
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);
                menu.findItem(R.id.more).setVisible(dashboardAdapter.getTracker().hasSelection());
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if(menuItem.getItemId() == R.id.more){
                    OptionBottomSheet optionBottomSheet = new OptionBottomSheet(requireContext(), null);
                    optionBottomSheet.show();
                }else if(menuItem.getItemId() == R.id.filter){
                    new FilterBottomSheet().show(getParentFragmentManager(), "FilterDialog");
                }
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        SecureElementManager.getInstance().getAdapter().getTracker().onSaveInstanceState(outState);
        outState.putCharSequence("searchbar_hint", binding.listPane.searchBar.getHint());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        SecureElementManager.getInstance().getAdapter().getTracker().onRestoreInstanceState(savedInstanceState);
        if(savedInstanceState == null)
            return;

        binding.listPane.searchBar.setHint(savedInstanceState.getCharSequence("searchbar_hint", getString(android.R.string.search_go)));
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
        viewModel.search(newText);
        return true;
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
            if (!initialized && dependency instanceof AppBarLayout appBarLayout) {
                initialized = true;
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
}
