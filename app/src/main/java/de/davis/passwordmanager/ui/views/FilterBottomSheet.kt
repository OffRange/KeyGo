package de.davis.passwordmanager.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.entities.onlyCustoms
import de.davis.passwordmanager.databinding.DialogFilterBinding
import de.davis.passwordmanager.databinding.LayoutChipBinding
import de.davis.passwordmanager.filter.Filter
import kotlinx.coroutines.launch

class FilterBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: DialogFilterBinding

    private val tags = mutableSetOf<String>()

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

        Filter.DEFAULT.apply {
            setStrength(binding.strengthGroup)
            setType(binding.typeGroup)
        }

        updateSelection(binding.strengthGroup, binding.typeGroup)

        binding.typeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            binding.strength.isEnabled = checkedIds.contains(R.id.password)
            Filter.DEFAULT.update()
        }
        binding.strengthGroup.setOnCheckedStateChangeListener { _, _ -> Filter.DEFAULT.update() }

        //Tag chip logic
        binding.tagGroup.setOnCheckedStateChangeListener { _, selectedIds ->
            //This is used for auto select/deselect the default tag (All) when a other tag is selected/deselected
            binding.allTagsChip.isChecked = selectedIds.isEmpty()
        }

        binding.allTagsChip.setOnCheckedChangeListener { v, isChecked ->
            // Clear all other tags selection if the all tag is selected
            if (isChecked) {
                binding.tagGroup.clearCheck()
                return@setOnCheckedChangeListener
            }

            // Prevent tag from being always selected -> Only select if no tags are selected
            if (binding.tagGroup.checkedChipIds.size > 0)
                return@setOnCheckedChangeListener

            v.isChecked = true
        }

        lifecycleScope.launch {
            val tags = SecureElementManager.getTags().onlyCustoms().map { it.name }
            this@FilterBottomSheet.tags.addAll(tags)
            val prevTags = Filter.DEFAULT.tags
            Filter.DEFAULT.tags = tags
            tags.forEach {
                val checked = prevTags.contains(it)
                binding.tagGroup.addView(createChip(it, checked))
                if (checked)
                    binding.allTagsChip.isChecked = false
            }
        }
    }

    private fun createChip(tag: String, checked: Boolean): Chip {
        return LayoutChipBinding.inflate(layoutInflater).root.apply {
            isChecked = checked
            text = tag

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked)
                    tags.add(text.toString())
                else
                    tags.remove(text.toString())

                Filter.DEFAULT.apply {
                    tags = this@FilterBottomSheet.tags.toList()
                    update()
                }
            }
        }
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