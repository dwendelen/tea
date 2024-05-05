import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.DocumentReadyState
import org.w3c.dom.HTMLElement
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.LOADING
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