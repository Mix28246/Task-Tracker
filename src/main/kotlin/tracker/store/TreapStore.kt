package tracker.store

import kotlin.random.Random

class TreapStore<K : Comparable<K>, T>(
    private val keyOf: (T) -> K,
    private val priorityOf: (T) -> Int,
    private val traceEnabled: Boolean = false,
    private val random: Random = Random.Default,
) : EntityStore<K, T> {

    private class Node<K, T>(val key: K, val value: T, val priority: Long) {
        var left: Node<K, T>? = null
        var right: Node<K, T>? = null
        var count: Int = 1
        var height: Int = 1

        val importance: Int
            get() = (priority shr PRIORITY_SHIFT).toInt()

        fun update() {
            count = 1 + countOf(left) + countOf(right)
            height = 1 + maxOf(heightOf(left), heightOf(right))
        }
    }

    private var root: Node<K, T>? = null

    override var size: Int = 0
        private set

    var lastPath: ArrayBuffer<K> = ArrayBuffer()
        private set

    private var trail: ArrayBuffer<K>? = null

    override fun add(entity: T): Boolean {
        val key = keyOf(entity)
        beginTrace()
        if (descend(key) != null) {
            endTrace()
            return false
        }
        beginTrace()
        root = insert(root, Node(key, entity, priorityFor(entity)))
        size++
        endTrace()
        return true
    }

    override fun findByKey(key: K): T? {
        beginTrace()
        val node = descend(key)
        endTrace()
        return node?.value
    }

    fun ceiling(key: K): T? {
        beginTrace()
        var current = root
        var candidate: Node<K, T>? = null
        while (current != null) {
            record(current)
            if (current.key >= key) {
                candidate = current
                current = current.left
            } else {
                current = current.right
            }
        }
        endTrace()
        return candidate?.value
    }

    override fun removeByKey(key: K): Boolean {
        beginTrace()
        val before = size
        root = remove(root, key)
        endTrace()
        return size < before
    }

    override fun clear() {
        root = null
        size = 0
        lastPath = ArrayBuffer()
    }

    override fun all(): ArrayBuffer<T> {
        val result = ArrayBuffer<T>(size)
        forEach { result.add(it) }
        return result
    }

    fun forEach(action: (T) -> Unit) {
        visitInOrder(root, action)
    }

    override fun range(from: K, to: K): ArrayBuffer<T> {
        val result = ArrayBuffer<T>()
        if (from <= to) collectRange(root, from, to, result)
        return result
    }

    override fun top(n: Int): ArrayBuffer<T> {
        val start = root
        if (n <= 0 || start == null) return ArrayBuffer()
        val frontier = NodeHeap<K, T> { a, b -> a.priority > b.priority }
        val chosen = NodeHeap<K, T> { a, b -> ranksHigher(b, a) }
        frontier.push(start)
        while (frontier.isNotEmpty()) {
            val node = frontier.pop()
            var rightMayRankHigher = true
            if (chosen.size < n) {
                chosen.push(node)
            } else {
                val worst = chosen.peek()
                if (node.importance < worst.importance) break
                if (ranksHigher(node, worst)) {
                    chosen.pop()
                    chosen.push(node)
                } else {
                    rightMayRankHigher = false
                }
            }
            node.left?.let { frontier.push(it) }
            if (rightMayRankHigher) node.right?.let { frontier.push(it) }
        }
        val ordered = arrayOfNulls<Any>(chosen.size)
        for (index in ordered.size - 1 downTo 0) ordered[index] = chosen.pop().value
        val result = ArrayBuffer<T>(ordered.size)
        @Suppress("UNCHECKED_CAST")
        for (item in ordered) result.add(item as T)
        return result
    }

    override fun valueAt(index: Int): T? {
        var current = root
        var remaining = index
        while (current != null) {
            val leftCount = countOf(current.left)
            current = when {
                remaining < leftCount -> current.left
                remaining == leftCount -> return current.value
                else -> {
                    remaining -= leftCount + 1
                    current.right
                }
            }
        }
        return null
    }

    fun maxKey(): K? {
        var current = root ?: return null
        while (true) {
            current = current.right ?: return current.key
        }
    }

    fun height(): Int = heightOf(root)

    fun copy(): TreapStore<K, T> {
        val copy = TreapStore(keyOf, priorityOf, traceEnabled, random)
        copy.root = copyOf(root)
        copy.size = size
        return copy
    }

    fun snapshot(maxDepth: Int, isFocused: (K) -> Boolean = { false }): TreeSnapshot<K, T> {
        val builder = SnapshotBuilder<K, T>(maxDepth.coerceAtLeast(0), isFocused)
        root?.let { builder.visit(it, 0) }
        return TreeSnapshot(builder.nodes, size, height())
    }

    @Suppress("UNCHECKED_CAST")
    fun inspect(limit: Int = Int.MAX_VALUE): StructureReport {
        var stack = arrayOfNulls<Any>(INITIAL_CAPACITY)
        var top = 0
        var current = root
        var visited = 0
        var keysOrdered = true
        var heapOrdered = true
        var subtreesConsistent = true
        var previous: K? = null
        var complete = true
        traversal@ while (current != null || top > 0) {
            while (current != null) {
                if (top >= limit) {
                    complete = false
                    break@traversal
                }
                if (top == stack.size) stack = grow(stack)
                stack[top++] = current
                current = current.left
            }
            val node = stack[--top] as Node<K, T>
            stack[top] = null
            visited++
            if (visited > limit) {
                complete = false
                break
            }
            val last = previous
            if (last != null && last >= node.key) keysOrdered = false
            previous = node.key
            val left = node.left
            val right = node.right
            if (left != null && left.priority > node.priority) heapOrdered = false
            if (right != null && right.priority > node.priority) heapOrdered = false
            if (node.count != 1 + countOf(left) + countOf(right)) subtreesConsistent = false
            if (node.height != 1 + maxOf(heightOf(left), heightOf(right))) subtreesConsistent = false
            current = right
        }
        return StructureReport(visited, size, keysOrdered, heapOrdered, subtreesConsistent, complete)
    }

    private fun ranksHigher(a: Node<K, T>, b: Node<K, T>): Boolean =
        a.importance > b.importance || (a.importance == b.importance && a.key < b.key)

    private fun copyOf(node: Node<K, T>?): Node<K, T>? {
        if (node == null) return null
        val copy = Node(node.key, node.value, node.priority)
        copy.left = copyOf(node.left)
        copy.right = copyOf(node.right)
        copy.count = node.count
        copy.height = node.height
        return copy
    }

    private fun priorityFor(entity: T): Long =
        (priorityOf(entity).toLong() shl PRIORITY_SHIFT) or (random.nextInt().toLong() and TIE_BREAK_MASK)

    private fun insert(node: Node<K, T>?, fresh: Node<K, T>): Node<K, T> {
        if (node == null) {
            record(fresh)
            return fresh
        }
        if (fresh.priority > node.priority) {
            val (less, rest) = split(node, fresh.key)
            fresh.left = less
            fresh.right = rest
            fresh.update()
            record(fresh)
            return fresh
        }
        record(node)
        if (fresh.key < node.key) node.left = insert(node.left, fresh) else node.right = insert(node.right, fresh)
        node.update()
        return node
    }

    private fun split(node: Node<K, T>?, key: K): Pair<Node<K, T>?, Node<K, T>?> {
        if (node == null) return Pair(null, null)
        record(node)
        return if (node.key < key) {
            val (less, rest) = split(node.right, key)
            node.right = less
            node.update()
            Pair(node, rest)
        } else {
            val (less, rest) = split(node.left, key)
            node.left = rest
            node.update()
            Pair(less, node)
        }
    }

    private fun merge(first: Node<K, T>?, second: Node<K, T>?): Node<K, T>? = when {
        first == null -> second
        second == null -> first
        first.priority > second.priority -> {
            first.right = merge(first.right, second)
            first.update()
            first
        }
        else -> {
            second.left = merge(first, second.left)
            second.update()
            second
        }
    }

    private fun remove(node: Node<K, T>?, key: K): Node<K, T>? {
        if (node == null) return null
        record(node)
        val comparison = key.compareTo(node.key)
        return when {
            comparison < 0 -> {
                node.left = remove(node.left, key)
                node.update()
                node
            }
            comparison > 0 -> {
                node.right = remove(node.right, key)
                node.update()
                node
            }
            else -> {
                size--
                merge(node.left, node.right)
            }
        }
    }

    private fun descend(key: K): Node<K, T>? {
        var current = root
        while (current != null) {
            record(current)
            val comparison = key.compareTo(current.key)
            if (comparison == 0) return current
            current = if (comparison < 0) current.left else current.right
        }
        return null
    }

    private fun visitInOrder(node: Node<K, T>?, action: (T) -> Unit) {
        if (node == null) return
        visitInOrder(node.left, action)
        action(node.value)
        visitInOrder(node.right, action)
    }

    private fun collectRange(node: Node<K, T>?, from: K, to: K, out: ArrayBuffer<T>) {
        if (node == null) return
        if (node.key > from) collectRange(node.left, from, to, out)
        if (node.key >= from && node.key <= to) out.add(node.value)
        if (node.key < to) collectRange(node.right, from, to, out)
    }

    private fun beginTrace() {
        trail = if (traceEnabled) ArrayBuffer() else null
    }

    private fun record(node: Node<K, T>) {
        trail?.add(node.key)
    }

    private fun endTrace() {
        trail?.let { lastPath = it }
        trail = null
    }

    private companion object {
        const val PRIORITY_SHIFT = 32
        const val TIE_BREAK_MASK = 0xFFFFFFFFL
        const val INITIAL_CAPACITY = 16

        fun countOf(node: Node<*, *>?): Int = node?.count ?: 0

        fun heightOf(node: Node<*, *>?): Int = node?.height ?: 0
    }

    private class NodeHeap<K, T>(private val above: (Node<K, T>, Node<K, T>) -> Boolean) {
        private var items = arrayOfNulls<Any>(INITIAL_CAPACITY)

        var size = 0
            private set

        fun isNotEmpty(): Boolean = size > 0

        fun peek(): Node<K, T> = at(0)

        fun push(node: Node<K, T>) {
            if (size == items.size) items = grow(items)
            var index = size++
            items[index] = node
            while (index > 0) {
                val parent = (index - 1) / 2
                if (!above(at(index), at(parent))) break
                swap(parent, index)
                index = parent
            }
        }

        fun pop(): Node<K, T> {
            val first = at(0)
            size--
            items[0] = items[size]
            items[size] = null
            var index = 0
            while (true) {
                val left = 2 * index + 1
                val right = left + 1
                var best = index
                if (left < size && above(at(left), at(best))) best = left
                if (right < size && above(at(right), at(best))) best = right
                if (best == index) break
                swap(index, best)
                index = best
            }
            return first
        }

        @Suppress("UNCHECKED_CAST")
        private fun at(index: Int): Node<K, T> = items[index] as Node<K, T>

        private fun swap(a: Int, b: Int) {
            val tmp = items[a]
            items[a] = items[b]
            items[b] = tmp
        }
    }

    private class SnapshotBuilder<K, T>(private val maxDepth: Int, private val isFocused: (K) -> Boolean) {
        val nodes = ArrayBuffer<SnapshotNode<K, T>>()
        private var column = 0

        fun visit(node: Node<K, T>, depth: Int): Int {
            val left = node.left
            val right = node.right
            val showLeft = left != null && shows(left, depth + 1)
            val showRight = right != null && shows(right, depth + 1)
            val hidden = (if (showLeft) 0 else countOf(left)) + (if (showRight) 0 else countOf(right))
            val leftIndex = if (showLeft) visit(left, depth + 1) else NO_PARENT
            val snapshotNode = SnapshotNode(node.key, node.value, depth, column++, hidden)
            val index = nodes.size
            nodes.add(snapshotNode)
            val rightIndex = if (showRight) visit(right, depth + 1) else NO_PARENT
            if (leftIndex != NO_PARENT) nodes[leftIndex].parent = index
            if (rightIndex != NO_PARENT) nodes[rightIndex].parent = index
            return index
        }

        private fun shows(node: Node<K, T>, depth: Int): Boolean = depth <= maxDepth || isFocused(node.key)
    }
}

const val NO_PARENT = -1

class TreeSnapshot<K, T>(
    val nodes: ArrayBuffer<SnapshotNode<K, T>>,
    val totalSize: Int,
    val height: Int,
)

class SnapshotNode<K, T>(
    val key: K,
    val value: T,
    val depth: Int,
    val column: Int,
    val hiddenBelow: Int,
) {
    var parent: Int = NO_PARENT
        internal set
}

data class StructureReport(
    val nodesFound: Int,
    val sizeCounter: Int,
    val keysOrdered: Boolean,
    val heapOrdered: Boolean,
    val subtreesConsistent: Boolean,
    val complete: Boolean,
) {
    val isValid: Boolean
        get() = complete && keysOrdered && heapOrdered && subtreesConsistent && nodesFound == sizeCounter
}
