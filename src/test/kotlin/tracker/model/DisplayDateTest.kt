package tracker.model

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class DisplayDateTest {
    @Test
    fun formatsAsDayMonthShortYear() {
        assertEquals("05.11.26", LocalDate.of(2026, 11, 5).display())
    }

    @Test
    fun parsesDayMonthShortYear() {
        assertEquals(LocalDate.of(2026, 11, 5), DisplayDate.parse("05.11.26"))
    }

    @Test
    fun rejectsImpossibleDate() {
        assertNull(DisplayDate.parse("31.02.26"))
    }

    @Test
    fun rejectsIsoFormat() {
        assertNull(DisplayDate.parse("2026-11-05"))
    }
}
