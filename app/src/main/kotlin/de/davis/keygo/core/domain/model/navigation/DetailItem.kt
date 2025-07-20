package de.davis.keygo.core.domain.model.navigation

import android.os.Parcel
import android.os.Parcelable
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.alias.ItemIdNone
import de.davis.keygo.generated.item.VaultItemType

// Todo(b/378882434): Configuration changes will cause a crash as android can not save the state yet
// To fix this, we use Parcelable even this violates the concept of domain-layer logic
sealed interface DetailItem : Parcelable {
    data class Edit(val type: VaultItemType, val getBy: GetBy = GetBy.Id(ItemIdNone)) : DetailItem {
        constructor(parcel: Parcel) : this(
            VaultItemType.entries[parcel.readInt()],
            @Suppress("DEPRECATION")
            parcel.readParcelable(GetBy::class.java.classLoader) ?: GetBy.Id(ItemIdNone)
        )

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(type.ordinal)
            dest.writeParcelable(getBy, flags)
        }

        companion object CREATOR : Parcelable.Creator<Edit> {
            override fun createFromParcel(parcel: Parcel): Edit {
                return Edit(parcel)
            }

            override fun newArray(size: Int): Array<Edit?> {
                return arrayOfNulls(size)
            }

            fun byId(type: VaultItemType, id: ItemId): Edit {
                return Edit(type, GetBy.Id(id))
            }

            fun byTotpUri(uri: String): Edit {
                return Edit(VaultItemType.Password, GetBy.TotpUri(uri))
            }
        }

        sealed interface GetBy : Parcelable {
            data class Id(val itemId: ItemId) : GetBy {

                constructor(parcel: Parcel) : this(parcel.readLong())

                override fun describeContents(): Int = 0

                override fun writeToParcel(dest: Parcel, flags: Int) {
                    dest.writeLong(itemId)
                }

                companion object CREATOR : Parcelable.Creator<Id> {
                    override fun createFromParcel(parcel: Parcel): Id {
                        return Id(parcel)
                    }

                    override fun newArray(size: Int): Array<Id?> {
                        return arrayOfNulls(size)
                    }
                }
            }

            data class TotpUri(val uri: String) : GetBy {

                constructor(parcel: Parcel) : this(parcel.readString() ?: "")

                override fun describeContents(): Int = 0

                override fun writeToParcel(dest: Parcel, flags: Int) {
                    dest.writeString(uri)
                }

                companion object CREATOR : Parcelable.Creator<TotpUri> {
                    override fun createFromParcel(parcel: Parcel): TotpUri {
                        return TotpUri(parcel)
                    }

                    override fun newArray(size: Int): Array<TotpUri?> {
                        return arrayOfNulls(size)
                    }
                }
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