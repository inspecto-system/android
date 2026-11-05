package dev.inspecto.android.feature.properties

import dev.inspecto.android.api.apis.PropertiesApi
import dev.inspecto.android.api.apis.RoomsApi
import dev.inspecto.android.api.models.CreatePropertyRequest
import dev.inspecto.android.api.models.CreateRoomRequest
import dev.inspecto.android.api.models.Property
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.api.models.UpdateRoomRequest
import dev.inspecto.android.core.error.apiCall
import java.util.UUID
import javax.inject.Inject

/** Properties and rooms of the signed-in user (platform/api/core.yaml, tags properties and rooms). */
class PropertyRepository
    @Inject
    constructor(
        private val properties: PropertiesApi,
        private val rooms: RoomsApi,
    ) {
        /** Active properties, newest first, optionally filtered by name or address. */
        suspend fun list(query: String): List<Property> =
            apiCall {
                properties.listProperties(
                    page = 0,
                    size = PAGE_SIZE,
                    archived = false,
                    query = query.trim().ifEmpty { null },
                )
            }.items

        suspend fun create(request: CreatePropertyRequest): Property = apiCall { properties.createProperty(request) }

        suspend fun details(propertyId: UUID): PropertyDetails = apiCall { properties.getProperty(propertyId) }

        suspend fun addRoom(
            propertyId: UUID,
            name: String,
            type: CreateRoomRequest.RoomType,
        ): Room = apiCall { rooms.createRoom(propertyId, CreateRoomRequest(name.trim(), type)) }

        suspend fun renameRoom(
            room: Room,
            name: String,
        ): Room = apiCall { rooms.updateRoom(room.id, UpdateRoomRequest(version = room.version, name = name.trim())) }

        suspend fun deleteRoom(roomId: UUID) {
            apiCall { rooms.deleteRoom(roomId) }
        }

        private companion object {
            /** The API allows up to 100 items per page; an inspector has far fewer properties. */
            const val PAGE_SIZE = 100
        }
    }
