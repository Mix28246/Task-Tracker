package tracker.bench

import java.util.PriorityQueue
import java.util.TreeMap
import tracker.model.Task
import tracker.model.TaskKey
import tracker.model.key

class TreeMapTarget : BenchTarget {
    private val map = TreeMap<TaskKey, Task>()

    override fun insertAll(tasks: Array<Task>) {
        for (task in tasks) map[task.key] = task
    }

    override fun findAll(keys: Array<TaskKey>): Long {
        var sum = 0L
        for (key in keys) sum += map[key]?.id ?: 0
        return sum
    }

    override fun rangeCount(from: TaskKey, to: TaskKey): Int =
        ArrayList(map.subMap(from, true, to, true).values).size

    override fun topCount(n: Int): Int {
        val worstFirst = PriorityQueue(n + 1, BY_RANK.reversed())
        for (task in map.values) {
            worstFirst.add(task)
            if (worstFirst.size > n) worstFirst.poll()
        }
        return worstFirst.size
    }

    override fun removeAll(keys: Array<TaskKey>): Long {
        var removed = 0L
        for (key in keys) if (map.remove(key) != null) removed++
        return removed
    }

    override fun height(): Int = NO_HEIGHT

    override fun isEmpty(): Boolean = map.isEmpty()

    companion object {
        private const val NO_HEIGHT = -1
        private val BY_RANK = compareByDescending<Task> { it.importance }.thenBy { it.key }
        val SPEC = BenchTargetSpec("TreeMap + PriorityQueue") { TreeMapTarget() }
    }
}
