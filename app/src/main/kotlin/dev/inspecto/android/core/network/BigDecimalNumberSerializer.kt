package dev.inspecto.android.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * Decimal numbers of the API (for example `areaSqm`) as JSON numbers, without loss of precision.
 * The adapter of the generated client writes them as JSON strings, which the contract does not
 * allow (`type: number`).
 */
object BigDecimalNumberSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.inspecto.android.BigDecimal", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: BigDecimal,
    ) {
        val json = encoder as? JsonEncoder ?: error("BigDecimal is supported only in JSON")
        json.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val json = decoder as? JsonDecoder ?: error("BigDecimal is supported only in JSON")
        return BigDecimal(json.decodeJsonElement().jsonPrimitive.content)
    }
}
