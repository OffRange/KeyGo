package de.davis.passwordmanager.ui.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.filter.Filter;

public class FilterBottomSheet extends BottomSheetDialogFragment {

    private static final int ID_PASSWORD = R.id.password;

    public FilterBottomSheet() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InformationView type = view.findViewById(R.id.type);
        InformationView strength = view.findViewById(R.id.strength);

        ChipGroup strengthGroup = (ChipGroup)((ViewGroup)strength.getContent()).getChildAt(0);
        ChipGroup typeGroup = (ChipGroup)((ViewGroup)type.getContent()).getChildAt(0);
        Filter.DEFAULT.setStrength(strengthGroup);
        Filter.DEFAULT.setType(typeGroup);


        updateSelection(strengthGroup, typeGroup);


        typeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            strength.setEnabled(checkedIds.contains(ID_PASSWORD));
            Filter.DEFAULT.update();
        });

        strengthGroup.setOnCheckedStateChangeListener((group, checkedIds) -> Filter.DEFAULT.update());
    }

    private void updateSelection(ChipGroup... groups){
        List<Integer> ids = Filter.DEFAULT.getSelectedIds();
        if(ids.isEmpty())
            return;

        for (ChipGroup group : groups) {
            group.clearCheck();
            ids.forEach(group::check);
        }
    }
}
