package tracker

import java.nio.file.Path
import tracker.store.ArrayBuffer

fun <T> ArrayBuffer<T>.items(): List<T> = List(size) { this[it] }

object Fixtures {
    fun path(name: String): Path {
        val url = Fixtures::class.java.getResource("/fixtures/$name") ?: error("Нет заготовленного файла $name")
        return Path.of(url.toURI())
    }
}
