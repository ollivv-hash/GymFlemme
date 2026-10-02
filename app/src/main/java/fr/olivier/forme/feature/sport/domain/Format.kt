package fr.olivier.forme.feature.sport.domain

import java.text.NumberFormat
import java.util.Locale

/** French display helpers shared by the UI and the text summary. */
object Fmt {
    val locale: Locale = Locale.FRANCE

    fun number(value: Double, maxDecimals: Int = 1): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = maxDecimals
            minimumFractionDigits = 0
        }.format(value).replace(' ', ' ').replace(' ', ' ')

    fun kg(value: Double): String = "${number(value, 2)} kg"

    /** 125 → "2:05". */
    fun mmss(totalSec: Int): String = "%d:%02d".format(totalSec / 60, totalSec % 60)

    /** 25 → "25 s", 300 → "5 min", 330 → "5:30". */
    fun duration(totalSec: Int): String = when {
        totalSec < 60 -> "$totalSec s"
        totalSec % 60 == 0 -> "${totalSec / 60} min"
        else -> mmss(totalSec)
    }
}
