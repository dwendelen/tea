package se.daan.tea.api

import kotlinx.serialization.*

typealias FlavourId = Int
@Serializable
data class Flavour(
    val id: FlavourId,
    val name: String
)

typealias ProductId = Int

@Serializable
data class Product(
    val id: ProductId,
    val name: String,
    val flavourId: FlavourId,
    val boxQuantity: Int
)

typealias MeasurementId = Int
@Serializable
data class Measurement(
    val id: MeasurementId,
    val date: String,
    val measurements: List<ProductMeasurement>
)

@Serializable
data class ProductMeasurement(
    val productId: ProductId,
    val bakje: Int,
    val boxesInStock: Int,
    val looseInStock: Int
)