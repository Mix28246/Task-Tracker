package tracker.store

class ArrayBuffer<T>(initialCapacity: Int = DEFAULT_CAPACITY) {
    private var items = arrayOfNulls<Any>(initialCapacity.coerceAtLeast(1))

    var size: Int = 0
        private set

    fun isEmpty(): Boolean = size == 0

    fun add(item: T) {
        if (size == items.size) items = grow(items)
        items[size++] = item
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index !in 0 until size) throw IndexOutOfBoundsException("Индекс $index вне 0..${size - 1}")
        return items[index] as T
    }

    fun getOrNull(index: Int): T? = if (index in 0 until size) get(index) else null

    fun last(): T = get(size - 1)

    inline fun forEach(action: (T) -> Unit) {
        for (index in 0 until size) action(get(index))
    }

    operator fun contains(item: T): Boolean {
        for (index in 0 until size) if (get(index) == item) return true
        return false
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
    }
}

internal fun grow(array: Array<Any?>): Array<Any?> {
    val bigger = arrayOfNulls<Any>(array.size * 2)
    System.arraycopy(array, 0, bigger, 0, array.size)
    return bigger
}
