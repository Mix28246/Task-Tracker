package tracker.store

import java.util.TreeMap
import kotlin.random.Random
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tracker.items

class TreapStoreTest {
    private data class Item(val key: Int, val priority: Int)

    private fun store(seed: Int = 1, trace: Boolean = false) =
        TreapStore<Int, Item>(keyOf = { it.key }, priorityOf = { it.priority }, traceEnabled = trace, random = Random(seed))

    @Test
    fun randomOperationsMatchReferenceMap() {
        val random = Random(7)
        repeat(100) { round ->
            val treap = store(round)
            val reference = TreeMap<Int, Item>()
            repeat(300) {
                val key = random.nextInt(150)
                when (random.nextInt(10)) {
                    in 0..4 -> {
                        val item = Item(key, random.nextInt(1, 11))
                        val expected = !reference.containsKey(key)
                        if (expected) reference[key] = item
                        assertEquals(expected, treap.add(item))
                    }
                    in 5..7 -> assertEquals(reference.remove(key) != null, treap.removeByKey(key))
                    else -> assertEquals(reference[key], treap.findByKey(key))
                }
                assertEquals(reference.size, treap.size)
            }
            assertEquals(reference.values.toList(), treap.all().items())
            assertTrue(treap.inspect().isValid)
        }
    }

    @Test
    fun rangeIsInclusiveAndOrdered() {
        val treap = store()
        (1..100).forEach { treap.add(Item(it, it % 10)) }
        assertEquals((20..30).toList(), treap.range(20, 30).items().map { it.key })
        assertEquals(listOf(100), treap.range(100, 500).items().map { it.key })
        assertTrue(treap.range(30, 20).isEmpty())
    }

    @Test
    fun topReturnsHighestPrioritiesFirst() {
        val treap = store()
        val random = Random(3)
        val items = (1..500).map { Item(it, random.nextInt(1, 11)) }
        items.forEach { treap.add(it) }
        val expected = items.sortedWith(compareByDescending<Item> { it.priority }.thenBy { it.key }).take(25)
        assertEquals(expected, treap.top(25).items())
    }

    @Test
    fun topLargerThanSizeReturnsEverything() {
        val treap = store()
        (1..50).forEach { treap.add(Item(it, it % 10)) }
        assertEquals(50, treap.top(10_000).size)
    }

    @Test
    fun valueAtFollowsKeyOrder() {
        val treap = store()
        listOf(40, 10, 30, 20, 50).forEach { treap.add(Item(it, it % 7)) }
        assertEquals(listOf(10, 20, 30, 40, 50), (0 until 5).map { treap.valueAt(it)?.key })
        assertNull(treap.valueAt(5))
    }

    @Test
    fun subtreeSizesAndHeightsStayConsistentAfterRemovals() {
        val treap = store()
        (1..1_000).forEach { treap.add(Item(it, it % 10)) }
        (1..1_000 step 2).forEach { treap.removeByKey(it) }
        assertTrue(treap.inspect().subtreesConsistent)
    }

    @Test
    fun heapPropertyHoldsAfterMixedOperations() {
        val treap = store()
        (1..2_000).forEach { treap.add(Item(it, it % 10)) }
        (1..2_000 step 3).forEach { treap.removeByKey(it) }
        val report = treap.inspect()
        assertTrue(report.isValid, report.toString())
        assertEquals(treap.size, report.nodesFound)
    }

    @Test
    fun heightStaysLogarithmicForSortedInsertions() {
        val treap = store()
        (1..100_000).forEach { treap.add(Item(it, it % 10 + 1)) }
        assertTrue(treap.height() < 80, "height = ${treap.height()}")
    }

    @Test
    fun duplicateKeyIsRejected() {
        val treap = store()
        assertTrue(treap.add(Item(5, 1)))
        assertFalse(treap.add(Item(5, 9)))
        assertEquals(1, treap.size)
        assertEquals(1, treap.findByKey(5)?.priority)
    }

    @Test
    fun searchPathEndsAtFoundKey() {
        val treap = store(trace = true)
        (1..50).forEach { treap.add(Item(it, it % 7)) }
        treap.findByKey(37)
        assertEquals(37, treap.lastPath.last())
        assertTrue(treap.lastPath.size <= treap.height())
    }

    @Test
    fun snapshotShowsFocusedPathBelowDepthLimit() {
        val treap = store(trace = true)
        (1..5_000).forEach { treap.add(Item(it, it % 10)) }
        val deepest = (1..5_000).maxBy { key -> treap.findByKey(key).let { treap.lastPath.size } }
        treap.findByKey(deepest)
        val path = treap.lastPath
        val snapshot = treap.snapshot(2) { it in path }
        assertTrue(path.items().all { key -> snapshot.nodes.items().any { it.key == key } })
        assertTrue(snapshot.nodes.size < 200)
    }

    @Test
    fun snapshotCountsHiddenNodes() {
        val treap = store()
        (1..1_000).forEach { treap.add(Item(it, it % 10)) }
        val snapshot = treap.snapshot(3)
        val nodes = snapshot.nodes.items()
        assertTrue(nodes.all { it.depth <= 3 })
        assertEquals(1_000, nodes.size + nodes.sumOf { it.hiddenBelow })
        assertEquals(nodes.map { it.key }.sorted(), nodes.map { it.key })
    }

    @Test
    fun emptyStoreBehaves() {
        val treap = store()
        assertNull(treap.findByKey(1))
        assertNull(treap.maxKey())
        assertFalse(treap.removeByKey(1))
        assertTrue(treap.all().isEmpty())
        assertEquals(0, treap.height())
        assertTrue(treap.inspect().isValid)
    }

    @Test
    fun copyIsIndependentOfOriginal() {
        val treap = store()
        (1..100).forEach { treap.add(Item(it, it % 10)) }
        val copy = treap.copy()
        copy.add(Item(500, 3))
        copy.removeByKey(1)
        assertEquals(100, treap.size)
        assertEquals(1, treap.findByKey(1)?.key)
        assertNull(treap.findByKey(500))
        assertTrue(copy.inspect().isValid)
    }

    @Test
    fun topBreaksTiesByKey() {
        val treap = store()
        listOf(50, 10, 40, 20, 30).forEach { treap.add(Item(it, 5)) }
        treap.add(Item(99, 9))
        assertEquals(listOf(99, 10, 20), treap.top(3).items().map { it.key })
    }

    @Test
    fun ceilingFindsFirstKeyNotLess() {
        val treap = store(trace = true)
        (1..10).forEach { treap.add(Item(it * 10, it)) }
        assertEquals(40, treap.ceiling(35)?.key)
    }

    @Test
    fun ceilingRecordsPathOnMiss() {
        val treap = store(trace = true)
        (1..10).forEach { treap.add(Item(it * 10, it)) }
        assertNull(treap.ceiling(500))
        assertTrue(treap.lastPath.size > 0)
    }

    @Test
    fun topMatchesSortedReferenceWithManyTies() {
        val random = Random(11)
        repeat(20) { round ->
            val treap = store(round)
            val items = (1..2_000).map { Item(it, random.nextInt(1, 4)) }.shuffled(random)
            items.forEach { treap.add(it) }
            val n = random.nextInt(1, 300)
            val expected = items.sortedWith(compareByDescending<Item> { it.priority }.thenBy { it.key }).take(n)
            assertEquals(expected, treap.top(n).items())
        }
    }
}
