package dev.inspecto.android.feature.inspections

import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.OpenDefect
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

        /** Defects open before this inspection, with the observation of this inspection if made. */
        suspend fun openDefects(inspectionId: UUID): List<OpenDefect> =
            apiCall { defects.listOpenDefectsForInspection(inspectionId) }
    }
