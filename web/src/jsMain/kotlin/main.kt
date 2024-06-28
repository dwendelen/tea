import kotlinx.serialization.json.*

import kotlinx.browser.window
import kotlinx.dom.clear
import kotlinx.serialization.encodeToString
import org.w3c.fetch.*
import se.daan.tea.api.*
import se.daan.tea.web.model.*
import kotlin.js.Date

external object config {
    val api: String
}

fun main() {
    window.fetch("${config.api}/stream", RequestInit())
        .then { resp ->
            if (!resp.ok) {
                console.error(resp.status)
                console.error(resp.body)
                window.location.hash = "#/error"
            }
            resp.text()
        }
        .then { json ->
            val application = Application()

            Json.decodeFromString<List<VersionedEntity>>(json).forEach {
                val item = when(it) {
                    is Flavour -> FlavourVersion(
                        it.id,
                        it.version,
                        it.id.toFloat(),
                        it.name
                    )
                    is Product -> ProductVersion(
                        it.id,
                        it.version,
                        it.id.toFloat(),
                        it.name,
                        application.versionStream.get<FlavourVersion>(it.flavourId, it.flavourVersion)!!,
                        it.boxSize,
                        it.status,
                        it.supplierInfo?.let { s ->
                            SupplierData(
                                s.name,
                                s.url,
                                s.code
                            )
                        }
                    )
                    is Measurement -> MeasurementVersion(
                        it.id,
                        it.version,
                        it.date,
                        it.measurements.map { pm ->
                            ProductMeasurementVersion(
                                application.versionStream.get<ProductVersion>(pm.productId, pm.productVersion)!!,
                                pm.tray,
                                pm.boxes,
                                pm.loose
                            )
                        }
                    )
                    is Delta -> DeltaVersion(
                        it.id,
                        it.version,
                        it.date,
                        it.deltas.map { pm ->
                            ProductDeltaVersion(
                                application.versionStream.get<ProductVersion>(pm.productId, pm.productVersion)!!,
                                pm.tray,
                                pm.boxes,
                                pm.loose
                            )
                        }
                    )
                    is Tombstone -> TombstoneVersion(it.id, it.version)
                }
                application.versionStream.upsert(item)
            }
            var pending = emptyList<EntityVersion>()
            var sending = false
            fun maybeSend() {
                if(!sending && pending.size > 0) {
                    val item = pending.first()
                    pending = pending.drop(1)

                    val mapped: VersionedEntity = when(item) {
                        is FlavourVersion -> Flavour(item.id, item.version, item.name)
                        is ProductVersion -> Product(item.id, item.version, item.name, item.flavour.id, item.flavour.version, item.boxSize, item.status, item.supplierData?.let { SupplierInfo(it.name, it.url, it.code) })
                        is MeasurementVersion -> Measurement(item.id, item.version, item.date, item.measurements.map {
                            ProductMeasurement(it.productVersion.id, it.productVersion.version, it.tray, it.boxes, it.loose)
                        })
                        is DeltaVersion -> Delta(item.id, item.version, item.date, item.deltas.map {
                            ProductDelta(it.productVersion.id, it.productVersion.version, it.tray, it.boxes, it.loose)
                        })
                        is TombstoneVersion -> Tombstone(item.id, item.version)
                    }

                    window.fetch("${config.api}/stream", RequestInit(
                        method = "POST",
                        headers = js("{\"Content-Type\":\"application/json\"}"),
                        body = Json.encodeToString(mapped)
                    ))
                        .then { resp ->
                            if (!resp.ok) {
                                console.error(resp.status)
                                console.error(resp.body)
                                window.location.hash = "#/error"
                            }
                            sending = false
                            maybeSend()
                        }
                        .catch {
                            console.error(it)
                            window.location.hash = "#/error"
                        }
                }
            }
            application.versionStream.onUpsert {
                pending = pending + it
                maybeSend()
            }

            ready { body ->
                body.clear()
                Content(body).apply {
                    mainPage(application)
                }
            }
        }
        .catch {
            console.error(it)
            window.location.hash = "#/error"
        }
}

fun Content.mainPage(application: Application) {
    div {
        classList("navigation-bar")
        a("#/home") { h1 { text("Home") } }
        a("#/manage") { h1 { text("Manage") } }
        a("#/order") { h1 { text("Order") } }
    }

    div {
        pathChanged { path ->
            if (path == "") {
                window.location.hash = "#/home"
            } else {
                parent.clear()
                val end = path.indexOf('/', 2)
                val correctedEnd = if(end == -1) {
                    path.length
                } else {
                    end
                }
                val firstPart = path.substring(0..<correctedEnd)
                when (firstPart) {
                    "#/error" -> error()
                    "#/home" -> home(application)
                    "#/add-measurement" -> addMeasurement(application)
                    "#/edit-measurement" -> {
                        val id = path.substring(end + 1).toInt()
                        editMeasurement(application, id)
                    }
                    "#/add-delta" -> addDelta(application)
                    "#/edit-delta" -> {
                        val id = path.substring(end + 1).toInt()
                        editDelta(application, id)
                    }
                    "#/order" -> order(application)
                    "#/manage" -> manage(application)
                    "#/add-flavour" -> addFlavour(application)
                    "#/flavours" -> {
                        val id = path.substring(end + 1).toInt()
                        editFlavour(application, id)
                    }
                    "#/add-product" -> addProduct(application)
                    "#/products" -> {
                        val id = path.substring(end + 1).toInt()
                        editProduct(application, id)
                    }
                    else -> error()
                }
            }
        }
    }
}

fun Content.home(application: Application) {
    h1 { text("Home") }
    a("#/add-measurement") { button { text("Add measurement") } }
    a("#/add-delta") { button { text("Add delta") } }

    val items1 = application.measurements.map { MeasurementItem(it) }
    val items2 = application.deltas.map { DeltaItem(it) }

    (items1 + items2)
        .sortedByDescending { it.date }
        .forEach { item ->
            when(item) {
                is MeasurementItem -> {
                    val previousMeasurement = application.measurements
                        .filter { it.date < item.date }
                        .maxByOrNull { it.date }

                    val deltas =
                        if(previousMeasurement != null) {
                            application.deltas
                                .filter { it.date < item.date && it.date > previousMeasurement.date }
                        } else {
                            null
                        }

                    div {
                        classList("measurement")
                        div { text(item.measurement.date.toHumanString()) }
                        div { text("Tray") }
                        div { text("Boxes") }
                        div { text("Loose") }
                        div { text("Diff") }
                        item.measurement.measurements.forEach { m ->
                            val total1 = m.tray + m.boxes * m.productVersion.boxSize + m.loose
                            val total2 = previousMeasurement
                                ?.measurements
                                ?.firstOrNull { it.productVersion.id == m.productVersion.id }
                                ?.let { it.tray + it.boxes * it.productVersion.boxSize + it.loose }
                            val dlta = deltas
                                ?.mapNotNull { it.deltas.firstOrNull { it.productVersion.id == m.productVersion.id } }
                                ?.sumOf { it.tray + it.boxes * it.productVersion.boxSize + it.loose }
                                ?: 0

                            val diff = if(total2 == null) {
                                null
                            } else {
                                total2 + dlta - total1
                            }

                            div { text(m.productVersion.name) }
                            div { text(m.tray.toString()) }
                            div { text(m.boxes.toString()) }
                            div { text(m.loose.toString()) }
                            div { diff?.let { text(it.toString()) } }
                        }
                        div {}
                        div { a("#/edit-measurement/${item.measurement.id}") { button { text("Edit") } } }
                    }
                }
                is DeltaItem -> {
                    div {
                        classList("delta")
                        div { text(item.delta.date.toHumanString()) }
                        div { text("Tray") }
                        div { text("Boxes") }
                        div { text("Loose") }
                        item.delta.deltas.forEach { m ->
                            div { text(m.productVersion.name) }
                            div { text(m.tray.toString()) }
                            div { text(m.boxes.toString()) }
                            div { text(m.loose.toString()) }
                        }
                        div {}
                        div { a("#/edit-delta/${item.delta.id}") { button { text("Edit") } } }
                    }
                }
            }
        }
}

sealed interface HomePageItem {
    val date: LocalDateTime
}
data class MeasurementItem(val measurement: MeasurementVersion): HomePageItem {
    override val date = measurement.date
}
data class DeltaItem(val delta: DeltaVersion): HomePageItem {
    override val date = delta.date
}

class MeasurementModel(
    override val id: Int,
    var date: String?,
    val measurements: List<ProductMeasurementModel>
): FormModel {
    constructor(measurement: MeasurementVersion): this(
        measurement.id,
        measurement.date.toHumanString(),
        measurement.measurements.map {
            ProductMeasurementModel(it.productVersion, it.tray, it.boxes, it.loose)
        }
    )
    override fun toEntityVersion(version: Int): EntityVersion {
        return MeasurementVersion(
            id,
            version,
            fromHumanString(date!!),
            measurements.map {
                ProductMeasurementVersion(
                    it.productVersion,
                    it.tray!!,
                    it.boxes!!,
                    it.loose!!
                )
            }
        )
    }
}
class ProductMeasurementModel(
    val productVersion: ProductVersion,
    var tray: Int? = null,
    var boxes: Int? = null,
    var loose: Int? = null
)

fun Content.addMeasurement(application: Application) {
    val dateString = now().toHumanString()

    val previousMeasurement = application.measurements
        .maxByOrNull { it.date }

    val activeProducts = application.products
        .filter { it.status == ProductStatus.ACTIVE || it.status == ProductStatus.DEPRECATED }

    val model = MeasurementModel(
        application.nextId,
        dateString,
        activeProducts.map { ProductMeasurementModel(it) }
    )

    measurementForm("Add Measurement", application, model, previousMeasurement)
}

fun Content.editMeasurement(application: Application, id: Int) {
    val measurement = application.measurements
        .first { it.id == id }

    val previousMeasurement = application.measurements
        .filter { it.date < measurement.date }
        .maxByOrNull { it.date }

    val model = MeasurementModel(measurement)

    measurementForm("Add Delta", application, model, previousMeasurement)
}

private fun Content.measurementForm(
    title: String,
    application: Application,
    model: MeasurementModel,
    previousMeasurement: MeasurementVersion?
) {
    form(title, application, model) {
        classList("add-measurement")
        div { string(model::date) { classList("date") } }
        div { text("Tray") }
        div {}
        div { text("Boxes") }
        div {}
        div { text("Loose") }
        div {}
        model.measurements.forEach { meas ->
            val lastProd =
                previousMeasurement?.measurements?.firstOrNull { it.productVersion.id == meas.productVersion.id }

            div { text(meas.productVersion.name) }
            div { int(meas::tray) }
            div { lastProd?.tray?.let { text("(${it})") } }
            div { int(meas::boxes) }
            div { lastProd?.boxes?.let { text("(${it})") } }
            div { int(meas::loose) }
            div { lastProd?.loose?.let { text("(${it})") } }
        }
        div { }
        div { saveButton("#/home"); deleteButton("#/home") }
    }
}

class DeltaModel(
    override val id: Int,
    var date: String?,
    val deltas: List<ProductDeltaModel>
): FormModel {
    constructor(delta: DeltaVersion) : this(
        delta.id,
        delta.date.toHumanString(),
        delta.deltas.map {
            ProductDeltaModel(it.productVersion, it.tray, it.boxes, it.loose)
        }
    )

    override fun toEntityVersion(version: Int): EntityVersion {
        return DeltaVersion(
            id,
            version,
            fromHumanString(date!!),
            deltas.map {
                ProductDeltaVersion(
                    it.productVersion,
                    it.tray!!,
                    it.boxes!!,
                    it.loose!!
                )
            }
        )
    }
}
class ProductDeltaModel(
    val productVersion: ProductVersion,
    var tray: Int? = 0,
    var boxes: Int? = 0,
    var loose: Int? = 0
)

fun Content.addDelta(application: Application) {
    val dateString = now().toHumanString()

    val activeProducts = application.products
        .filter { it.status == ProductStatus.ACTIVE || it.status == ProductStatus.DEPRECATED }

    val model = DeltaModel(
        application.nextId,
        dateString,
        activeProducts.map { ProductDeltaModel(it) }
    )

    deltaForm("Add Delta", application, model)
}

fun Content.editDelta(application: Application, id: Int) {
    val delta = application.deltas
        .first { it.id == id }

    val model = DeltaModel(delta)

    deltaForm("Edit Delta", application, model)
}

fun Content.deltaForm(title: String, application: Application, model: DeltaModel) {
    form(title, application, model) {
        classList("delta")
        div { string(model::date) { classList("date")} }
        div { text("Tray") }
        div { text("Boxes") }
        div { text("Loose") }
        model.deltas.forEach { dlta ->
            div { text(dlta.productVersion.name) }
            div { int(dlta::tray) }
            div { int(dlta::boxes) }
            div { int(dlta::loose) }
        }
        div { }
        div { saveButton("#/home"); deleteButton("#/home") }
    }
}

private fun now(): LocalDateTime {
    val date = Date()
    val localDateTime = LocalDateTime(
        date.getFullYear(),
        date.getMonth().inc(),
        date.getDate(),
        date.getHours(),
        date.getMinutes(),
    )
    return localDateTime
}

fun Content.order(application: Application) {
    val calculate = calculate(application, now())
    val filtered = calculate.lines.filter { it.boxesToOrder > 0 && it.productVersion.supplierData != null}

    h1 { text("Order") }
    div {
        classList("order-lines")
        div { text("#") }
        div { text("Code") }
        div { text("Name") }
        filtered.forEach { c ->
            val supplierData = c.productVersion.supplierData!!
            div { text(c.boxesToOrder.toString()) }
            div {
                if(supplierData.url == null) {
                    if(supplierData.code != null) {
                        text(supplierData.code)
                    }
                } else {
                    a(supplierData.url) {
                        text(supplierData.code ?: "no-code")
                    }
                }
            }
            div { text(supplierData.name) }
        }
    }

    h1 { text("Calculation") }
    div {
        classList("calculation-summary")
        div { text("Out of stock:") }
        div { text(calculate.outOfStockDate?.toHumanString() ?: "none") }
        div { text("Goal date:") }
        div { text(calculate.goalDate.toHumanString()) }
        div { text("Last measurement:") }
        div { text(calculate.lastMeasurement?.date?.toHumanString() ?: "none") }
    }

    div {
        classList("calculation-lines")
        div { text("Product") }
        div { text("Start") }
        div { text("End") }
        div { text("From") }
        div { text("To") }
        div { text("Deltas") }
        div { text("Diff") }
        div { text("/") }
        div { text("*") }
        div { text("Goal") }
        div { text("Last") }
        div { text("Order") }
        div { text("Boxes") }
        calculate.lines.forEach { calc ->
            div { text(calc.productVersion.name) }
            div { text(calc.start?.toHumanString()?:"") }
            div { text(calc.end?.toHumanString()?:"") }
            div { text(calc.amountStart.toString()) }
            div { text(calc.amountEnd.toString()) }
            div { text(calc.deltas.toString()) }
            div { text(calc.diff.toString()) }
            div { text(calc.days.toString()) }
            div { text(calc.goalDays.toString()) }
            div { text(calc.goal.toString()) }
            div { text(calc.current.toString()) }
            div { text(calc.toOrder.toString()) }
            div { text(calc.boxesToOrder.toString()) }
        }
    }

    val sortedLines = calculate.lines.sortedBy { it.outOfStockDate }

    div {
        classList("out-of-stock-lines")
        div { text("Product") }
        div { text("Start") }
        div { text("End") }
        div { text("From") }
        div { text("To") }
        div { text("Deltas") }
        div { text("Diff") }
        div { text("/") }
        div { text("/") }
        div { text("Days") }
        div { text("Out of Stock") }
        sortedLines.forEach { calc ->
            div { text(calc.productVersion.name) }
            div { text(calc.start?.toHumanString()?:"") }
            div { text(calc.end?.toHumanString()?:"") }
            div { text(calc.amountStart.toString()) }
            div { text(calc.amountEnd.toString()) }
            div { text(calc.deltas.toString()) }
            div { text(calc.diff.toString()) }
            div { text(calc.days.toString()) }
            div { text(calc.current.toString()) }
            div { text(calc.daysOutOfStock?.toString()?:"") }
            div { text(calc.outOfStockDate?.toHumanString()?:"") }
        }
    }
}

fun Content.manage(application: Application) {
    h1 { text("Manage") }
    div {
        classList("flavours")
        div { text("Flavour") }
        div { a("#/add-flavour") { button { text("Create") } } }
        fun flavour(flavour: FlavourVersion) {
            div { text(flavour.name) }
            div { a("#/flavours/${flavour.id}") { button { text("Edit") } } }
        }
        application.flavours.forEach {
            flavour(it)
        }
    }

    div {
        classList("products")
        div { text("Product") }
        div { text("Flavour") }
        div { a("#/add-product") { button { text("Create") } } }

        fun product(product: ProductVersion) {
            div { text(product.name) }
            div { text(product.flavour.name) }
            div {
                a("#/products/${product.id}") { button { text("Edit") } }
            }
        }
        application.products.forEach {
            product(it)
        }
    }
}

fun Content.addFlavour(application: Application) {
    flavourForm("Add Flavour", application, FlavourModel(application.nextId))
}

fun Content.editFlavour(application: Application, id: Int) {
    val currentFlavour = application.flavours
        .first { it.id == id }

    flavourForm("Edit Flavour", application, FlavourModel(currentFlavour))
}

class FlavourModel(
    override val id: Int,
    var name: String? = null
): FormModel {
    constructor(flavourVersion: FlavourVersion):
            this(id = flavourVersion.id, name = flavourVersion.name)

    override fun toEntityVersion(version: Int): EntityVersion {
        return FlavourVersion(
            id,
            version,
            0f,
            name!!
        )
    }
}

fun Content.flavourForm(
    title: String,
    application: Application,
    model: FlavourModel,
) {
    form(title, application, model) {
        classList("form")
        string("Name", model::name)
        div { }
        div { saveButton("#/manage") }
    }
}

fun Content.addProduct(application: Application) {
    productForm("Add Product", application, ProductModel(application.nextId))
}

fun Content.editProduct(application: Application, id: Int) {
    val currentProduct = application.products
        .first { it.id == id }

    productForm("Edit Product", application, ProductModel(currentProduct))
}

class ProductModel(
    override val id: Int,
    var name: String? = null,
    var flavour: FlavourVersion? = null,
    var boxSize: Int? = null,
    var status: ProductStatus? = null,
    var supplierName: String? = null,
    var supplierUrl: String? = null,
    var supplierCode: String? = null,
): FormModel {
    constructor(productVersion: ProductVersion):
            this(
                productVersion.id,
                productVersion.name,
                productVersion.flavour,
                productVersion.boxSize,
                productVersion.status,
                productVersion.supplierData?.name,
                productVersion.supplierData?.url,
                productVersion.supplierData?.code,
            )
    override fun toEntityVersion(version: Int): EntityVersion {
        return ProductVersion(
            id,
            version,
            0f,
            name!!,
            flavour!!,
            boxSize!!,
            status!!,
            supplierName?.let {
                SupplierData(it, supplierUrl!!, supplierCode!!)
            }
        )
    }
}

fun Content.productForm(
    title: String,
    application: Application,
    model: ProductModel
) {
    form(title, application, model) {
        classList("form")
        string("Name", model::name)
        dropdown("Flavour", model::flavour, application.flavours, { it.id.toString() }, { it.name })
        int("Box Size", model::boxSize)
        dropdown("Status", model::status, ProductStatus.entries, { it.name }, { it.name })
        string("Supplier name", model::supplierName)
        string("Supplier url", model::supplierUrl)
        string("Supplier code", model::supplierCode)
        div { }
        div { saveButton("#/manage") }
    }
}

fun Content.error() {
    h1 { text("Error") }
}
