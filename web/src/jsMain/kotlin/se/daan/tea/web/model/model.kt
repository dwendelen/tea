package se.daan.tea.web.model

import se.daan.tea.api.LocalDateTime
import se.daan.tea.api.ProductStatus
import se.daan.tea.api.Version
import kotlin.reflect.KClass

class Application() {
    val versionStream = VersionStream()

    val flavours: List<FlavourVersion>
        get() = versionStream.getCurrentAll<FlavourVersion>()
            .values
            .sortedBy { it.id }

    val products: List<ProductVersion>
        get() = versionStream.getCurrentAll<ProductVersion>()
            .values
            .sortedBy { it.id }

    val measurements: List<MeasurementVersion>
        get() = versionStream.getCurrentAll<MeasurementVersion>()
            .values
            .sortedBy { it.id }

    val deltas: List<DeltaVersion>
        get() = versionStream.getCurrentAll<DeltaVersion>()
            .values
            .sortedBy { it.id }

    val nextId: Int
        get() = versionStream.nextVersion

    val nextVersion: Version
        get() = versionStream.nextVersion

    fun upsert(entity: EntityVersion) {
        versionStream.upsert(entity)
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
            versionStream.nextId,
            versionStream.nextVersion,
            date,
            deltaVersions
        )
        versionStream.upsert(delta)
        return delta
    }
}

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
    var nextId = 0
    var state = mapOf<Int, EntityVersion>()
    private var listeners: List<(EntityVersion) -> Unit> = emptyList()

    inline fun <reified T: EntityVersion> get(id: Int, version: Int): T? {
        return versionStream
            .firstOrNull { it.id == id && it.version == version }
            as? T
    }

    inline fun <reified T: EntityVersion> getCurrentAll(): Map<Int, T> {
        return state
            .filter { it.value is T }
            .mapValues { it.value as T }
    }

    fun upsert(entity: EntityVersion) {
        if(entity.version != nextVersion) {
            throw IllegalStateException()
        }
        if(entity.id >= nextId) {
            nextId = entity.id + 1
        }
        nextVersion++
        versionStream.add(entity)
        state = state + (entity.id to entity)
        listeners.forEach {
            it(entity)
        }
    }

    fun onUpsert(fn: (EntityVersion) -> Unit) {
        listeners = listeners + fn
    }

    fun clearListeners() {
        listeners = emptyList()
    }
}
