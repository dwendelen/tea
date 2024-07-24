import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL

fun ready(fn: (body: HTMLElement) -> Unit) {
    if(document.readyState != DocumentReadyState.LOADING) {
        fn(document.body!!)
    } else {
        document.addEventListener("DOMContentLoaded", { fn(document.body!!) })
    }
}

fun pathChanged(fn: (String) -> Unit) {
    fn(window.location.hash)
    window.addEventListener("hashchange", { ev: Event ->
        val hashChangeEvent = ev as HashChangeEvent
        val newHash = URL(hashChangeEvent.newURL).hash
        fn(newHash)
    })
}


open class Content(
    var parent: HTMLElement
) {
    private inline fun <reified T: HTMLElement> simple(tag: String, content: Content.() -> Unit): T {
        val el = document.createElement(tag) as T
        val oldParent = parent
        parent = el
        Content(el).apply(content)
        parent = oldParent
        parent.append(el)
        return el
    }

    fun classList(classList: String) {
        parent.classList.value = classList
    }

    fun h1(content: Content.() -> Unit): HTMLHeadingElement {
        return simple("h1", content)
    }

    fun h2(content: Content.() -> Unit): HTMLHeadingElement {
        return simple("h2", content)
    }

    fun div(content: DivContent.() -> Unit): HTMLDivElement {
        val div = document.createElement("div") as HTMLDivElement
        val oldParent = parent
        parent = div
        DivContent(div).apply(content)
        parent = oldParent
        parent.append(div)
        return div
    }

    fun a(url: String, content: Content.() -> Unit): HTMLAnchorElement {
        val anchor = simple<HTMLAnchorElement>("a", content)
        anchor.href = url
        return anchor
    }

    fun button(content: Content.() -> Unit): HTMLButtonElement {
        return simple("button", content)
    }

    fun textInput(content: Content.() -> Unit): HTMLInputElement {
        val input = simple<HTMLInputElement>("input", content)
        input.type = "text"
        return input
    }

    fun checkbox(content: Content.() -> Unit): HTMLInputElement {
        val input = simple<HTMLInputElement>("input", content)
        input.type = "checkbox"
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

open class DivContent(
    private val div: HTMLDivElement
): Content(div) {

}