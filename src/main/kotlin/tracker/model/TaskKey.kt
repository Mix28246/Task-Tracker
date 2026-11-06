package tracker.model

import java.time.LocalDate

data class TaskKey(val deadline: LocalDate, val id: Int) : Comparable<TaskKey> {
    override fun compareTo(other: TaskKey): Int {
        val byDeadline = deadline.compareTo(other.deadline)
        return if (byDeadline != 0) byDeadline else id.compareTo(other.id)
    }

    override fun toString(): String = "$deadline #$id"

    companion object {
        fun firstOf(date: LocalDate): TaskKey = TaskKey(date, Int.MIN_VALUE)
        fun lastOf(date: LocalDate): TaskKey = TaskKey(date, Int.MAX_VALUE)
    }
}

val Task.key: TaskKey
    get() = TaskKey(deadline, id)
