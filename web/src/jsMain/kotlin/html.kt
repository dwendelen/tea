import kotlinx.browser.document
import org.w3c.dom.*

class BiMap<K, V> {
    private val map = mutableMapOf<K, V>()
    private val inverse = mutableMapOf<V, K>()

    operator fun get(key: K): V? {
        return map[key]
    }

    operator fun get(value: V): K? {
        return inverse[value]
    }

    operator fun set(key: K, value: V) {
        map[key] = value
        inverse[value] = key
    }

    operator fun set(value: V, key: K) {
        set(key, value)
    }
}

class Html {
    private val texts = BiMap<String, org.w3c.dom.Text>()

    fun upsert(element: HtmlThing) {
        when (element) {
            is Div -> {
                val myElement = document.getElementById(element.id)
                if(myElement == null) {
                    val parent = if (element.parent == "") {
                        document.body!!
                    } else {
                        document.getElementById(element.parent)!!
                    }
                    val divEl = document.createElement("div") as HTMLDivElement
                    divEl.id = element.id
                    divEl.dataset["order"] = element.order.toString()
                    parent.append(divEl)
                } else {
                    // Nothing to do I think
                }
            }
            is Text -> {
                val myText = texts[element.id]
                if(myText == null) {
                    val parent = if (element.parent == "") {
                        document.body!!
                    } else {
                        document.getElementById(element.parent)!!
                    }
                    val txt = document.createTextNode(element.text)
                    texts[element.id] = txt
                    parent.append(txt)
                } else {
                    texts[element.id]!!.data = element.text
                    // Nothing to do I think
                }
            }
            is Input -> {
                val myElement = document.getElementById(element.id)
                if(myElement == null) {
                    val parent = if (element.parent == "") {
                        document.body!!
                    } else {
                        document.getElementById(element.parent)!!
                    }
                    val inputEl = document.createElement("input") as HTMLInputElement
                    inputEl.id = element.id
                    inputEl.type = "text"
                    inputEl.dataset["order"] = element.order.toString()
                    parent.append(inputEl)
                } else {
                    // Nothing to do I think
                }
            }
            is Button -> {
                val myElement = document.getElementById(element.id)
                if(myElement == null) {
                    val parent = if (element.parent == "") {
                        document.body!!
                    } else {
                        document.getElementById(element.parent)!!
                    }
                    val inputEl = document.createElement("button") as HTMLButtonElement
                    inputEl.id = element.id
                    inputEl.dataset["order"] = element.order.toString()
                    parent.append(inputEl)
                } else {
                    // Nothing to do I think
                }
            }
            is InputValue -> {
                val parent = document.getElementById(element.parent)!! as HTMLInputElement
                parent.value = element.value
            }
        }
    }

    fun delete(id: String) {
        document.getElementById(id)
            ?: texts[id]
            ?.remove()
    }
}

sealed interface HtmlThing {
}

sealed interface HtmlElement: HtmlThing {
    val parent: String
    val id: String
    val order: Float
}

data class Div(
    override val id: String,
    override val parent: String,
    override val order: Float,
    val clazz: String? = null
): HtmlElement

data class Input(
    override val id: String,
    override val parent: String,
    override val order: Float,
    val clazz: String? = null
): HtmlElement

data class Button(
    override val id: String,
    override val parent: String,
    override val order: Float,
    val clazz: String? = null
): HtmlElement

data class InputValue(
    val parent: String,
    val value: String
): HtmlThing

data class Text(
    override val id: String,
    override val parent: String,
    override val order: Float,
    val text: String
): HtmlElement


open class Content(
    private val parent: HTMLElement
) {
    private inline fun <reified T: HTMLElement> simple(tag: String, content: Content.() -> Unit): T {
        val el = document.createElement(tag) as T
        Content(el).apply(content)
        parent.append(el)
        return el
    }

    fun h1(content: Content.() -> Unit): HTMLHeadingElement {
        return simple("h1", content)
    }

    fun div(content: DivContent.() -> Unit): HTMLDivElement {
        val div = document.createElement("div") as HTMLDivElement
        DivContent(div).apply(content)
        parent.append(div)
        return div
    }

    fun button(content: Content.() -> Unit): HTMLButtonElement {
        return simple("button", content)
    }

    fun textInput(content: Content.() -> Unit): HTMLInputElement {
        val input = simple<HTMLInputElement>("input", content)
        input.type = "text"
        return input
    }

    fun <T> dropdown(items: List<T>, toVal: (T) -> String, toText: (T) -> String): HTMLSelectElement {
        val select = document.createElement("select") as HTMLSelectElement
        items.forEach {
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = toVal(it)
            opt.text = toText(it)
            select.append(opt)
        }
        parent.append(select)
        return select
    }

    fun text(text: String): org.w3c.dom.Text {
        val textNode = document.createTextNode(text)
        parent.append(textNode)
        return textNode
    }
}

class DivContent(
    private val div: HTMLDivElement
): Content(div) {
    fun classList(classList: String) {
        div.classList.value = classList
    }
}