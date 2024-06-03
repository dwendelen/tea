import kotlinx.serialization.json.*

import kotlinx.browser.window
import kotlinx.dom.clear
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.fetch.*
import se.daan.tea.api.*
import se.daan.tea.web.model.*
import kotlin.js.Date


fun main() {
    window.fetch("https://api.dev.tea.daan.se/stream", RequestInit())
        .then { resp ->
            if (!resp.ok) {
                TODO()
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
                        it.deprecated,
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
                        is ProductVersion -> Product(item.id, item.version, item.name, item.flavour.id, item.flavour.version, item.boxSize, item.deprecated, item.supplierData?.let { SupplierInfo(it.name, it.url, it.code) })
                        is MeasurementVersion -> Measurement(item.id, item.version, item.date, item.measurements.map {
                            ProductMeasurement(it.productVersion.id, it.productVersion.version, it.tray, it.boxes, it.loose)
                        })
                        is DeltaVersion -> Delta(item.id, item.version, item.date, item.deltas.map {
                            ProductDelta(it.productVersion.id, it.productVersion.version, it.tray, it.boxes, it.loose)
                        })
                    }

                    window.fetch("https://api.dev.tea.daan.se/stream", RequestInit(
                        method = "POST",
                        headers = js("{\"Content-Type\":\"application/json\"}"),
                        body = Json.encodeToString(mapped)
                    ))
                        .then { resp ->
                            if (!resp.ok) {
                                TODO()
                            }
                            sending = false
                            maybeSend()
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
                    "#/home" -> home(application)
                    "#/add-measurement" -> addMeasurement(application)
                    "#/add-delta" -> addDelta(application)
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
    val addMeas = button { text("Add measurement") }
    addMeas.onclick = {
        window.location.hash = "#/add-measurement"
        null
    }
    val addDelta = button { text("Add delta") }
    addDelta.onclick = {
        window.location.hash = "#/add-delta"
        null
    }

    val items1 = application.measurements.map { MeasurementItem(it) }
    val items2 = application.deltas.map { DeltaItem(it) }

    (items1 + items2)
        .sortedByDescending { it.date }
        .forEach { item ->
            when(item) {
                is MeasurementItem -> {
                    div {
                        classList("measurement")
                        div { text(item.measurement.date.toHumanString()) }
                        div { text("Tray") }
                        div { text("Boxes") }
                        div { text("Loose") }
                        item.measurement.measurements.forEach { m ->
                            div { text(m.productVersion.name) }
                            div { text(m.tray.toString()) }
                            div { text(m.boxes.toString()) }
                            div { text(m.loose.toString()) }
                        }
                    }
                }
                is DeltaItem -> {
                    div {
                        classList("measurement")
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

fun Content.addMeasurement(application: Application) {
    val previousMeasurement = application.measurements
        .lastOrNull()

    fun active(productVersion: ProductVersion): Boolean {
        return if (!productVersion.deprecated) {
            true
        } else {
            if (previousMeasurement == null) {
                false
            } else {
                val previousMeas = previousMeasurement.measurements
                    .firstOrNull { it.productVersion.id == productVersion.id }
                if (previousMeas == null) {
                    false
                } else {
                    previousMeas.tray != 0 || previousMeas.boxes != 0 || previousMeas.loose != 0
                }
            }
        }
    }

    val dateString = now().toHumanString()

    val activeProducts = application.products
        .filter { active(it) }

    var dateInput: HTMLInputElement? = null
    val inputs = mutableListOf<Triple<HTMLInputElement, HTMLInputElement, HTMLInputElement>>()
    h1 { text("Add Measurement") }

    div {
        classList("measurement")
        div {
            dateInput = textInput { classList("date") }
            dateInput!!.value = dateString
        }
        div { text("Tray") }
        div { text("Boxes") }
        div { text("Loose") }
        activeProducts.forEach { prod ->
            var tray: HTMLInputElement? = null
            var boxes: HTMLInputElement? = null
            var loose: HTMLInputElement? = null
            div { text(prod.name) }
            div { tray = textInput { } }
            div { boxes = textInput { } }
            div { loose = textInput { } }
            inputs.add(Triple(tray!!, boxes!!, loose!!))
        }
        div {}
        div {
            val button = button { text("Create") }
            button.onclick = {
                val meas = inputs.mapIndexed { i, inp ->
                    MeasurementData(
                        activeProducts[i],
                        inp.first.value.toInt(),
                        inp.second.value.toInt(),
                        inp.third.value.toInt()
                    )
                }
                application.newMeasurement(fromHumanString(dateInput!!.value), meas)
                window.location.hash = "#/home"
                null
            }
        }
    }
}


fun Content.addDelta(application: Application) {
    val previousMeasurement = application.measurements
        .lastOrNull()

    fun active(productVersion: ProductVersion): Boolean {
        return if (!productVersion.deprecated) {
            true
        } else {
            if (previousMeasurement == null) {
                false
            } else {
                val previousMeas = previousMeasurement.measurements
                    .firstOrNull { it.productVersion.id == productVersion.id }
                if (previousMeas == null) {
                    false
                } else {
                    previousMeas.tray != 0 || previousMeas.boxes != 0 || previousMeas.loose != 0
                }
            }
        }
    }

    val dateString = now().toHumanString()

    val activeProducts = application.products
        .filter { active(it) }

    var dateInput: HTMLInputElement? = null
    val inputs = mutableListOf<Triple<HTMLInputElement, HTMLInputElement, HTMLInputElement>>()
    h1 { text("Add Delta") }

    div {
        classList("delta")
        div {
            dateInput = textInput { classList("date") }
            dateInput!!.value = dateString
        }
        div { text("Tray") }
        div { text("Boxes") }
        div { text("Loose") }
        activeProducts.forEach { prod ->
            var tray: HTMLInputElement? = null
            var boxes: HTMLInputElement? = null
            var loose: HTMLInputElement? = null
            div { text(prod.name) }
            div { tray = textInput { } }
            div { boxes = textInput { } }
            div { loose = textInput { } }
            inputs.add(Triple(tray!!, boxes!!, loose!!))
        }
        div {}
        div {
            val button = button { text("Create") }
            button.onclick = {
                val meas = inputs.mapIndexed { i, inp ->
                    DeltaData(
                        activeProducts[i],
                        inp.first.value.toInt(),
                        inp.second.value.toInt(),
                        inp.third.value.toInt()
                    )
                }
                application.newDelta(fromHumanString(dateInput!!.value), meas)
                window.location.hash = "#/home"
                null
            }
        }
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
        text("Goal date: " + calculate.goalDate.toHumanString())
    }
    div {
        text("Last measurement: " + (calculate.lastMeasurement?.date?.toHumanString() ?: "none"))
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
}

fun Content.manage(application: Application) {
    h1 { text("Manage") }
    div {
        classList("flavours")
        div { text("Flavour") }
        div {
            val button = button { text("Create") }
            button.onclick = {
                window.location.hash = "#/add-flavour"
                null
            }
        }
        fun flavour(flavour: FlavourVersion) {
            div { text(flavour.name) }
            div {
                val edit = button { text("Edit") }
                edit.onclick = {
                    window.location.hash = "#/flavours/${flavour.id}"
                    null
                }
            }
        }
        application.flavours.forEach {
            flavour(it)
        }
    }

    div {
        classList("products")
        div { text("Product") }
        div { text("Flavour") }
        div {
            val button = button { text("Create") }
            button.onclick = {
                window.location.hash = "#/add-product"
                null
            }
        }
        fun product(product: ProductVersion) {
            div { text(product.name) }
            div { text(product.flavour.name) }
            div {
                val edit = button { text("Edit") }
                edit.onclick = {
                    window.location.hash = "#/products/${product.id}"
                    null
                }
            }
        }
        application.products.forEach {
            product(it)
        }
    }
}

fun Content.addFlavour(application: Application) {
    var nameInput: HTMLInputElement? = null

    h1 { text("Add Flavour") }
    div {
        classList("form")
        div { text("Name") }
        div { nameInput = textInput {} }
        div { }
        div {
            val button = button { text("Create") }
            button.onclick = {
                application.newFlavour(nameInput!!.value)
                window.location.hash = "#/manage"
                null
            }
        }
    }
}

fun Content.editFlavour(application: Application, id: Int) {
    val currentFlavour = application.flavours
        .first { it.id == id }

    var nameInput: HTMLInputElement? = null

    h1 { text("Edit Flavour") }
    div {
        classList("form")
        div { text("Name") }
        div {
            nameInput = textInput {}
            nameInput!!.value = currentFlavour.name
        }
        div { }
        div {
            val button = button { text("Save") }
            button.onclick = {
                application.updateFlavour(currentFlavour, nameInput!!.value)
                window.location.hash = "#/manage"
                null
            }
        }
    }
}

fun Content.addProduct(application: Application) {
    var nameInput: HTMLInputElement? = null
    var flavourInput: HTMLSelectElement? = null
    var boxSizeInput: HTMLInputElement? = null
    var deprecatedInput: HTMLInputElement? = null

    var supplierNameInput: HTMLInputElement? = null
    var supplierUrlInput: HTMLInputElement? = null
    var supplierCodeInput: HTMLInputElement? = null

    h1 { text("Add Product") }
    div {
        classList("form")
        div { text("Name") }
        div { nameInput = textInput {} }
        div { text("Flavour") }
        div { flavourInput = dropdown(application.flavours, { it.id.toString() }, { it.name }) }
        div { text("Box size") }
        div { boxSizeInput = textInput {} }
        div { text("Deprecated") }
        div { deprecatedInput = checkbox {} }
        div { text("Supplier name") }
        div { supplierNameInput = textInput {} }
        div { text("Supplier url") }
        div { supplierUrlInput = textInput {} }
        div { text("Supplier code") }
        div { supplierCodeInput = textInput {} }
        div { }
        div {
            val button = button { text("Create") }
            button.onclick = {
                val flavourId = flavourInput!!.value
                val flavour = application.flavours.first { it.id.toString() == flavourId }
                application.newProduct(
                    nameInput!!.value,
                    flavour,
                    boxSizeInput!!.value.toInt(),
                    deprecatedInput!!.checked,
                    supplierNameInput!!.value.ifBlank { null },
                    supplierUrlInput!!.value.ifBlank { null },
                    supplierCodeInput!!.value.ifBlank { null },
                )
                window.location.hash = "#/manage"
                null
            }
        }
    }
}

fun Content.editProduct(application: Application, id: Int) {
    val currentProduct = application.products
        .first { it.id == id }

    var nameInput: HTMLInputElement? = null
    var flavourInput: HTMLSelectElement? = null
    var boxSizeInput: HTMLInputElement? = null
    var deprecatedInput: HTMLInputElement? = null

    var supplierNameInput: HTMLInputElement? = null
    var supplierUrlInput: HTMLInputElement? = null
    var supplierCodeInput: HTMLInputElement? = null

    h1 { text("Edit Product") }
    div {
        classList("form")
        div { text("Name") }
        div {
            nameInput = textInput {}
            nameInput!!.value = currentProduct.name
        }
        div { text("Flavour") }
        div {
            flavourInput = dropdown(application.flavours, { it.id.toString() }, { it.name })
            flavourInput!!.value = currentProduct.flavour.id.toString()
        }
        div { text("Box Size") }
        div {
            boxSizeInput = textInput {}
            boxSizeInput!!.value = currentProduct.boxSize.toString()
        }
        div { text("Deprecated") }
        div {
            deprecatedInput = checkbox {}
            deprecatedInput!!.checked = currentProduct.deprecated
        }
        div { text("Supplier name") }
        div {
            supplierNameInput = textInput {}
            supplierNameInput!!.value = currentProduct.supplierData?.name?:""
        }
        div { text("Supplier url") }
        div {
            supplierUrlInput = textInput {}
            supplierUrlInput!!.value = currentProduct.supplierData?.url?:""
        }
        div { text("Supplier code") }
        div {
            supplierCodeInput = textInput {}
            supplierCodeInput!!.value = currentProduct.supplierData?.code?:""
        }
        div { }
        div {
            val button = button { text("Save") }
            button.onclick = {
                val flavourId = flavourInput!!.value
                val flavour = application.flavours.first { it.id.toString() == flavourId }
                application.updateProduct(
                    currentProduct,
                    nameInput!!.value,
                    flavour,
                    boxSizeInput!!.value.toInt(),
                    deprecatedInput!!.checked,
                    supplierNameInput!!.value.ifBlank { null },
                    supplierUrlInput!!.value.ifBlank { null },
                    supplierCodeInput!!.value.ifBlank { null },
                )
                window.location.hash = "#/manage"
                null
            }
        }
    }
}

fun Content.error() {
    h1 { text("Error") }
}
