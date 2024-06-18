import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import se.daan.tea.web.model.Application
import se.daan.tea.web.model.EntityVersion
import kotlin.reflect.KMutableProperty0

fun <T: FormModel> Content.form(
    title: String,
    application: Application,
    model: T,
    formContent: FormContent<T>.() -> Unit
) {
    h1 { text(title) }
    val div = div {}
    val ctx = FormContent(div, application, model)
    ctx.apply(formContent)
}

class FormContent<T: FormModel>(
    div: HTMLDivElement,
    private val application: Application,
    private val model: T
): DivContent(div) {
    private var onSave = {}

    fun string(label: String, property: KMutableProperty0<String?>, content: Content.() -> Unit = {}) {
        div { text(label) }
        div { string(property, content) }
    }

    fun string(property: KMutableProperty0<String?>, content: Content.() -> Unit = {}) {
        val input = textInput(content)
        input.value = property.get() ?: ""
        val oldSave = onSave
        onSave = {
            oldSave()
            val newValue = input.value.ifBlank { null }
            property.set(newValue)
        }
    }

    fun int(label: String, property: KMutableProperty0<Int?>) {
        div { text(label) }
        div { int(property) }
    }

    fun int(property: KMutableProperty0<Int?>) {
        val input = textInput {}
        input.value = property.get()?.toString()?:""
        val oldSave = onSave
        onSave = {
            oldSave()
            val newValue = input.value.ifBlank { null }
            property.set(newValue?.toInt())
        }
    }

    fun <I> dropdown(label: String, property: KMutableProperty0<I?>, items: List<I>, toVal: (I) -> String, toText: (I) -> String) {
        div { text(label) }
        div {
            val input = dropdown(items, toVal, toText)
            property.get()?.let {
                input.value = toVal(it)
            }
            val oldSave = onSave
            onSave = {
                oldSave()
                val newValue = items.firstOrNull { toVal(it) == input.value }
                property.set(newValue)
            }
        }
    }

    fun saveButton(redirect: String) {
        val button = button { text("Save") }
        button.onclick = {
            onSave()
            val entityVersion = model.toEntityVersion(application.nextVersion)
            application.upsert(entityVersion)
            window.location.hash = redirect
            null
        }
    }

    fun deleteButton(redirect: String) {
        val button = button { text("Delete") }
        button.onclick = {
            application.delete(model.id)
            window.location.hash = redirect
            null
        }
    }
}

interface FormModel {
    val id: Int
    fun toEntityVersion(version: Int): EntityVersion
}