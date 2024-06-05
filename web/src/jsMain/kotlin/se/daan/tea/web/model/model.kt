package se.daan.tea.web.model

import se.daan.tea.api.LocalDateTime
import se.daan.tea.api.ProductStatus
import kotlin.reflect.KClass

class Application() {
    val versionStream = VersionStream()

    val flavours: List<FlavourVersion>
        get() = versionStream.getCurrentAll<FlavourVersion>()
            .values
            .sortedBy { it.id }

    fun newFlavour(name: String): FlavourVersion {
        val flavour = FlavourVersion(
            versionStream.nextId<FlavourVersion>(),
            versionStream.nextVersion,
            0f,
            name
        )
        versionStream.upsert(flavour)
        return flavour
    }

    fun updateFlavour(flavour: FlavourVersion, name: String): FlavourVersion {
        val newFlavour = FlavourVersion(
            flavour.version,
            versionStream.nextVersion,
            0f,
            name
        )
        versionStream.upsert(newFlavour)
        return newFlavour
    }

    val products: List<ProductVersion>
        get() = versionStream.getCurrentAll<ProductVersion>()
            .values
            .sortedBy { it.id }

    fun newProduct(
        name: String,
        flavour: FlavourVersion,
        boxSize: Int,
        status: ProductStatus,
        supplierName: String?,
        supplierCode: String?,
        supplierUrl: String?,
    ): ProductVersion {
        val productVersion = ProductVersion(
            versionStream.nextId<ProductVersion>(),
            versionStream.nextVersion,
            0f,
            name,
            flavour,
            boxSize,
            status,
            supplierName?.let {
                SupplierData(it, supplierCode!!, supplierUrl!!)
            }
        )
        versionStream.upsert(productVersion)
        return productVersion
    }

    fun updateProduct(
        productVersion: ProductVersion,
        name: String,
        flavour: FlavourVersion,
        boxSize: Int,
        status: ProductStatus,
        supplierName: String?,
        supplierCode: String?,
        supplierUrl: String?,
    ): ProductVersion {
        val newVersion = ProductVersion(
            productVersion.id,
            versionStream.nextVersion,
            0f,
            name,
            flavour,
            boxSize,
            status,
            supplierName?.let {
                SupplierData(it, supplierCode!!, supplierUrl!!)
            }
        )
        versionStream.upsert(newVersion)
        return newVersion
    }

    val measurements: List<MeasurementVersion>
        get() = versionStream.getCurrentAll<MeasurementVersion>()
            .values
            .sortedBy { it.id }

    val deltas: List<DeltaVersion>
        get() = versionStream.getCurrentAll<DeltaVersion>()
            .values
            .sortedBy { it.id }

    fun newMeasurement(date: LocalDateTime, measurements: List<MeasurementData>): MeasurementVersion {
        val measurementVersions = measurements.map {  m ->
            ProductMeasurementVersion(
                m.productVersion,
                m.tray,
                m.boxes,
                m.loose
            )
        }
        val measurement = MeasurementVersion(
            versionStream.nextId<MeasurementVersion>(),
            versionStream.nextVersion,
            date,
            measurementVersions
        )
        versionStream.upsert(measurement)
        return measurement
    }

    fun newDelta(date: LocalDateTime, deltas: List<DeltaData>): DeltaVersion {
        val deltaVersions = deltas.map { pd ->
            ProductDeltaVersion(
                pd.productVersion,
                pd.tray,
                pd.boxes,
                pd.loose
            )
        }
        val delta = DeltaVersion(
            versionStream.nextId<DeltaVersion>(),
            versionStream.nextVersion,
            date,
            deltaVersions
        )
        versionStream.upsert(delta)
        return delta
    }
}

data class MeasurementData(
    val productVersion: ProductVersion,
    val tray: Int,
    val boxes: Int,
    val loose: Int
)

data class DeltaData(
    val productVersion: ProductVersion,
    val tray: Int,
    val boxes: Int,
    val loose: Int
)

sealed interface EntityVersion {
    val id: Int
    val version: Int
}

data class FlavourVersion(
    override val id: Int,
    override val version: Int,
    val order: Float,
    val name: String,
): EntityVersion

data class ProductVersion(
    override val id: Int,
    override val version: Int,
    val order: Float,
    val name: String,
    val flavour: FlavourVersion,
    val boxSize: Int,
    val status: ProductStatus,
    val supplierData: SupplierData?
): EntityVersion {
}

data class SupplierData(
    val name: String,
    val url: String?,
    val code: String?
)

data class MeasurementVersion(
    override val id: Int,
    override val version: Int,
    val date: LocalDateTime,
    val measurements: List<ProductMeasurementVersion>
): EntityVersion
data class ProductMeasurementVersion(
    val productVersion: ProductVersion,
    val tray: Int,
    val boxes: Int,
    val loose: Int
)

data class DeltaVersion(
    override val id: Int,
    override val version: Int,
    val date: LocalDateTime,
    val deltas: List<ProductDeltaVersion>
): EntityVersion
data class ProductDeltaVersion(
    val productVersion: ProductVersion,
    val tray: Int,
    val boxes: Int,
    val loose: Int
)

class VersionStream() {
    val versionStream = mutableListOf<EntityVersion>()
    var nextVersion = 0
    var state = mapOf<KClass<out EntityVersion>, Map<Int, EntityVersion>>()
    private var listeners: List<(EntityVersion) -> Unit> = emptyList()

    inline fun <reified T: EntityVersion> get(id: Int, version: Int): T? {
        return versionStream
            .filterIsInstance<T>()
            .firstOrNull { it.id == id && it.version == version }
    }

    inline fun <reified T: EntityVersion> getCurrent(id: Int): T? {
        return state[T::class]?.get(id) as T?
    }

    inline fun <reified T: EntityVersion> getCurrentAll(): Map<Int, T> {
        return state[T::class] as Map<Int, T>? ?: emptyMap()
    }

    fun upsert(entity: EntityVersion) {
        if(entity.version != nextVersion) {
            throw IllegalStateException()
        }
        nextVersion++
        versionStream.add(entity)
        val currentEntityMap = state[entity::class]?: emptyMap()
        val newEntityMap = currentEntityMap + (entity.id to entity)
        state = state + (entity::class to newEntityMap)
        listeners.forEach {
            it(entity)
        }
    }

    inline fun <reified T: EntityVersion> nextId(): Int {
        val maxId = (state[T::class] ?: emptyMap())
            .keys
            .maxOrNull()
            ?: -1
        return maxId + 1
    }

    fun onUpsert(fn: (EntityVersion) -> Unit) {
        listeners = listeners + fn
    }

    fun clearListeners() {
        listeners = emptyList()
    }
}
