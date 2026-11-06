package dev.inspecto.android.feature.inspections

import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.CreateObservationRequest
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.DefectDetails
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.OpenDefect
import dev.inspecto.android.api.models.UpdateObservationRequest
import dev.inspecto.android.core.error.apiCall
import java.util.UUID
import javax.inject.Inject

/** Defects, their observations and categories (platform/api/core.yaml, tags defects and dictionaries). */
class DefectRepository
    @Inject
    constructor(
        private val defects: DefectsApi,
        private val dictionaries: DictionariesApi,
    ) {
        suspend fun categories(): List<DefectCategory> =
            apiCall {
                dictionaries.listDefectCategories(
                    DictionariesApi.AcceptLanguageListDefectCategories.valueOf(apiLanguage()),
                )
            }

        suspend fun createDefect(
            inspectionId: UUID,
            request: CreateDefectRequest,
        ): DefectDetails = apiCall { defects.createDefect(inspectionId, request) }

        /** Defects open before this inspection, with the observation of this inspection if made. */
        suspend fun openDefects(inspectionId: UUID): List<OpenDefect> =
            apiCall { defects.listOpenDefectsForInspection(inspectionId) }

        /**
         * Records whether an earlier defect remains or is resolved in this inspection: a new
         * observation, or a changed status of the observation already made.
         */
        suspend fun recordObservation(
            inspectionId: UUID,
            openDefect: OpenDefect,
            status: Observation.Status,
        ): Observation {
            val current = openDefect.currentObservation
            return if (current == null) {
                apiCall {
                    defects.createObservation(
                        inspectionId,
                        CreateObservationRequest(
                            openDefect.defect.id,
                            CreateObservationRequest.Status.valueOf(status.name),
                        ),
                    )
                }
            } else {
                apiCall {
                    defects.updateObservation(
                        current.id,
                        UpdateObservationRequest(current.version, UpdateObservationRequest.Status.valueOf(status.name)),
                    )
                }
            }
        }
    }
