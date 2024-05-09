import kotlinx.serialization.json.*

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.clear
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.fetch.*
import se.daan.tea.api.Flavour
import se.daan.tea.web.model.Application
import se.daan.tea.web.model.FlavourVersion
import se.daan.tea.web.model.MeasurementData
import se.daan.tea.web.model.ProductVersion
import kotlin.js.Date


fun main() {
    val application = Application()
    testData(application)

    ready { body ->
        Content(body).apply {
            mainPage(application)
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
                application.versionStream.clearListeners()
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
                div { text(meas.date) }
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
        console.log(1, productVersion)
        return if(!productVersion.deprecated) {
            true
        } else {
            console.log(2, previousMeasurement)
            if(previousMeasurement == null) {
                false
            } else {
                val previousMeas = previousMeasurement.measurements
                    .firstOrNull { it.productVersion.id == productVersion.id }
                console.log(3, previousMeas)
                if (previousMeas == null) {
                    false
                } else {
                    previousMeas.tray != 0 || previousMeas.boxes != 0 || previousMeas.loose != 0
                }
            }
        }
    }

    val date = Date()
    val dateString = "${date.getFullYear()}-" +
            "${date.getMonth().inc().toString().padStart(2, '0')}-" +
            "${date.getDate().toString().padStart(2, '0')} "+
            "${date.getHours()}:" +
            "${date.getMinutes().toString().padStart(2, '0')}"

    val activeProducts = application.products
        .filter { active(it) }

    console.log(activeProducts)

    val inputs = mutableListOf<Triple<HTMLInputElement, HTMLInputElement, HTMLInputElement>>()
    h1 { text("Add Measurement") }

    div {
        classList("measurement")
        div { text(dateString) }
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
                application.newMeasurement(dateString, meas)
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
        application.versionStream.onUpsert {
            if (it is FlavourVersion) {
                flavour(it)
            }
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
        application.versionStream.onUpsert {
            if (it is ProductVersion) {
                product(it)
            }
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

fun speel() {
    window.fetch("https://api.dev.budget.daan.se/ping", init = RequestInit(mode = RequestMode.NO_CORS))
        .then {
            val body = document.body!!

            //body.append(Text(it.ok.toString()))
            null
        }
        .catch { println(it.message) }

    val prod: Flavour = Json.decodeFromString("{\"id\":4,\"name\":\"Earl Grey\"}")
    //val body = document.body
    //if(body == null) {
    println("No body")
    //} else {
    //body.append(Text(prod.toString()))
    //}

    val application = Application()

    val html = Html()
    html.upsert(Div("flavours", "", 0f))
    html.upsert(Div("flavour-header", "flavours", 0f))
    html.upsert(Div("flavour-header-flavour", "flavour-header", 0f))
    html.upsert(Div("flavour-header-action", "flavour-header", 1f))

    application.versionStream.onUpsert {
        if (it is FlavourVersion) {
            html.upsert(Div("flavour-${it.id}", "flavours", 0f))
            html.upsert(Text("flavour-${it.id}", "flavour-${it.id}", 0f, it.name))
        }
    }

    html.upsert(Div("flavour-create", "flavours", 100f))
    html.upsert(Input("flavour-create-input", "flavour-create", 0f))
    html.upsert(Button("flavour-create-button", "flavour-create", 1f))
    html.upsert(Text("flavour-create-button-txt", "flavour-create-button", 0f, "Create"))
    html.upsert(InputValue("flavour-create-input", "Val1"))



    application.newFlavour("Earl Grey")
    application.newFlavour("Linde")
    application.newFlavour("Kamille")

    html.upsert(Text("test", "", 0f, "Test1"))
    window.setTimeout({
        html.upsert(Text("test", "", 0f, "Test2"))
        html.upsert(InputValue("flavour-create-input", "Val2"))

    }, 5000)
}

fun words() {
    val numbers = (0..150)
        .map { (0..194745).random() }

    numbers
        .map { "${('a'.code + it.toString().length).toChar()}$it" }
        .sorted()
        .forEach { console.log(it) }
}
