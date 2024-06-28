@file:JsModule("chart.js/auto")
@file:JsNonModule

import org.w3c.dom.HTMLCanvasElement


@JsName("default")
external class Chart(
    ctx: HTMLCanvasElement,
    config: Config
)

external interface Config {
    var type: String
    var data: Data
    var options: dynamic
    var plugins: List<dynamic>
}

external interface Data
external interface PrimitiveData: Data {
    var datasets: Array<PrimitiveDataSet>
    var labels: Array<String>
}
external interface PrimitiveDataSet {
    var data: Array<Int>
}
external interface ObjectData: Data {
    var datasets: Array<ObjectDataSet>
}
external interface ObjectDataSet {
    var data: Array<DataObject>
}
external interface DataObject {
    var x: dynamic
    var y: dynamic
}