package tracker.bench

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RaceDemoTest {
    @Test
    fun synchronizedStoreKeepsEveryInsert() {
        val result = RaceDemo().run(synchronized = true, threads = 4, perThread = 5_000)
        assertTrue(result.report.isValid, result.report.toString())
        assertEquals(20_000, result.report.nodesFound)
    }

    @Test
    fun synchronizedStoreHasNoThreadFailures() {
        val result = RaceDemo().run(synchronized = true, threads = 4, perThread = 2_000)
        assertEquals(0, result.failedThreads)
        assertNull(result.firstError)
    }
}
