package se.daan.tea.web.model

import se.daan.tea.api.LocalDateTime
import se.daan.tea.api.daysBetween
import se.daan.tea.api.minusDays

fun calculate(application: Application, now: LocalDateTime): List<CalculationLine> {
    return application.products
        .filter { !it.deprecated }
        .map {
        calculate(application, it, now)
    }
}

private fun calculate(application: Application, productVersion: ProductVersion, now: LocalDateTime): CalculationLine {
    val oneMonthAgo = minusDays(now, 30)
    val measurements = application.measurements
        .map { it.date to it.measurements.firstOrNull { it.productVersion == productVersion }?.let{ total(it) } }
        .filter { it.second != null && it.second != 0 && oneMonthAgo <= it.first && it.first <= now }

    if(measurements.isEmpty()) {
        return CalculationLine(productVersion, null, null, 0, 0, 0)
    }

    val start = measurements.minBy { it.first }
    val end = measurements.maxBy { it.first }

    return CalculationLine(
        productVersion,
        start.first,
        end.first,
        start.second!!,
        end.second!!,
        0
    )
}

private fun total(productMeasurement: ProductMeasurementVersion): Int {
    return productMeasurement.tray!! +
            productMeasurement.boxes!! * productMeasurement.productVersion.boxSize +
            productMeasurement.loose!!
}

data class CalculationLine(
    val productVersion: ProductVersion,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val amountStart: Int,
    val amountEnd: Int,
    val deltas: Int
) {
    val diff = amountStart - amountEnd + deltas
    val days = if(start != null && end != null) { daysBetween(start, end) } else { null }
}
