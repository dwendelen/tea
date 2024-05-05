package se.daan.tea.web.model

class Application {
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

    fun newProduct(name: String, flavour: FlavourVersion): ProductVersion {
        val productVersion = ProductVersion(
            versionStream.nextId<ProductVersion>(),
            versionStream.nextVersion,
            0f,
            name,
            flavour
        )
        versionStream.upsert(productVersion)
        return productVersion
    }

    fun updateProduct(productVersion: ProductVersion, name: String, flavour: FlavourVersion): ProductVersion {
        val newVersion = ProductVersion(
            productVersion.id,
            versionStream.nextVersion,
            0f,
            name,
            flavour
        )
        versionStream.upsert(newVersion)
        return newVersion
    }

    val measurements: List<MeasurementVersion>
        get() = versionStream.getCurrentAll<MeasurementVersion>()
            .values
            .sortedBy { it.id }

    fun newMeasurement(date: String, measurements: List<MeasurementData>): MeasurementVersion {
        val version = versionStream.nextVersion
        val prodId = versionStream.nextId<ProductMeasurementVersion>()
        val measurementVersions = measurements.mapIndexed { idx, m ->
            ProductMeasurementVersion(
                prodId + idx,
                version,
                m.productVersion,
                m.tray,
                m.boxes,
                m.loose
            )
        }
        val measurement = MeasurementVersion(
            versionStream.nextId<MeasurementVersion>(),
            version,
            date,
            measurementVersions
        )
        versionStream.upsert(measurement)
        return measurement
    }
}

data class MeasurementData(
    val productVersion: ProductVersion,
    val tray: Int,
    val boxes: Int,
    val loose: Int
)

interface EntityVersion {
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
    val flavour: FlavourVersion
): EntityVersion
data class MeasurementVersion(
    override val id: Int,
    override val version: Int,
    val date: String,
    val measurements: List<ProductMeasurementVersion>
): EntityVersion
data class ProductMeasurementVersion(
    override val id: Int,
    override val version: Int,
    val productVersion: ProductVersion,
    val tray: Int,
    val boxes: Int,
    val loose: Int
): EntityVersion

class VersionStream {
    val versionStream = mutableListOf<EntityVersion>()
    var nextVersion = 0
    private var listeners: List<(EntityVersion) -> Unit> = emptyList()

    inline fun <reified T: EntityVersion> getCurrent(id: Int): T? {
        return versionStream
            .filterIsInstance<T>()
            .lastOrNull { it.id == id }
    }

    inline fun <reified T: EntityVersion> getCurrentAll(): Map<Int, T> {
        return versionStream
            .filterIsInstance<T>()
            .groupBy { it.id }
            .mapValues { it.value.last() }
    }

    fun upsert(entity: EntityVersion) {
        if(entity.version != nextVersion) {
            throw IllegalStateException()
        }
        versionStream.add(entity)
        listeners.forEach {
            it(entity)
        }
    }

    fun upsert(entities: List<EntityVersion>) {
        for (entity in entities) {
            if(entity.version != nextVersion) {
                throw IllegalStateException()
            }
        }
        versionStream.addAll(entities)
    }

    inline fun <reified T: EntityVersion> nextId(): Int {
        return versionStream
            .filterIsInstance<T>()
            .lastOrNull()
            ?.let { it.id + 1}
            ?:0
    }

    fun onUpsert(fn: (EntityVersion) -> Unit) {
        listeners = listeners + fn
    }
}
