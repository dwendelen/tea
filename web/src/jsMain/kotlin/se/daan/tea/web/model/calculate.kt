package se.daan.tea.web.model

import se.daan.tea.api.*
import kotlin.math.ceil
import kotlin.math.floor

fun calculate(application: Application, now: LocalDateTime): Calculation {
    val goalDate = plusDays(now, 90).copy(hour = 0, minute = 0)
    val lastMeasurement = application.measurements.maxByOrNull { it.date }

    val lines = application.products
        .filter { it.status == ProductStatus.ACTIVE }
        .map {
            calculate(application, it, goalDate, lastMeasurement)
        }

    val minOutOfStock = lines
        .mapNotNull { it.outOfStockDate }
        .minOrNull()

    return Calculation(minOutOfStock, goalDate, lastMeasurement, lines)
}

private fun calculate(application: Application, productVersion: ProductVersion, goalDate: LocalDateTime, lastMeasurement: MeasurementVersion?): CalculationLine {
    val mostRecentMeasurements = application.measurements
        .map { it.date to it.measurements.firstOrNull { it.productVersion.id == productVersion.id }?.let{ total(it) } }
        .sortedByDescending { it.first }
        .dropWhile { it.second == null || it.second == 0 }

    if(mostRecentMeasurements.size < 2) {
        return CalculationLine(productVersion, null, null, 0, 0, 0, 0, 0, null, 0,0, 0, 0,1, null, null)
    }

    val end = mostRecentMeasurements.first()
    val endDate = end.first
    val cleanEndDate = endDate.copy(hour = 0, minute = 0)
    val oneMonthAgo = minusDays(cleanEndDate, 30)

    val mostRecentOneMonthMeasurements = mostRecentMeasurements
        .takeWhile { it.first >= oneMonthAgo && it.second != null && it.second != 0 }

    val start = mostRecentOneMonthMeasurements.last()
    val startDate = start.first

    val deltas = application.deltas
        .filter { it.date in startDate..endDate }
        .sumOf { it.deltas.firstOrNull { it.productVersion.id == productVersion.id }?.let { total(it) } ?: 0 }

    val diff = start.second!! - end.second!! + deltas
    val days = daysBetween(startDate, endDate)

    val goalDays = daysBetween(lastMeasurement!!.date, goalDate)

    val current = lastMeasurement
        .measurements
        .first { it.productVersion.id == productVersion.id }
        .let { total(it) }

    val goal = ceil(diff.toDouble() / days.toDouble() * goalDays).toInt()
    val toOrder = goal - current
    val boxesToOrder = ceil(toOrder.toFloat() / productVersion.boxSize.toDouble()).toInt()
    val daysOutOfStock = floor(current * days.toDouble() / diff.toDouble()).toInt()
    val outOfStockDate = plusDays(lastMeasurement.date, daysOutOfStock)

    return CalculationLine(
        productVersion,
        start.first,
        end.first,
        start.second!!,
        end.second!!,
        deltas,
        diff,
        days,
        goalDate,
        goalDays,
        goal,
        current,
        toOrder,
        boxesToOrder,
        daysOutOfStock,
        outOfStockDate
    )
}

private fun total(productMeasurement: ProductMeasurementVersion): Int {
    return productMeasurement.tray +
            productMeasurement.boxes * productMeasurement.productVersion.boxSize +
            productMeasurement.loose
}

private fun total(productDelta: ProductDeltaVersion): Int {
    return productDelta.tray +
            productDelta.boxes * productDelta.productVersion.boxSize +
            productDelta.loose
}

data class Calculation(
    val outOfStockDate: LocalDateTime?,
    val goalDate: LocalDateTime,
    val lastMeasurement: MeasurementVersion?,
    val lines: List<CalculationLine>
)

data class CalculationLine(
    val productVersion: ProductVersion,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val amountStart: Int,
    val amountEnd: Int,
    val deltas: Int,
    val diff: Int,
    val days: Int,
    val goalDate: LocalDateTime?,
    val goalDays: Int,
    val goal: Int,
    val current: Int,
    val toOrder: Int,
    val boxesToOrder: Int,
    val daysOutOfStock: Int?,
    val outOfStockDate: LocalDateTime?
) {

}
