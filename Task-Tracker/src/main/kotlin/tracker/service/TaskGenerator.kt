package tracker.service

import java.time.LocalDate
import kotlin.random.Random
import tracker.model.RegularTask
import tracker.model.TaskRules
import tracker.model.UrgentTask

class TaskGenerator(
    private val random: Random = Random.Default,
    private val today: LocalDate = LocalDate.now(),
) {
    fun next(id: Int): RegularTask {
        val title = "${ACTIONS.random(random)} ${OBJECTS.random(random)}"
        val assignee = PEOPLE.random(random)
        val deadline = today.plusDays(random.nextLong(-DAYS_BACK, DAYS_FORWARD + 1))
        val importance = random.nextInt(TaskRules.MIN_IMPORTANCE, TaskRules.MAX_IMPORTANCE + 1)
        return if (random.nextInt(PERCENT) < URGENT_PERCENT) {
            UrgentTask(id, title, assignee, deadline, importance, PEOPLE.random(random))
        } else {
            RegularTask(id, title, assignee, deadline, importance)
        }
    }

    private companion object {
        const val DAYS_BACK = 60L
        const val DAYS_FORWARD = 365L
        const val PERCENT = 100
        const val URGENT_PERCENT = 20

        val ACTIONS = arrayOf("Подготовить", "Проверить", "Исправить", "Обновить", "Согласовать", "Протестировать")
        val OBJECTS = arrayOf("отчёт", "сервер", "документацию", "релиз", "базу данных", "интерфейс", "договор")
        val PEOPLE = arrayOf("Иванов", "Петрова", "Сидоров", "Кузнецова", "Смирнов", "Попова", "Морозов")
    }
}
