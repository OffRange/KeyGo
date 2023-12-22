package de.davis.passwordmanager.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import de.davis.passwordmanager.R
import de.davis.passwordmanager.databinding.DialogFilterBinding
import de.davis.passwordmanager.databinding.LayoutChipBinding
import de.davis.passwordmanager.filter.Filter
import de.davis.passwordmanager.ktx.doFlowInLifecycle
import kotlinx.coroutines.flow.collectLatest

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

        binding.typeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            binding.strength.isEnabled = checkedIds.contains(R.id.password)

            Filter.updateFilter {
                password = if (checkedIds.contains(R.id.password)) {
                    getStrengthFilter(binding.strengthGroup.checkedChipIds)
                } else {
                    null
                }

                creditCard = checkedIds.contains(R.id.creditCard)
            }
        }
        binding.strengthGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            Filter.updateFilter {
                password = getStrengthFilter(checkedIds)
            }
        }

        //Tag chip logic
        binding.tagGroup.setOnCheckedStateChangeListener { _, selectedIds ->
            //This is used for auto select/deselect the default tag (All) when a other tag is selected/deselected
            binding.allTagsChip.isChecked = selectedIds.isEmpty()
        }

        binding.allTagsChip.setOnCheckedChangeListener { v, isChecked ->
            // Clear all other tags selection if the all tag is selected
            if (isChecked) {
                binding.tagGroup.clearCheck()
                Filter.updateFilter {
                    tags.clear()
                }
                return@setOnCheckedChangeListener
            }

            // Prevent tag from being always selected -> Only select if no tags are selected
            if (binding.tagGroup.checkedChipIds.size > 0)
                return@setOnCheckedChangeListener

            v.isChecked = true
        }

        viewLifecycleOwner.doFlowInLifecycle(Filter.tagsFlow) {
            collectLatest { tags ->
                val prevTags = Filter.filterFlow.value.tags
                val onlyOneTagAvailable = tags.size == 1
                tags.forEach {
                    val checked = prevTags.contains(it) || onlyOneTagAvailable
                    binding.tagGroup.addView(
                        createChip(
                            it,
                            checked,
                            staySelected = onlyOneTagAvailable
                        )
                    )
                    if (checked)
                        binding.allTagsChip.isChecked = false
                }

                if (onlyOneTagAvailable)
                    binding.allTagsChip.visibility = View.GONE

            }
        }

        /*lifecycleScope.launch {
            val tags = SecureElementManager.getTags().onlyCustoms().map { it.name }
            //this@FilterBottomSheet.tags.addAll(tags)
            val prevTags = FilterV2.filterFlow.value.tags

            tags.forEach {
                val checked = prevTags.contains(it)
                binding.tagGroup.addView(createChip(it, checked))
                if (checked)
                    binding.allTagsChip.isChecked = false
            } //TODO there is a bug with the filter -> when selecting all tags the "all" tag should be selected
        }*/
    }

    private fun getStrengthFilter(checkedIds: List<Int>): Filter.StrengthFilter {
        return Filter.StrengthFilter(
            veryStrong = checkedIds.contains(R.id.veryStrong),
            strong = checkedIds.contains(R.id.strong),
            moderate = checkedIds.contains(R.id.moderate),
            weak = checkedIds.contains(R.id.weak),
            ridiculous = checkedIds.contains(R.id.ridiculous)
        )
    }

    private fun createChip(tag: String, checked: Boolean, staySelected: Boolean): Chip {
        return LayoutChipBinding.inflate(layoutInflater).root.apply {
            isChecked = checked
            text = tag

            setOnCheckedChangeListener { _, isChecked ->
                if (staySelected) {
                    this.isChecked = true
                    return@setOnCheckedChangeListener
                }
                if (isChecked)
                    tags.add(text.toString())
                else
                    tags.remove(text.toString())

                Filter.updateFilter {
                    if (isChecked) {
                        tags += text.toString()
                    } else
                        tags -= text.toString()
                }
            }
        }
    }
}