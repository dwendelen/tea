package se.daan.tea.api

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import se.daan.tea.api.LocalDateTime.Companion.DAYS_OFFSET

typealias Version = Int

@Serializable
sealed interface VersionedEntity {
    val version: Version
}

typealias FlavourId = Int
@Serializable
data class Flavour(
    val id: FlavourId,
    override val version: Version,
    val name: String
): VersionedEntity

typealias ProductId = Int
typealias BoxSize = Int
enum class ProductStatus {
    ACTIVE,
    DEPRECATED,
    INACTIVE
}
@Serializable
data class Product(
    val id: ProductId,
    override val version: Version,
    val name: String,
    val flavourId: FlavourId,
    val flavourVersion: Version,
    val boxSize: BoxSize,
    val status: ProductStatus,
    val supplierInfo: SupplierInfo?
): VersionedEntity

@Serializable
data class SupplierInfo(
    val name: String,
    val url: String?,
    val code: String?
)

typealias MeasurementId = Int
@Serializable
data class Measurement(
    val id: MeasurementId,
    override val version: Version,
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

typealias DeltaId = Int
@Serializable
data class Delta(
    val id: DeltaId,
    override val version: Version,
    val date: LocalDateTime,
    val deltas: List<ProductDelta>
): VersionedEntity

@Serializable
data class ProductDelta(
    val productId: ProductId,
    val productVersion: Version,
    val tray: Int,
    val boxes: Int,
    val loose: Int,
)

@Serializable
data class Tombstone(
    val id: Int,
    override val version: Version
): VersionedEntity

@Serializable(with = LocalDateTimeSerializer::class)
data class LocalDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
): Comparable<LocalDateTime> {
    override fun compareTo(other: LocalDateTime): Int {
        return if (year != other.year) {
            year - other.year
        } else if (month != other.month) {
            month - other.month
        } else if (day != other.day) {
            day - other.day
        } else if (hour != other.hour) {
            hour - other.hour
        } else if (minute != other.minute) {
            minute - other.minute
        } else {
            0
        }
    }

    override fun toString(): String {
        return "${year.toString().padStart(4, '0')}-" +
                "${month.toString().padStart(2, '0')}-" +
                "${day.toString().padStart(2, '0')}T" +
                "${hour.toString().padStart(2, '0')}:" +
                "${minute.toString().padStart(2, '0')}"
    }
    fun toHumanString(): String {
        return toHumanDateString() + " " +
                "${hour.toString().padStart(2, '0')}:" +
                "${minute.toString().padStart(2, '0')}"
    }
    fun toHumanDateString(): String {
        return "${year.toString().padStart(4, '0')}-" +
                "${month.toString().padStart(2, '0')}-" +
                "${day.toString().padStart(2, '0')}"
    }

    companion object {
        val DAYS_IN_MONTH = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val DAYS_OFFSET = listOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    }
}

fun minusDays(date: LocalDateTime, days: Int): LocalDateTime {
    return toLocalDateTime(date.year, toDayOfYear(date) - days)
        .copy(hour = date.hour, minute = date.minute)
}

fun plusDays(date: LocalDateTime, days: Int): LocalDateTime {
    return toLocalDateTime(date.year, toDayOfYear(date) + days)
        .copy(hour = date.hour, minute = date.minute)
}

/**
 * First day = 0
 */
private fun toDayOfYear(localDateTime: LocalDateTime): Int {
    return DAYS_OFFSET[localDateTime.month - 1] + (localDateTime.day - 1)
}
private tailrec fun toLocalDateTime(year: Int, dayOfYear: Int): LocalDateTime {
    if(dayOfYear < 0) {
        return toLocalDateTime(year - 1, dayOfYear + 365)
    } else if(dayOfYear >= 365) {
        return toLocalDateTime(year + 1, dayOfYear - 365)
    } else {
        DAYS_OFFSET.forEachIndexed { i, offset ->
            if(offset > dayOfYear) {
                val prevIdx = i - 1
                return LocalDateTime(year, prevIdx + 1, dayOfYear - DAYS_OFFSET[prevIdx] + 1,0, 0)
            }
        }
        return LocalDateTime(year, 12, dayOfYear - DAYS_OFFSET[11] + 1,0, 0)
    }
}

fun daysBetween(start: LocalDateTime, end: LocalDateTime): Int {
    return toDayOfYear(end) - toDayOfYear(start) + 365 * (end.year - start.year)
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

fun fromHumanString(string: String): LocalDateTime {
    val spaceSplit = string.split(" ")
    val dashSplit = spaceSplit[0].split("-")
    val colonSplit = spaceSplit[1].split(":")
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