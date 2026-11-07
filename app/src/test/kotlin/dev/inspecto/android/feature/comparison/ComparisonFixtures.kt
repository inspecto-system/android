package dev.inspecto.android.feature.comparison

import dev.inspecto.android.api.models.CategoryStateCounts
import dev.inspecto.android.api.models.ComparedDefect
import dev.inspecto.android.api.models.ComparedInspection
import dev.inspecto.android.api.models.InspectionComparison
import dev.inspecto.android.api.models.InspectionSummary
import dev.inspecto.android.api.models.StateCounts
import java.time.OffsetDateTime
import java.util.UUID

object ComparisonFixtures {
    val propertyId: UUID = UUID.randomUUID()
    private val kitchen: UUID = UUID.randomUUID()
    private val bedroom: UUID = UUID.randomUUID()
    private val category: UUID = UUID.randomUUID()

    fun inspection(
        date: String,
        title: String? = null,
    ): InspectionSummary {
        val at = OffsetDateTime.parse("${date}T09:00:00Z")
        return InspectionSummary(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            inspectorId = propertyId,
            inspectedAt = at,
            status = InspectionSummary.Status.COMPLETED,
            createdAt = at,
            updatedAt = at,
            version = 1,
            defectCount = 0,
            newDefectCount = 0,
            title = title,
        )
    }

    fun defect(
        title: String,
        state: ComparedDefect.State,
        inKitchen: Boolean = true,
        reopened: Boolean = false,
    ) = ComparedDefect(
        defectId = UUID.randomUUID(),
        state = state,
        title = title,
        severity = ComparedDefect.Severity.MAJOR,
        roomId = if (inKitchen) kitchen else bedroom,
        roomName = if (inKitchen) "Кухня" else "Спальня",
        categoryId = category,
        reopened = reopened,
    )

    fun comparison(
        base: InspectionSummary,
        target: InspectionSummary,
        defects: List<ComparedDefect>,
    ): InspectionComparison {
        fun count(state: ComparedDefect.State) = defects.count { it.state == state }
        val summary =
            StateCounts(
                new = count(ComparedDefect.State.NEW),
                resolved = count(ComparedDefect.State.RESOLVED),
                remaining = count(ComparedDefect.State.REMAINING),
                notChecked = count(ComparedDefect.State.NOT_CHECKED),
            )
        return InspectionComparison(
            base = ComparedInspection(base.id, base.inspectedAt),
            target = ComparedInspection(target.id, target.inspectedAt),
            summary = summary,
            defects = defects,
            bySeverity = mapOf("MAJOR" to summary),
            byCategory =
                listOf(
                    CategoryStateCounts(
                        summary.new,
                        summary.resolved,
                        summary.remaining,
                        summary.notChecked,
                        category,
                        "Трещина",
                    ),
                ),
        )
    }
}
