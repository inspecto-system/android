package dev.inspecto.android.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.Part
import java.lang.reflect.Type
import java.util.UUID

/**
 * Writes identifiers in multipart form fields as plain text (`propertyId` of `POST /photos`,
 * platform/api/media.yaml). Without it the JSON converter would send `"…"` with quotes.
 */
class FormFieldConverterFactory : Converter.Factory() {
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? {
        if (type != UUID::class.java || parameterAnnotations.none { it is Part }) {
            return null
        }
        return Converter<UUID, RequestBody> { it.toString().toRequestBody(TEXT) }
    }

    private companion object {
        val TEXT = "text/plain; charset=utf-8".toMediaType()
    }
}
