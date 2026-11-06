package tracker.store

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import tracker.items

class ArrayBufferTest {
    @Test
    fun growsBeyondInitialCapacity() {
        val buffer = ArrayBuffer<Int>(2)
        (1..100).forEach { buffer.add(it) }
        assertEquals((1..100).toList(), buffer.items())
    }

    @Test
    fun outOfRangeIndexFails() {
        val buffer = ArrayBuffer<Int>()
        buffer.add(1)
        assertFailsWith<IndexOutOfBoundsException> { buffer[1] }
    }

    @Test
    fun getOrNullReturnsNullOutsideBuffer() {
        val buffer = ArrayBuffer<Int>()
        buffer.add(7)
        assertNull(buffer.getOrNull(1))
    }

    @Test
    fun containsFindsAddedItem() {
        val buffer = ArrayBuffer<String>()
        buffer.add("a")
        assertTrue("a" in buffer)
    }
}
