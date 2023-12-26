package de.davis.passwordmanager.ui.views

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView.OnItemClickListener
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doBeforeTextChanged
import com.google.android.material.chip.Chip
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.isProtectedTagName
import de.davis.passwordmanager.database.entities.onlyCustoms
import de.davis.passwordmanager.database.entities.shouldBeProtected
import de.davis.passwordmanager.databinding.LayoutTagViewBinding
import de.davis.passwordmanager.ktx.capitalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class TagView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet?,
    defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr) {

    private val binding: LayoutTagViewBinding

    private val whitespace = Regex("\\s+")
    private var ignore: Boolean = false
    private var editable: Boolean = true


    private val _tags = linkedMapOf<String, Chip>()
    val tags get() = _tags.keys.map { Tag(it) }

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        binding = LayoutTagViewBinding.inflate(LayoutInflater.from(context), this, true)

        context.obtainStyledAttributes(attributeSet, R.styleable.TagView, defStyleAttr, 0)
            .apply {
                val typeId =
                    getInteger(R.styleable.TagView_defaultTag, -1)

                if (typeId > -1)
                    addDefaultElementTag(ElementType.getTypeByTypeId(typeId))

                setEditable(getBoolean(R.styleable.TagView_editable, editable))


                recycle()
            }

        binding.tagInput.run {
            doBeforeTextChanged { _, _, count, after ->
                if (count < after)
                    handleLastChip(false)
            }

            filters += InputFilter { source, _, _, dest, _, _ ->
                if (source.isBlank() && dest.isProtectedTagName)
                    return@InputFilter ""

                return@InputFilter null
            }

            doAfterTextChanged { editable ->
                if (ignore)
                    return@doAfterTextChanged

                editable?.run {
                    if (isProtectedTagName) {
                        binding.textInputLayout.error =
                            context.getString(R.string.prefix_not_allowed)
                        binding.textInputLayout.isErrorEnabled = true
                        return@doAfterTextChanged
                    }

                    binding.textInputLayout.isErrorEnabled = false

                    ignore = true
                    handleInput(this)
                    ignore = false
                }
            }

            setOnEditorActionListener { v, actionId, _ ->
                return@setOnEditorActionListener when (actionId) {
                    EditorInfo.IME_ACTION_GO -> {
                        handleInput(v.editableText, true)
                        true
                    }

                    else -> false
                }
            }

            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
                return@setOnKeyListener when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        if (text?.isNotEmpty() == true) return@setOnKeyListener false
                        handleLastChip(true)
                        true
                    }

                    else -> false
                }
            }

            scope.launch {
                val tags = SecureElementManager.getTags()
                    .onlyCustoms()
                    .map { it.name }
                    .toTypedArray()

                withContext(Dispatchers.Main) {
                    binding.tagInput.setSimpleItems(tags)
                }
            }

            binding.tagInput.onItemClickListener = OnItemClickListener { _, _, _, _ ->
                handleInput(text, true)
            }

        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    private fun setEditable(editable: Boolean) {
        this.editable = editable

        binding.textInputLayout.visibility = if (editable) View.VISIBLE else View.GONE
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        binding.root.setOnLongClickListener(l)
    }

    private fun handleInput(editable: Editable, force: Boolean = false) = editable.run {
        if (!contains(whitespace) && !force)
            return@run

        if (isProtectedTagName)
            return@run


        val tags = split(whitespace).filter { it.isNotBlank() }
        tags.forEach { addTag(Tag(it)) }
        editable.delete(0, length)
    }

    private fun createChip(tag: String, removable: Boolean): Chip {
        return Chip(
            context,
            null,
            com.google.android.material.R.style.Widget_Material3_Chip_Input
        ).apply {
            text = tag
            isCloseIconVisible = removable
            if (removable)
                setOnCloseIconClickListener {
                    removeTag(tag)
                }
        }
    }

    private fun addDefaultElementTag(elementType: ElementType) {
        addTag(elementType.tag, context.getString(elementType.title), false)
    }

    @JvmOverloads
    fun setTags(tags: List<Tag>, removable: Boolean = false) {
        removeCustomTags()
        tags.onlyCustoms().forEach { addTag(it, removable = removable) }
    }

    private fun removeCustomTags() {
        val iterator = _tags.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.isProtectedTagName)
                continue

            binding.chipGroup.removeView(entry.value)
            iterator.remove()
        }
    }

    private fun addTag(tag: Tag, text: String = tag.name, removable: Boolean = true) {
        val key = if (tag.shouldBeProtected) tag.name else text.capitalize()
        if (_tags.containsKey(key))
            return

        val chip = createChip(text.capitalize(), removable)
        _tags[key] = chip
        binding.chipGroup.apply { addView(chip) }
    }

    private fun removeTag(tag: String) {
        binding.chipGroup.removeView(_tags.remove(tag.capitalize()))
    }

    private fun handleLastChip(isDeleting: Boolean) {
        val chip: Chip = binding.chipGroup.children.lastOrNull() as? Chip ?: return

        if (!chip.isCloseIconVisible)
            return

        if (!isDeleting) {
            chip.isSelected = false
            return
        }

        if (chip.isSelected) {
            removeTag(chip.text.toString())
        } else {
            chip.isSelected = true
        }
    }
}