package tracker.store

class SynchronizedStore<K : Comparable<K>, T>(private val inner: EntityStore<K, T>) : EntityStore<K, T> {
    private val lock = Any()

    override val size: Int
        get() = synchronized(lock) { inner.size }

    override fun add(entity: T): Boolean = synchronized(lock) { inner.add(entity) }

    override fun findByKey(key: K): T? = synchronized(lock) { inner.findByKey(key) }

    override fun removeByKey(key: K): Boolean = synchronized(lock) { inner.removeByKey(key) }

    override fun clear() = synchronized(lock) { inner.clear() }

    override fun all(): ArrayBuffer<T> = synchronized(lock) { inner.all() }

    override fun range(from: K, to: K): ArrayBuffer<T> = synchronized(lock) { inner.range(from, to) }

    override fun top(n: Int): ArrayBuffer<T> = synchronized(lock) { inner.top(n) }

    override fun valueAt(index: Int): T? = synchronized(lock) { inner.valueAt(index) }
}
