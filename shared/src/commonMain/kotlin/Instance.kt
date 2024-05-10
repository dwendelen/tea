package se.daan.tea.api

import kotlinx.serialization.*

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
    val flavour: FlavourId,
): VersionedEntity

typealias MeasurementId = Int
@Serializable
data class Measurement(
    val id: MeasurementId,
    val version: Version,
    val date: String,
    val measurements: List<ProductMeasurement>
): VersionedEntity

@Serializable
data class ProductMeasurement(
    val product: ProductId,
    val tray: Int,
    val boxes: Int,
    val loose: Int
)