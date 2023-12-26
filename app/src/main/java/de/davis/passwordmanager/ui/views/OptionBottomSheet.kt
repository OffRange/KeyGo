package de.davis.passwordmanager.ui.views

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputLayout
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.SecureElementManager.switchFavStateCoroutine
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.database.entities.getLocalizedName
import de.davis.passwordmanager.database.entities.isProtectedTagName
import de.davis.passwordmanager.databinding.MoreBottomSheetContentBinding
import de.davis.passwordmanager.dialog.DeleteDialog
import de.davis.passwordmanager.dialog.EditDialogBuilder
import de.davis.passwordmanager.ktx.capitalize
import de.davis.passwordmanager.manager.ActivityResultManager
import de.davis.passwordmanager.ui.dashboard.DashboardFragment
import kotlinx.coroutines.launch

@Suppress("FunctionName")
inline fun <reified I : Item> OptionBottomSheet(items: List<I>): BottomSheetDialogFragment =
    OptionBottomSheet(items, items[0]::class.java)


class OptionBottomSheet<out I : Item>(
    private val items: List<I>,
    private val iClass: Class<out I>
) :
    BottomSheetDialogFragment() {

    private lateinit var binding: MoreBottomSheetContentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return MoreBottomSheetContentBinding.inflate(layoutInflater).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (items.isEmpty()) return

        tryViewCreatedForElement()
        tryViewCreatedForTag()

        binding.delete.setOnClickListener {
            DeleteDialog(context).show(items)
            dismiss()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified E> tryCast(): List<E>? {
        return if (iClass == E::class.java) (items as List<E>) else null
    }

    private fun tryViewCreatedForElement() = tryCast<SecureElement>()?.let { list ->
        val firstElement: SecureElement = list[0]
        setTitle(list) { it.title }

        if (items.size > 1) {
            binding.edit.visibility = View.GONE
            binding.favorite.visibility = View.GONE
        } else {
            binding.edit.setOnClickListener {
                ActivityResultManager.getOrCreateManager(DashboardFragment::class.java, null)
                    .launchEdit(firstElement, context)
                dismiss()
            }
            binding.favorite.setOnClickListener {
                switchFavStateCoroutine(firstElement)
                dismiss()
            }
            binding.favorite.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (firstElement.favorite) R.drawable.baseline_star_24 else R.drawable.baseline_star_outline_24,
                0, 0, 0
            )
            binding.favorite.setText(if (firstElement.favorite) R.string.remove_from_favorite else R.string.mark_as_favorite)
        }
    }

    private fun tryViewCreatedForTag() = tryCast<TagWithCount>()?.let { list ->
        val title = setTitle(list) { it.tag.getLocalizedName(requireContext()) }
        binding.favorite.visibility = View.GONE

        if (items.size > 1) {
            binding.edit.text = getString(R.string.merge_tag)

            binding.edit.setOnClickListener {
                dismiss()

                EditDialogBuilder(requireContext()).apply {
                    setTitle(R.string.merge_tag)
                    withInformation(InformationView.Information().apply {
                        text = list.first().tag.name
                        hint = getString(R.string.tag)
                        inputType = InputType.TYPE_CLASS_TEXT
                        isSecret = false
                    })
                    setButtonListener(
                        DialogInterface.BUTTON_POSITIVE,
                        R.string.ok
                    ) { dialog, _, newText ->
                        if (!checkInput(newText, dialog as AlertDialog))
                            return@setButtonListener


                        lifecycleScope.launch {
                            SecureElementManager.mergeTags(list.map { it.tag }, newText)

                            dialog.dismiss()
                        }
                    }
                }.show()
            }
        } else {
            val firstTag = list.first()
            binding.edit.setOnClickListener {
                dismiss()
                EditDialogBuilder(requireContext()).apply {
                    setTitle(R.string.edit_tag)
                    setButtonListener(
                        DialogInterface.BUTTON_POSITIVE,
                        R.string.ok
                    ) { dialog, _, newText ->
                        if (!checkInput(newText, dialog as AlertDialog))
                            return@setButtonListener

                        if (newText == firstTag.tag.name) {
                            dialog.dismiss()
                            return@setButtonListener
                        }

                        lifecycleScope.launch {
                            val updatedRows =
                                SecureElementManager.updateTag(
                                    firstTag.tag.copy(
                                        name = newText.trim().capitalize()
                                    )
                                )
                            if (updatedRows != 0) {
                                dialog.dismiss()
                                return@launch
                            }

                            val inputLayout =
                                dialog.findViewById<TextInputLayout>(R.id.textInputLayout)!!
                            inputLayout.error = context.getString(R.string.tag_already_existed)
                        }
                    }

                    withInformation(InformationView.Information().apply {
                        text = title
                        hint = getString(R.string.tag)
                        inputType = InputType.TYPE_CLASS_TEXT
                        isSecret = false
                    })
                }.show()
            }
        }
    }

    private fun EditDialogBuilder.checkInput(input: String, dialog: AlertDialog): Boolean {
        val inputLayout = dialog.findViewById<TextInputLayout>(R.id.textInputLayout)!!
        if (input.isBlank()) {
            inputLayout.error = context.getString(R.string.tag_cant_be_blank)
            inputLayout.editText?.text?.clear()
            return false
        }

        if (input.isProtectedTagName) {
            inputLayout.error = context.getString(R.string.prefix_not_allowed)
            return false
        }

        return true
    }

    private fun <I : Item> setTitle(list: List<I>, titleProvider: (I) -> String): String {
        val title = if (list.size > 1) requireContext().getString(R.string.options)
        else titleProvider(list[0])
        binding.title.text = title

        return title
    }
}