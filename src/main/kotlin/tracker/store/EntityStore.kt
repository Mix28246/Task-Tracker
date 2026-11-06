package tracker.store

interface EntityStore<K : Comparable<K>, T> {
    val size: Int

    fun add(entity: T): Boolean

    fun findByKey(key: K): T?

    fun removeByKey(key: K): Boolean

    fun clear()

    fun all(): ArrayBuffer<T>

    fun range(from: K, to: K): ArrayBuffer<T>

    fun top(n: Int): ArrayBuffer<T>

    fun valueAt(index: Int): T?
}
