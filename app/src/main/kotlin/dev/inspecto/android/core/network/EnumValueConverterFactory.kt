package dev.inspecto.android.core.network

import kotlinx.serialization.SerialName
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Writes enum values of headers, paths and query parameters as their contract value
 * ({@code @SerialName}), for example {@code ru} instead of the constant name {@code RU}. The
 * generated enums do not override {@code toString()}, which Retrofit would use otherwise.
 */
class EnumValueConverterFactory : Converter.Factory() {
    override fun stringConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, String>? {
        val enumClass = type as? Class<*>
        if (enumClass == null || !enumClass.isEnum) {
            return null
        }
        return Converter<Enum<*>, String> { value -> contractValue(value) }
    }

    private fun contractValue(value: Enum<*>): String =
        value.javaClass
            .getField(value.name)
            .getAnnotation(SerialName::class.java)
            ?.value ?: value.name
}
