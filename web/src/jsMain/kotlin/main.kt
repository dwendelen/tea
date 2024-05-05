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


fun main() {
    val application = Application()
    testData(application)

    ready { body ->
        pathChanged { path ->
            if (path == "") {
                window.location.hash = "#/home"
            } else {
                body.clear()
                Content(body).apply {
                    when (path) {
                        "#/home" -> home(application)
                        "#/order" -> order(application)
                        "#/manage" -> manage(application)
                        else -> error()
                    }
                }
            }
        }
    }
}

fun Content.home(application: Application) {
    h1 { text("Home") }
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

fun Content.order(application: Application) {
    h1 { text("Order") }
}

fun Content.manage(application: Application) {
    h1 { text("Manage") }
    div {
//        var inputText: HTMLInputElement? = null
        classList("flavours")
        div { text("Flavour") }
//        div { }
//        div { inputText = textInput {} }
        div {
            val button = button { text("Create") }
            button.onclick = {
//                application.newFlavour(inputText!!.value)
//                inputText!!.value = ""
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
//        var inputText: HTMLInputElement? = null
//        var select: HTMLSelectElement? = null
        classList("products")
        div { text("Product") }
        div { text("Flavour") }
//        div { }
//        div { inputText = textInput {} }
//        div { select = dropdown(application.flavours, { it.id.toString() }, { it.name }) }
        div {
            val button = button { text("Create") }
            button.onclick = {
//                val flavourId = select!!.value
//                val flavour = application.flavours.first { it.id.toString() == flavourId }
//                application.newProduct(inputText!!.value, flavour)
//                inputText!!.value = ""
//                select!!.value = ""
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
