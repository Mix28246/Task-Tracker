# Таск-трекер

Лабораторные работы по курсу «Программирование на Java» (Kotlin), вариант 9 — «Таск-трекер».
Kotlin 2.0, Compose Desktop 1.7, Jackson 2.17, Gradle, JDK 21.

## Запуск

Открыть папку в IntelliJ IDEA → дождаться синхронизации Gradle →
зелёная стрелка у `main` в `src/main/kotlin/tracker/Main.kt`.

Из терминала (Windows — `gradlew.bat` вместо `./gradlew`):

| Команда | Что делает |
|---|---|
| `./gradlew run` | запускает программу |
| `./gradlew test` | автотесты, отчёт в `build/reports/tests/test/index.html` |
| `./gradlew formats` | сравнение форматов CSV, JSON и бинарного → `build/reports/formats/` |
| `./gradlew benchmark` | замеры дерева и сравнение с `TreeMap` → `build/reports/benchmark/` |
| `./gradlew race` | гонка потоков без защиты и с `synchronized` → `build/reports/race/race.txt` |

Примеры данных: `tasks.csv` — корректный файл, `tasks_broken.csv` — с битыми строками
(загрузятся 3 задачи, 8 строк будут пропущены с отчётом), `tasks_bad_header.csv` — неверный заголовок.
Эталонные и битые файлы JSON и бинарного формата — в `src/test/resources/fixtures/`.

## Что умеет

**Лабораторная 1 — модель данных.** Три типа задач, интерфейс `Editable`, загрузка и сохранение CSV
с пропуском битых строк и кодами ошибок, добавление и изменение в окне.

**Лабораторная 2 — своя структура данных и асинхронность.**
- Хранилище — собственное декартово дерево `TreapStore<K, T>`: ключ — срок (при равных сроках — ID),
  приоритет кучи — важность. Реализация через split/merge, без коллекций `java.util`.
- Операции: вставка, поиск, удаление, задачи в интервале сроков, N самых важных.
- Вкладка «Дерево»: верхние 5 уровней с подсветкой пути последней операции.
- Генерация, импорт и экспорт — в фоновом потоке с прогрессом; генерацию и импорт можно отменить.
- Замеры и сравнение с `TreeMap` (`./gradlew benchmark`), гонка потоков (`./gradlew race`).

**Лабораторная 3 — сериализация.**
- Общий интерфейс `Serializer<T>` и две реализации: `JsonTaskSerializer` (Jackson)
  и `BinaryTaskSerializer` (`java.io.Serializable`).
- В файл пишется `TaskArchive`: номер версии формата и массив записей `TaskRecord`.
  У классов архива есть `serialVersionUID`. Файл другой версии не загружается — ошибка `UNSUPPORTED_VERSION`.
- Кнопки «Импорт…» и «Экспорт…»: формат выбирается фильтром в окне выбора файла (CSV, JSON, бинарный).
  После импорта обновляются таблица и дерево.
- Битые файлы: пустой, обрезанный, с синтаксической ошибкой, без нужного поля, другой версии, чужого формата —
  понятная ошибка с кодом. Некорректные записи внутри файла пропускаются с отчётом, как строки CSV.
- Тесты на заготовленных файлах из `src/test/resources/fixtures/`, сравнение форматов — `./gradlew formats`.

## Структура

| Файл | Что внутри |
|---|---|
| `Main.kt` | точка входа: создаёт окно |
| `model/Task.kt` | `Task` (sealed), `RegularTask`, `UrgentTask` (+ дежурный), `ArchivedTask` (read-only), `Editable`, `TaskType`, `TaskRules` |
| `model/TaskKey.kt` | ключ дерева: срок + ID |
| `model/DisplayDate.kt` | дата в окне в формате ДД.ММ.ГГ |
| `store/EntityStore.kt`, `store/TreapStore.kt` | интерфейс хранилища и декартово дерево |
| `store/ArrayBuffer.kt` | свой расширяемый массив для выдачи результатов |
| `store/SynchronizedStore.kt` | потокобезопасная обёртка над любым хранилищем |
| `store/TaskRepository.kt` | дерево по сроку + дерево-индекс по ID под одной блокировкой |
| `service/TaskService.kt` | операции над задачами и правила |
| `service/TaskGenerator.kt` | генератор случайных задач |
| `io/Serializer.kt` | интерфейс `Serializer<T>` |
| `io/TaskArchive.kt`, `io/TaskRecord.kt` | что пишется в файл: версия и записи задач |
| `io/JsonTaskSerializer.kt`, `io/BinaryTaskSerializer.kt` | JSON и бинарный формат |
| `io/TaskCsv.kt` | формат CSV, потоковая загрузка и сохранение |
| `io/TaskFiles.kt` | выбор формата по расширению, импорт с пропуском битых записей |
| `io/DataErrors.kt` | коды ошибок и исключения загрузки |
| `concurrent/Background.kt` | токен отмены и слушатель прогресса |
| `ui/AppState.kt` | действия пользователя, диалоги, фоновые операции |
| `ui/BackgroundRunner.kt` | фоновый поток с прогрессом и отменой |
| `ui/App.kt` | окно, панель кнопок, выбор файла |
| `ui/TableScreen.kt` | вкладка «Таблица»: фильтры и таблица |
| `ui/TreeScreen.kt` | вкладка «Дерево»: снимок, раскладка узлов, рисунок |
| `ui/Dialogs.kt`, `ui/TaskForm.kt` | диалоги и форма задачи |
| `src/test/kotlin/` | автотесты (JUnit 5) |
| `src/test/kotlin/tracker/bench/` | эксперименты: форматы, замеры, гонка потоков |

## Форматы файлов

- **CSV:** `type;id;title;assignee;deadline;importance;dutyPerson`, UTF-8, дата `ГГГГ-ММ-ДД`, всегда 7 полей.
  `type` — `REGULAR`, `URGENT` или `ARCHIVED`; `dutyPerson` заполняется только у `URGENT`.
- **JSON:** `{"version": 1, "tasks": [{"type": ..., "id": ..., ...}]}` — те же поля, что в CSV.
- **Бинарный (`.bin`):** объект `TaskArchive`, записанный `ObjectOutputStream`.

## CI

GitHub Actions собирает проект и запускает тесты при каждом пуше в любую ветку
(`.github/workflows/build.yml`), а по тегу `v*` собирает установщики (`release.yml`).
