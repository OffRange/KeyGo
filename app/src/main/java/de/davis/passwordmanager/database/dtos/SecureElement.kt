package de.davis.passwordmanager.database.dtos

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.TypedValue
import com.amulyakhare.textdrawable.TextDrawable
import com.amulyakhare.textdrawable.util.ColorGenerator
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.entities.SecureElementEntity
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.Timestamps
import de.davis.passwordmanager.database.entities.details.ElementDetail
import de.davis.passwordmanager.database.entities.wrappers.CombinedElement
import de.davis.passwordmanager.gson.annotations.ExcludeFromGson
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import net.greypanther.natsort.SimpleNaturalComparator
import java.util.Date

private object TagParceler : Parceler<Tag> {
    override fun create(parcel: Parcel): Tag {
        return Tag(parcel.readString()!!, parcel.readLong())
    }

    override fun Tag.write(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeString(name)
            writeLong(tagId)
        }
    }
}

private object TimestampsParceler : Parceler<Timestamps> {
    override fun create(parcel: Parcel): Timestamps {
        return Timestamps(
            (parcel.readSerializable() as Date),
            parcel.readSerializable()?.let { it as Date })
    }

    override fun Timestamps.write(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeSerializable(createdAt)
            writeSerializable(modifiedAt)
        }
    }
}

@Parcelize
@TypeParceler<Tag, TagParceler>
@TypeParceler<Timestamps, TimestampsParceler>
data class SecureElement @JvmOverloads constructor(
    var title: String,
    var detail: ElementDetail,
    var tags: List<Tag> = listOf(),
    var favorite: Boolean = false,
    private var timestamps: Timestamps = Timestamps.CURRENT,
    @ExcludeFromGson override val id: Long = 0
) : Item, Comparable<SecureElement>, Parcelable {

    init {
        tags += detail.elementType.tag
    }

    val letter get() = title[0].uppercaseChar()
    val elementType: ElementType get() = detail.elementType

    fun toEntity(): CombinedElement = run {
        return CombinedElement(SecureElementEntity(title, detail, favorite, timestamps, id), tags)
    }

    fun getIcon(context: Context): Drawable {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            context.resources.displayMetrics
        ).toInt()

        return TextDrawable.builder()
            .beginConfig().bold().endConfig()
            .roundRect(px)
            .build(
                title.substring(0, if (title.length >= 2) 2 else 1),
                ColorGenerator.MATERIAL.getColor(title)
            )
    }

    override fun compareTo(other: SecureElement): Int {
        return SimpleNaturalComparator.getInstance<CharSequence>().compare(
            title.lowercase(),
            other.title.lowercase()
        )
    }

    companion object {
        @JvmStatic
        fun fromEntity(combinedElement: CombinedElement): SecureElement = combinedElement.run {
            val secureElement = secureElementEntity.run {
                SecureElement(title, detail, combinedElement.tags, favorite, timestamps, id)
            }
            return@run secureElement
        }
    }
}