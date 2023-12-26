package de.davis.passwordmanager.ui.highlights;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.databinding.FragmentHighlightsBinding;
import de.davis.passwordmanager.ui.dashboard.viewholders.SecureElementViewHolder;
import de.davis.passwordmanager.ui.viewmodels.HighlightsViewModel;

public class HighlightsFragment extends Fragment {

    private FragmentHighlightsBinding binding;
    private HighlightsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHighlightsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelProvider.Factory.from(HighlightsViewModel.initializer)).get(HighlightsViewModel.class);

        int px = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16,
                requireContext().getResources().getDisplayMetrics());

        viewModel.getElements().observe(getViewLifecycleOwner(), elements -> {
            binding.viewToShow.setVisibility(elements.size() > 0 ? View.GONE : View.VISIBLE);
            binding.lastUsed.removeViews(2, binding.lastUsed.getChildCount()-2);
            elements.forEach(element -> {
                SecureElementViewHolder viewHolder = new SecureElementViewHolder(getLayoutInflater(), binding.lastUsed, getChildFragmentManager());
                viewHolder.bind(element, null, this::launchElement, false);
                viewHolder.letterView.setVisibility(View.GONE);
                viewHolder.itemView.setPadding(px, viewHolder.itemView.getPaddingTop(), px, viewHolder.itemView.getPaddingBottom());
                binding.lastUsed.addView(viewHolder.itemView);
            });
        });

        binding.materialButtonToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> viewModel.setState(group.getCheckedButtonId() == binding.lastAdded.getId()));


        List<SecureElement> favorites = viewModel.getFavorites();
        binding.noFavorites.setVisibility(favorites.size() > 0 ? View.GONE : View.VISIBLE);
        binding.favoriteContainer.removeViews(1, binding.favoriteContainer.getChildCount()-1);
        favorites.forEach(secureElement -> {
            View fav_view = getLayoutInflater().inflate(R.layout.fav_layout, null, false);
            ((TextView)fav_view.findViewById(R.id.title)).setText(secureElement.getTitle());
            ((ImageView)fav_view.findViewById(R.id.image)).setImageDrawable(secureElement.getIcon(requireContext()));

            fav_view.setOnClickListener(v -> launchElement(secureElement));

            ((ViewGroup)view.findViewById(R.id.favorite_container)).addView(fav_view);
        });
    }

    private void launchElement(SecureElement element){
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        Bundle bundle = new Bundle();
        bundle.putParcelable("element", element);
        navController.navigate(R.id.dashboardFragment, bundle);
    }
}
