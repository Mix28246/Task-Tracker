package tracker.io

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import java.io.Serializable
import tracker.model.Task
import tracker.store.ArrayBuffer

class TaskArchive @JsonCreator constructor(
    @param:JsonProperty("version") val version: Int,
    @param:JsonProperty("tasks") @param:JsonSetter(contentNulls = Nulls.FAIL) val tasks: Array<TaskRecord>,
) : Serializable {
    companion object {
        const val CURRENT_VERSION = 1
        private const val serialVersionUID = 1L

        fun of(tasks: ArrayBuffer<Task>): TaskArchive =
            TaskArchive(CURRENT_VERSION, Array(tasks.size) { index -> TaskRecord.of(tasks[index]) })
    }
}
