package se.daan.tea.api

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias Version = Int

@Serializable
sealed interface VersionedEntity

typealias FlavourId = Int
@Serializable
data class Flavour(
    val id: FlavourId,
    val version: Version,
    val name: String
): VersionedEntity

typealias ProductId = Int
@Serializable
data class Product(
    val id: ProductId,
    val version: Version,
    val name: String,
    val flavourId: FlavourId,
    val flavourVersion: Version,
    val deprecated: Boolean,
): VersionedEntity

typealias MeasurementId = Int
@Serializable
data class Measurement(
    val id: MeasurementId,
    val version: Version,
    val date: LocalDateTime,
    val measurements: List<ProductMeasurement>
): VersionedEntity

@Serializable
data class ProductMeasurement(
    val productId: ProductId,
    val productVersion: Version,
    val tray: Int,
    val boxes: Int,
    val loose: Int,
)

@Serializable(with = LocalDateTimeSerializer::class)
data class LocalDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
) {
    override fun toString(): String {
        return "${year.toString().padStart(4, '0')}-" +
                "${month.toString().padStart(2, '0')}-" +
                "${day.toString().padStart(2, '0')}T" +
                "${hour.toString().padStart(2, '0')}:" +
                "${minute.toString().padStart(2, '0')}"
    }
}

fun fromString(string: String): LocalDateTime {
    val tSplit = string.split("T")
    val dashSplit = tSplit[0].split("-")
    val colonSplit = tSplit[1].split(":")
    return LocalDateTime(
        dashSplit[0].toInt(),
        dashSplit[1].toInt(),
        dashSplit[2].toInt(),
        colonSplit[0].toInt(),
        colonSplit[1].toInt()
    )
}

class LocalDateTimeSerializer: KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTimeSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val asString = decoder.decodeString()
        return fromString(asString)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }
}