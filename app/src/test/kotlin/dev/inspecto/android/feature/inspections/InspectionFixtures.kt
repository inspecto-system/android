package dev.inspecto.android.feature.inspections

import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.ChecklistTemplate
import dev.inspecto.android.api.models.Defect
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.InspectionRoom
import dev.inspecto.android.api.models.ObservationWithDefect
import dev.inspecto.android.api.models.Room
import java.time.OffsetDateTime
import java.util.UUID

/** Test data shaped like answers of core-service. */
object InspectionFixtures {
    val now: OffsetDateTime = OffsetDateTime.parse("2026-11-06T10:00:00Z")
    val propertyId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val inspectionId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val kitchen: Room = room("00000000-0000-0000-0000-0000000000a1", "Кухня", 0)
    val bedroom: Room = room("00000000-0000-0000-0000-0000000000a2", "Спальня", 1)
    val cracks: DefectCategory =
        DefectCategory(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000c1"),
            name = "Трещины",
            system = true,
            active = true,
            elementGroup = DefectCategory.ElementGroup.WALLS,
        )
    val doorCategory: DefectCategory =
        DefectCategory(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000c2"),
            name = "Двери не закрываются",
            system = true,
            active = true,
            elementGroup = DefectCategory.ElementGroup.DOORS,
        )
    val standard = ChecklistTemplate(UUID.randomUUID(), "ROOM_STANDARD", "Стандартный", emptyList())
    val wet = ChecklistTemplate(UUID.randomUUID(), "WET_ROOM", "Влажное помещение", emptyList())

    fun room(
        id: String,
        name: String,
        order: Int,
    ) = Room(UUID.fromString(id), propertyId, name, Room.RoomType.LIVING, order, 0)

    fun item(
        title: String,
        room: Room = kitchen,
        result: ChecklistItem.Result = ChecklistItem.Result.NOT_CHECKED,
    ) = ChecklistItem(
        id = UUID.nameUUIDFromBytes(title.toByteArray()),
        inspectionId = inspectionId,
        roomId = room.id,
        elementGroup = ChecklistItem.ElementGroup.WALLS,
        title = title,
        result = result,
        custom = false,
        sortOrder = 0,
        version = 0,
    )

    fun observation(
        item: ChecklistItem?,
        title: String,
    ): ObservationWithDefect {
        val defectId = UUID.nameUUIDFromBytes(title.toByteArray())
        return ObservationWithDefect(
            id = UUID.randomUUID(),
            defectId = defectId,
            inspectionId = inspectionId,
            status = ObservationWithDefect.Status.OPEN,
            severity = ObservationWithDefect.Severity.MAJOR,
            observedBy = UUID.randomUUID(),
            photos = emptyList(),
            createdAt = now,
            version = 0,
            defect =
                Defect(
                    id = defectId,
                    propertyId = propertyId,
                    roomId = kitchen.id,
                    categoryId = cracks.id,
                    elementGroup = Defect.ElementGroup.WALLS,
                    title = title,
                    severity = Defect.Severity.MAJOR,
                    currentStatus = Defect.CurrentStatus.OPEN,
                    firstInspectionId = inspectionId,
                    createdAt = now,
                    updatedAt = now,
                    version = 0,
                ),
            checklistItemId = item?.id,
        )
    }

    fun details(
        status: InspectionDetails.Status = InspectionDetails.Status.IN_PROGRESS,
        kitchenItems: List<ChecklistItem> = listOf(item("Стены без трещин")),
        observations: List<ObservationWithDefect> = emptyList(),
    ) = InspectionDetails(
        id = inspectionId,
        propertyId = propertyId,
        inspectorId = UUID.randomUUID(),
        inspectedAt = now,
        status = status,
        createdAt = now,
        updatedAt = now,
        version = 3,
        rooms =
            listOf(
                InspectionRoom(kitchen, kitchenItems, observations),
                InspectionRoom(bedroom, listOf(item("Окно открывается", bedroom)), emptyList()),
            ),
    )

    fun inspection(status: Inspection.Status) =
        Inspection(
            id = inspectionId,
            propertyId = propertyId,
            inspectorId = UUID.randomUUID(),
            inspectedAt = now,
            status = status,
            createdAt = now,
            updatedAt = now,
            version = 4,
            completedAt = if (status == Inspection.Status.COMPLETED) now else null,
        )
}
