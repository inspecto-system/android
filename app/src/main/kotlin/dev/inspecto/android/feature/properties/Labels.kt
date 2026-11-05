package dev.inspecto.android.feature.properties

import androidx.annotation.StringRes
import dev.inspecto.android.R
import dev.inspecto.android.api.models.CreatePropertyRequest
import dev.inspecto.android.api.models.CreateRoomRequest

/** Display names of property types; the generated models share these constant names. */
@StringRes
fun propertyTypeLabel(type: String): Int =
    when (type) {
        CreatePropertyRequest.Type.APARTMENT.name -> R.string.property_type_apartment
        CreatePropertyRequest.Type.HOUSE.name -> R.string.property_type_house
        CreatePropertyRequest.Type.COMMERCIAL.name -> R.string.property_type_commercial
        else -> R.string.property_type_other
    }

/** Display names of room types. */
@StringRes
fun roomTypeLabel(type: String): Int =
    when (type) {
        CreateRoomRequest.RoomType.LIVING.name -> R.string.room_type_living
        CreateRoomRequest.RoomType.KITCHEN.name -> R.string.room_type_kitchen
        CreateRoomRequest.RoomType.BATHROOM.name -> R.string.room_type_bathroom
        CreateRoomRequest.RoomType.TOILET.name -> R.string.room_type_toilet
        CreateRoomRequest.RoomType.HALLWAY.name -> R.string.room_type_hallway
        CreateRoomRequest.RoomType.BALCONY.name -> R.string.room_type_balcony
        else -> R.string.room_type_other
    }
