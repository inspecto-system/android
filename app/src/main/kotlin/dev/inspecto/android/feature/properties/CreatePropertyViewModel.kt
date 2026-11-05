package dev.inspecto.android.feature.properties

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.R
import dev.inspecto.android.api.models.CreatePropertyRequest
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class CreatePropertyState(
    val name: String = "",
    val type: CreatePropertyRequest.Type = CreatePropertyRequest.Type.APARTMENT,
    val address: String = "",
    val city: String = "",
    val area: String = "",
    val notes: String = "",
    @StringRes val nameError: Int? = null,
    @StringRes val addressError: Int? = null,
    @StringRes val areaError: Int? = null,
    @StringRes val errorRes: Int? = null,
    val submitting: Boolean = false,
    /** Set once the property has been created; the screen then opens it. */
    val createdId: UUID? = null,
)

@HiltViewModel
class CreatePropertyViewModel
    @Inject
    constructor(
        private val repository: PropertyRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(CreatePropertyState())
        val state: StateFlow<CreatePropertyState> = mutableState.asStateFlow()

        fun onNameChange(value: String) = mutableState.update { it.copy(name = value, nameError = null) }

        fun onTypeChange(value: CreatePropertyRequest.Type) = mutableState.update { it.copy(type = value) }

        fun onAddressChange(value: String) = mutableState.update { it.copy(address = value, addressError = null) }

        fun onCityChange(value: String) = mutableState.update { it.copy(city = value) }

        fun onAreaChange(value: String) = mutableState.update { it.copy(area = value, areaError = null) }

        fun onNotesChange(value: String) = mutableState.update { it.copy(notes = value) }

        fun save() {
            val form = state.value
            if (form.submitting) {
                return
            }
            val area = parseArea(form.area)
            val checked =
                form.copy(
                    nameError = if (form.name.isBlank()) R.string.validation_required else null,
                    addressError = if (form.address.isBlank()) R.string.validation_required else null,
                    areaError = if (form.area.isNotBlank() && area == null) R.string.validation_area else null,
                )
            mutableState.value = checked
            if (checked.nameError != null || checked.addressError != null || checked.areaError != null) {
                return
            }
            viewModelScope.launch {
                mutableState.update { it.copy(submitting = true, errorRes = null) }
                try {
                    val created = repository.create(form.toRequest(area))
                    mutableState.update { it.copy(submitting = false, createdId = created.id) }
                } catch (e: AppError) {
                    mutableState.update { it.copy(submitting = false, errorRes = e.messageRes()) }
                }
            }
        }

        private fun CreatePropertyState.toRequest(area: BigDecimal?) =
            CreatePropertyRequest(
                name = name.trim(),
                type = type,
                address = address.trim(),
                city = city.trim().ifEmpty { null },
                areaSqm = area,
                notes = notes.trim().ifEmpty { null },
            )

        internal companion object {
            private val MAX_AREA = BigDecimal("100000")

            /** Area in square metres with at most two decimals; a comma is accepted as separator. */
            fun parseArea(text: String): BigDecimal? {
                val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
                val valid = value.signum() >= 0 && value <= MAX_AREA && value.scale() <= 2
                return if (valid) value else null
            }
        }
    }
