# Таск-трекер

Вариант 9. Kotlin 2.0, Compose Desktop, Gradle (Kotlin DSL), JDK 21.

## Запуск

Открыть папку в IntelliJ IDEA → дождаться синхронизации Gradle →
зелёная стрелка у `main` в `src/main/kotlin/tracker/Main.kt`.
Из терминала: `./gradlew run` (Windows: `gradlew.bat run`).

Примеры данных: `tasks.csv` — корректный файл, `tasks_broken.csv` — с битыми строками
(загрузятся 3 задачи, 8 строк будут пропущены с отчётом), `tasks_bad_header.csv` — неверный заголовок
(ошибка BAD_HEADER, файл не загружается).

## Структура

| Файл | Что внутри |
|---|---|
| `Main.kt` | точка входа: создаёт окно |
| `model/Task.kt` | `Task` (базовый, sealed), `RegularTask`, `UrgentTask` (+ дежурный), `ArchivedTask` (read-only), `Editable`, `TaskType`, `TaskRules` |
| `csv/TaskCsv.kt` | формат файла `TaskCsvFormat`, загрузка `CsvLoader`, сохранение `CsvWriter` |
| `csv/CsvErrors.kt` | коды ошибок `CsvErrorCode`, исключения `CsvLineException` / `CsvFileException` |
| `store/TaskRepository.kt` | хранилище задач (в лаб. 2 заменится на декартово дерево) |
| `service/TaskService.kt` | операции: загрузить, сохранить, добавить, изменить |
| `ui/AppState.kt` | состояние окна, обработка действий, форма диалога `TaskForm` |
| `ui/App.kt` | главное окно: кнопки, строка состояния, окно выбора файла |
| `ui/TaskTable.kt` | таблица задач |
| `ui/Dialogs.kt` | диалоги: добавление/изменение, ошибка, отчёт о загрузке |

## Иерархия

```
Task (sealed, все поля val)
├── RegularTask : Editable        — обычная, редактируемая
│   └── UrgentTask                — срочная, + dutyPerson
└── ArchivedTask                  — архивная, read-only (только из файла)
```

## Формат CSV

```
type;id;title;assignee;deadline;importance;dutyPerson
URGENT;2;Починить падение сервера;Анна Смирнова;2026-10-03;10;Пётр Иванов
ARCHIVED;4;Настроить репозиторий;Иван Петров;2026-09-01;3;
```

Всегда 7 полей, кодировка UTF-8, дата ГГГГ-ММ-ДД, важность 1–10.
Символ `;` внутри текстовых полей запрещён (проверяется при вводе).
