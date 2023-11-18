package de.davis.passwordmanager.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import de.davis.passwordmanager.R
import de.davis.passwordmanager.databinding.DialogFilterBinding
import de.davis.passwordmanager.filter.Filter

class FilterBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: DialogFilterBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Filter.DEFAULT.setStrength(binding.strengthGroup)
        Filter.DEFAULT.setType(binding.typeGroup)

        updateSelection(binding.strengthGroup, binding.typeGroup)

        binding.typeGroup.setOnCheckedStateChangeListener { _: ChipGroup, checkedIds: List<Int> ->
            binding.strength.isEnabled = checkedIds.contains(R.id.password)
            Filter.DEFAULT.update()
        }
        binding.strengthGroup.setOnCheckedStateChangeListener { _: ChipGroup, _: List<Int> -> Filter.DEFAULT.update() }
    }

    private fun updateSelection(vararg groups: ChipGroup) {
        val ids = Filter.DEFAULT.selectedIds
        if (ids.isEmpty())
            return

        groups.forEach { group ->
            group.clearCheck()
            ids.forEach { group.check(it) }
        }
    }
}