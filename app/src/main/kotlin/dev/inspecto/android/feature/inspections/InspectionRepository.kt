package dev.inspecto.android.feature.inspections

import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.apis.InspectionsApi
import dev.inspecto.android.api.models.ChecklistTemplate
import dev.inspecto.android.api.models.CreateInspectionRequest
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.InspectionSummary
import dev.inspecto.android.api.models.UpdateInspectionRequest
import dev.inspecto.android.api.models.VersionOnly
import dev.inspecto.android.core.error.apiCall
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Language of dictionary names and checklist titles: English if the device uses it, else Russian. */
internal fun apiLanguage(): String = if (Locale.getDefault().language == "en") "EN" else "RU"

/** What the inspector chose on the "new inspection" screen. */
data class NewInspection(
    val roomIds: List<UUID>,
    val templateCodes: List<String>,
    val title: String,
    val inspectedAt: OffsetDateTime,
)

/** Inspections and checklist templates (platform/api/core.yaml). */
class InspectionRepository
    @Inject
    constructor(
        private val inspections: InspectionsApi,
        private val dictionaries: DictionariesApi,
    ) {
        /** Inspection history of a property, newest first. */
        suspend fun list(propertyId: UUID): List<InspectionSummary> =
            apiCall { inspections.listInspections(propertyId, page = 0, size = PAGE_SIZE) }.items

        suspend fun templates(): List<ChecklistTemplate> =
            apiCall {
                dictionaries.listChecklistTemplates(
                    DictionariesApi.AcceptLanguageListChecklistTemplates.valueOf(apiLanguage()),
                )
            }

        /** Creates a draft with a checklist for every selected room × template item. */
        suspend fun create(
            propertyId: UUID,
            draft: NewInspection,
        ): InspectionDetails =
            apiCall {
                inspections.createInspection(
                    propertyId,
                    CreateInspectionRequest(
                        inspectedAt = draft.inspectedAt,
                        roomIds = draft.roomIds.toSet(),
                        templateCodes = draft.templateCodes.toSet(),
                        title = draft.title.trim().ifEmpty { null },
                    ),
                    InspectionsApi.AcceptLanguageCreateInspection.valueOf(apiLanguage()),
                )
            }

        suspend fun details(inspectionId: UUID): InspectionDetails = apiCall { inspections.getInspection(inspectionId) }

        /** DRAFT → IN_PROGRESS. */
        suspend fun start(inspection: InspectionDetails): Inspection =
            apiCall {
                inspections.updateInspection(
                    inspection.id,
                    UpdateInspectionRequest(
                        version = inspection.version,
                        status = UpdateInspectionRequest.Status.IN_PROGRESS,
                    ),
                )
            }

        /** IN_PROGRESS → COMPLETED; the inspection cannot be changed afterwards. */
        suspend fun complete(inspection: InspectionDetails): Inspection =
            apiCall { inspections.completeInspection(inspection.id, VersionOnly(inspection.version)) }

        private companion object {
            /** The API allows up to 100 items per page; a property has far fewer inspections. */
            const val PAGE_SIZE = 100
        }
    }
