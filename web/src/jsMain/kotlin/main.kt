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
                        it.deprecated
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
                        is ProductVersion -> Product(item.id, item.version, item.name, item.flavour.id, item.flavour.version, item.deprecated)
                        is MeasurementVersion -> Measurement(item.id, item.version, item.date, item.measurements.map {
                            ProductMeasurement(it.productVersion.id, it.productVersion.version, it.tray, it.boxes, it.loose)
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
    application.measurements
        .reversed()
        .forEach { meas ->
            div {
                classList("measurement")
                div { text(meas.date.toHumanString()) }
                div { text("Tray") }
                div { text("Boxes") }
                div { text("Loose") }
                meas.measurements.forEach { m ->
                    div { text(m.productVersion.name) }
                    div { text(m.tray.toString()) }
                    div { text(m.boxes.toString()) }
                    div { text(m.loose.toString()) }
                }
            }
        }
}

fun Content.addMeasurement(application: Application) {
    val previousMeasurement = application.measurements
        .lastOrNull()
    fun active(productVersion: ProductVersion): Boolean {
        return if(!productVersion.deprecated) {
            true
        } else {
            if(previousMeasurement == null) {
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

    val date = Date()
    val dateString = LocalDateTime(
        date.getFullYear(),
        date.getMonth().inc(),
        date.getDate(),
        date.getHours(),
        date.getMinutes(),
    ).toHumanString()

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

fun Content.order(application: Application) {
    h1 { text("Order") }
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
    var deprecatedInput: HTMLInputElement? = null

    h1 { text("Add Product") }
    div {
        classList("form")
        div { text("Name") }
        div { nameInput = textInput {} }
        div { text("Flavour") }
        div { flavourInput = dropdown(application.flavours, { it.id.toString() }, { it.name }) }
        div { text("Deprecated") }
        div { deprecatedInput = checkbox {} }
        div { }
        div {
            val button = button { text("Create") }
            button.onclick = {
                val flavourId = flavourInput!!.value
                val flavour = application.flavours.first { it.id.toString() == flavourId }
                application.newProduct(nameInput!!.value, flavour, deprecatedInput!!.checked)
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
    var deprecatedInput: HTMLInputElement? = null

    h1 { text("Add Product") }
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
        div { text("Deprecated") }
        div {
            deprecatedInput = checkbox {}
            deprecatedInput!!.checked = currentProduct.deprecated
        }
        div { }
        div {
            val button = button { text("Save") }
            button.onclick = {
                val flavourId = flavourInput!!.value
                val flavour = application.flavours.first { it.id.toString() == flavourId }
                application.updateProduct(currentProduct, nameInput!!.value, flavour, deprecatedInput!!.checked)
                window.location.hash = "#/manage"
                null
            }
        }
    }
}

fun Content.error() {
    h1 { text("Error") }
}
