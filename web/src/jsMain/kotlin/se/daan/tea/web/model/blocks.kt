package se.daan.tea.web.model

import se.daan.tea.api.LocalDateTime

fun blocks(application: Application): List<TimeFrame> {
    val sorted = application.measurements.sortedBy { it.date }
    val frames = sorted.zipWithNext { s, e ->
        val items = e.measurements
            .mapNotNull { m ->
                val matching = s.measurements.firstOrNull { it.productVersion.id == m.productVersion.id }
                if(matching == null) {
                    null
                } else {
                    TimeFrameItem(m.productVersion, total(m), total(matching), 0)
                }
        }
        val totalDiff = s.measurements.sumOf { total(it) } - e.measurements.sumOf { total(it) }
        TimeFrame(s.date, e.date, items, totalDiff)
    }
    return frames
}

private fun total(productMeasurement: ProductMeasurementVersion): Int {
    return productMeasurement.tray +
            productMeasurement.boxes * productMeasurement.productVersion.boxSize +
            productMeasurement.loose
}

data class TimeFrame(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val timeFrameItems: List<TimeFrameItem>,
    val totalDiff: Int,
)

data class TimeFrameItem(
    val productVersion: ProductVersion,
    val amountStart: Int,
    val amountEnd: Int,
    val delta: Int
)