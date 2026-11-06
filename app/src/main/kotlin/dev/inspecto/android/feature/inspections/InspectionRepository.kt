package dev.inspecto.android.feature.inspections

import dev.inspecto.android.api.apis.ChecklistApi
import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.apis.InspectionsApi
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.ChecklistTemplate
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.CreateInspectionRequest
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.DefectDetails
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.InspectionSummary
import dev.inspecto.android.api.models.UpdateChecklistItemRequest
import dev.inspecto.android.api.models.UpdateInspectionRequest
import dev.inspecto.android.api.models.VersionOnly
import dev.inspecto.android.core.error.apiCall
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** What the inspector chose on the "new inspection" screen. */
data class NewInspection(
    val roomIds: List<UUID>,
    val templateCodes: List<String>,
    val title: String,
    val inspectedAt: OffsetDateTime,
)

/** Inspections, checklists, defects and dictionaries (platform/api/core.yaml). */
class InspectionRepository
    @Inject
    constructor(
        private val inspections: InspectionsApi,
        private val checklist: ChecklistApi,
        private val defects: DefectsApi,
        private val dictionaries: DictionariesApi,
    ) {
        /** Inspection history of a property, newest first. */
        suspend fun list(propertyId: UUID): List<InspectionSummary> =
            apiCall { inspections.listInspections(propertyId, page = 0, size = PAGE_SIZE) }.items

        suspend fun templates(): List<ChecklistTemplate> =
            apiCall {
                dictionaries.listChecklistTemplates(
                    DictionariesApi.AcceptLanguageListChecklistTemplates.valueOf(language()),
                )
            }

        suspend fun categories(): List<DefectCategory> =
            apiCall {
                dictionaries.listDefectCategories(
                    DictionariesApi.AcceptLanguageListDefectCategories.valueOf(language()),
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
                    InspectionsApi.AcceptLanguageCreateInspection.valueOf(language()),
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

        suspend fun setResult(
            item: ChecklistItem,
            result: ChecklistItem.Result,
        ): ChecklistItem =
            apiCall {
                checklist.updateChecklistItem(
                    item.id,
                    UpdateChecklistItemRequest(
                        version = item.version,
                        result = UpdateChecklistItemRequest.Result.valueOf(result.name),
                    ),
                )
            }

        suspend fun createDefect(
            inspectionId: UUID,
            request: CreateDefectRequest,
        ): DefectDetails = apiCall { defects.createDefect(inspectionId, request) }

        private companion object {
            /** The API allows up to 100 items per page; a property has far fewer inspections. */
            const val PAGE_SIZE = 100

            /** Language of dictionary names and checklist titles: English if the device uses it, else Russian. */
            fun language(): String = if (Locale.getDefault().language == "en") "EN" else "RU"
        }
    }
