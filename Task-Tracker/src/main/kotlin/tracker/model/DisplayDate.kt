package tracker.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object DisplayDate {
    const val PATTERN_HINT = "ДД.ММ.ГГ"

    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uu").withResolverStyle(ResolverStyle.STRICT)

    fun format(date: LocalDate): String = date.format(FORMAT)

    fun parse(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim(), FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }
}

fun LocalDate.display(): String = DisplayDate.format(this)
