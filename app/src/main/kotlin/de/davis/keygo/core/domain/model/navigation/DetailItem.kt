package de.davis.keygo.core.domain.model.navigation

import android.os.Parcel
import android.os.Parcelable
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.alias.ItemIdNone
import de.davis.keygo.generated.item.VaultItemEnum

// Todo(b/378882434): Configuration changes will cause a crash as android can not save the state yet
// To fix this, we use Parcelable even this violates the concept of domain-layer logic
sealed interface DetailItem : Parcelable {
    data class Edit(val type: VaultItemEnum, val itemId: ItemId = ItemIdNone) : DetailItem {
        constructor(parcel: Parcel) : this(
            VaultItemEnum.entries[parcel.readInt()],
            parcel.readLong()
        )

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(type.ordinal)
            dest.writeLong(itemId)
        }

        companion object CREATOR : Parcelable.Creator<Edit> {
            override fun createFromParcel(parcel: Parcel): Edit {
                return Edit(parcel)
            }

            override fun newArray(size: Int): Array<Edit?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class View(val itemId: ItemId) : DetailItem {
        constructor(parcel: Parcel) : this(parcel.readLong())

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeLong(itemId)
        }

        companion object CREATOR : Parcelable.Creator<View> {
            override fun createFromParcel(parcel: Parcel): View {
                return View(parcel)
            }

            override fun newArray(size: Int): Array<View?> {
                return arrayOfNulls(size)
            }
        }
    }
}