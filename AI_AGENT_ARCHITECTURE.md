# AI-агент в Askya: технический аудит и план v0.1

> Состояние кода на 26 сентября 2026, коммит `af166aa`, схема базы — версия 48.
> Документ — только анализ. Код, `build.gradle*` и зависимости не менялись.
> Все классы, методы и таблицы ниже взяты из исходников. Всё, чего в проекте
> ещё нет, помечено словом **новое**.

---

## 0. Главное до чтения остального

1. **Агент в облаке нарушает заявленный принцип приложения.** README («Про сеть
   и приватность») обещает: «ни одна запись никуда не уходит, облака нет».
   В сеть сейчас уходят только координаты для погоды, ссылка на видео,
   набранная человеком, и запрос к GitHub за обновлением. Облачная модель
   получит расписание, заметки и напоминания. Это решение о продукте, а не о
   коде, и принимать его нужно до первой строки (варианты — в §11–12).
   Раньше модель в Askya уже была и была убрана («Без модели»): ключ
   `claude_key` и рассказ о себе `about_me` до сих пор стираются при каждом
   запуске (`SettingsPreferences.dropRemovedModelSecrets`). Приложение стоит на
   двух телефонах, и один из них чужой.
2. **Threads, nodes и edges в проекте нет.** Таблицы `threads`, `thread_nodes`
   и `thread_ties` стёрты миграцией 44 → 45 вместе с разделом (комментарий к
   `AppDatabase`). Опираться на них нельзя.
3. **Сущности «задача» в Askya нет.** Ближе всего к ней три вещи:
   дело дня `ScheduleItem`, у которого начало обязательно; строка списка
   внутри дела `DeedTask`; пункт списка Yet `YetItem`. В v0.1 под
   `create_task` понимается **дело дня** (§9).
4. **Логика создания лежит в ViewModel, а не в репозиториях.** Дело вместе с
   напоминанием заводит `AskyaDayViewModel.save` вместе с приватным
   `applyRemind`, отдельное напоминание — `RemindersViewModel.save`.
   Репозитории только пишут строку, будильник они не ставят. Инструменты агента
   должны повторить ту же последовательность, иначе появится напоминание без
   будильника.
5. **Поиск по базе не находит кириллицу без учёта регистра.**
   `NoteDao.observeFiltered` ищет через `LIKE`, а `LIKE` в SQLite игнорирует
   регистр только у ASCII: запрос «дача» не найдёт «Дача». Экран Scroll ищет
   иначе: словами, в Kotlin, через `lowercase` (`ScrollScreen.kt`, приватные
   `askOf` и `Note.matches`). Агенту нужен второй способ.
6. **Все нужные данные лежат в модуле `shared`.** Он общий у телефона и
   Windows-версии, поэтому ядро агента, положенное туда, заработает на обеих
   системах.

---

## 1. Архитектура проекта

### Модули

| Модуль | Что внутри | Цели сборки |
|---|---|---|
| `:shared` | База Room, сущности, DAO, репозитории, настройки, домен, экраны AskyaDay / Scroll / Ledger / Yet / Routine / Reminders / NoteEdit, навигация | Kotlin Multiplatform: `androidTarget()` и `jvm("desktop")` |
| `:app` | Только Android: `AskyaApplication`, `MainActivity`, `AndroidContainer`, Echo, AskyaV, виджеты, будильники, шторка, мосты (экран), погода, Слепок, обновления, `AskyaLibrary` | Android |
| `:desktop` | Windows-версия: `DesktopContainer`, `DesktopAlarms` | JVM |

`commonMain` фактически JVM-код: в нём используются `java.time`,
`javax.crypto` и `java.security`. Поэтому `java.net.HttpURLConnection` доступен и
в общем коде.

### Пакеты (`app.askya.*`)

```
app/            AppContainer (shared), AndroidContainer, AskyaApplication, MainActivity,
                Incoming (намерения), FileOpenRouter, OpenRoutes
data/db/        AppDatabase (v48), Transactions (withTransaction), dao/ (13 DAO), Migrations (android)
data/entity/    Note, ScrollTopic, ImageAlbum, ScheduleItem, DeedTask, RoutineItem, GeneratedDay,
                Reminder, Bridge, YetList, YetItem, Ledger*, Echo*, VideoPlaylist*, SyncClock, SyncState
data/repository/ NoteRepository, ScheduleRepository, DeedTaskRepository, ReminderRepository,
                RoutineRepository, DayRepository, YetRepository, LedgerRepository, BridgeRepository, Trash
                (app: EchoRepository, VideoRepository)
data/preferences/ SettingsPreferences, ReaderPreferences, WeatherPreferences (shared);
                EchoPreferences, VideoPreferences (app)
data/sync/      Uid, SyncSchema, SyncTables, SyncEngine, SyncStore, Pack, Crypt, CloudAccount, Json, Rows
data/account/   AccountPreferences, AccountGate
data/library/   AskyaLibrary (app)
data/backup/    Snapshots, SnapshotAlarms (app) — «Слепок»
domain/         model/ (BlockIcon, RemindAt, DayPlan, DeedLink, DeedDays, Money…), plan/ (DayComposer,
                RoutineDayComposer, SameEvent), lived/, markdown/, docs/, account/ (Secrets, AccountRules)
reminders/      ReminderClock, dropReminders, moveReminder (shared); ReminderAlarms, приёмники (app)
bridges/        DeedTarget, crossBridge (shared); BridgeApps (android)
ui/             navigation/, components/, askyaday/, scroll/, noteedit/, yet/, routine/, reminders/,
                ledger/, account/, settings/, theme/ (shared); bridges/, echo/, video/, weather/, launch/ (app)
widget/ shade/ echo/ video/ weather/ update/   (app)
```

### UI / Compose

- Одна Activity (`MainActivity`) и Compose Multiplatform 1.9.3 в `shared`;
  Material 3 из compose-bom `2025.10.01` в `app`.
- Корень кладёт контейнер в дерево:
  `CompositionLocalProvider(LocalAppContainer provides container())`
  (`MainActivity.kt:85`). Экраны достают его через `appContainer()`, а экраны
  только для Android — через `androidContainer()`.
- Общие компоненты лежат в `ui/components/`. Для окна агента пригодятся
  `CardDialog`, `AskyaDialog`, `ScreenScaffold`, `ScreenHeader`, `Composer`,
  `Dictation`, `UndoBar` и разборщики `TypedDate` / `TypedTime` / `TypedRemind`.

### ViewModel

Все ViewModel лежат в `shared` и получают зависимости через собственную
фабрику `companion object { fun factory(container: AppContainer) =
viewModelFactory { … } }`. Экран создаёт их так:
`viewModel(factory = AskyaDayViewModel.factory(container))`
(`AskyaDayScreen.kt:168`).

| ViewModel | Зависимости |
|---|---|
| `AskyaDayViewModel` | ScheduleRepository, RoutineRepository, DayRepository, ReminderRepository, ReminderClock, DeedTaskRepository |
| `RemindersViewModel` | ReminderRepository, ReminderClock |
| `RoutineViewModel` | RoutineRepository, ReminderRepository, ReminderClock |
| `ScrollViewModel` | NoteRepository, YetRepository, Trash |
| `NoteEditViewModel` | NoteRepository |
| `YetViewModel` | YetRepository |
| `LedgerViewModel` | LedgerRepository, Trash |

Экран «Мосты» (`BridgesScreen`) обходится без ViewModel и работает с
контейнером напрямую.

### Repository

Устроены вручную, без слоя use case: README («Архитектура») прямо говорит, что
use case здесь «был бы прослойкой из однострочников». Репозиторий отдаёт
`Flow` для экранов и `suspend`-функции для разовых чтений.

### Room / SQLite

`AppDatabase`, версия 48, `exportSchema = true` (схемы лежат в
`shared/schemas/`). 21 сущность, `TypeConverters(Converters)`, база
собирается через `AppDatabaseConstructor`, имя файла — `askya.db`. Миграции
расписаны вручную (`androidMain/.../Migrations.kt`, около 1600 строк).
На таблицах стоят триггеры журнала правок (`SyncSchema.triggers()`): любая
вставка пишет строку в `sync_state`, так что агентские записи попадут в
синхронизацию автоматически.

### Preferences

DataStore Preferences, у каждого дела своё хранилище:

| Хранилище | Класс | Где создаётся |
|---|---|---|
| `settings` | `SettingsPreferences` → `AppSettings` | `PreferenceStores.android.kt` |
| `reader` | `ReaderPreferences` | там же |
| `weather` | `WeatherPreferences` | там же |
| `echo` | `EchoPreferences` | app |
| `video` | `VideoPreferences` | app |
| (своё) | `AccountPreferences` | `shared/androidMain/.../data/account` |

Слепок копирует хранилища из списка `Snapshots.STORES = listOf("settings",
"echo", "reader", "video", "weather")`. Системная резервная копия выключена
(`allowBackup="false"`).

### Навигация

Navigation Compose. Разделы задаёт `Destination` (TODAY `today`, NOTES
`notes`, ECHO `practices`, VIDEO `video`, LEDGER `ledger`), подстраницы —
`Routes` (`settings`, `reminders`, `bridges`, `lived`, `weather`,
`scroll/lists`, `yet/{listId}`, `note/{noteId}` и другие). Граф строит
`AskyaNavHost`. Маршруты, которые есть только у телефона, достраивает
`PlatformShell.routes`, а заполняет его `rememberAndroidShell()` →
`androidRoutes(nav)` (`AndroidShell.kt:124`). Боковое меню — `AppDrawer`, в нём
есть кнопка быстрой заметки `onQuickNote`.

---

## 2–3. Сущности пользовательских данных

«Чтение», «создание», «изменение» и «удаление» ниже — реальные методы проекта.
Удаление почти везде мягкое: поле `removedAt` плюс корзина на сутки (`Trash`),
окончательно строки стираются через `purgeTrash()` при запуске.

### Дело дня — `ScheduleItem` (таблица `schedule_items`) — «расписание» и «задача»

- **Поля:** `id, uid, date, startTime (обязательно), endTime?, title, note,
  done, icon: BlockIcon?, link: String?, removedAt?`.
- **Хранение:** `ScheduleDao`.
- **Repository:** `ScheduleRepository`; сборка дня — `DayRepository`.
- **ViewModel / UI:** `AskyaDayViewModel` / `AskyaDayScreen` (`ui/askyaday/`).
- **Чтение:** `itemsOn(date): Flow`, `itemsOnce(date)`, `get(id)`,
  `busyDates(from, to): Flow`, `lived(from, to)`.
- **Создание:** `add(item): Long`. Полный путь с напоминанием —
  `AskyaDayViewModel.save(existing = null, …)`: `schedule.add(...)`, затем
  `applyRemind` (`dropReminders` → `reminderOf` → `reminders.add` →
  `alarms.schedule`).
- **Изменение:** `save(item)`, `setDone(id, done)`,
  `AskyaDayViewModel.swapTimes` с `moveReminder`.
- **Удаление:** `remove(id)` (в корзину) вместе с `dropReminders`;
  `restore(id)`, `delete(id)`, `clearDay(date)`.
- `link` — строка `DeedLink.store()` вида `kind:id` (`bridge`, `book`, `note`,
  `yet`, `echo`, `video`).
- Шторка (`TaskShade`), виджет дня и экран блокировки подписаны на
  `itemsOn(today)` в `AskyaApplication`. Новое дело на сегодня обновит их само.

### Строка списка внутри дела — `DeedTask` (`deed_tasks`)

- **Поля:** `id, uid, deedId → schedule_items.id, text, done, heading,
  createdAt, removedAt?`.
- **Хранение / Repository:** `DeedTaskDao` / `DeedTaskRepository`.
- **ViewModel / UI:** `AskyaDayViewModel` (`addTasks`, `toggleTask`,
  `removeTask`, `clearDoneTasks`); компонент `CardTasks`; шторка
  `TaskShade`.
- **Чтение:** `tasksOf(deedId): Flow`, `tasksOn(date): Flow`,
  `tasksOnce(date)`, `deedsWithTasksOn(date)`, `get(id)`.
- **Создание:** `addLines(deedId, source)`. Разбирает строки, строки вида
  `# …` становятся заголовками.
- **Изменение:** `toggle(task)`, `toggle(id)`.
- **Удаление:** `remove(id)`, `restore(id)`, `clearDone(deedId)`,
  `deleteOf(deedId)`.

### Список дел (распорядок) — `RoutineItem` (`routine_items`) и `GeneratedDay` (`generated_days`) — «routines»

- **Поля:** `id, uid, title, startTime, endTime?, priority, enabled, icon?,
  link?, days?` (дни недели, `DeedDays`). `standsAlone` — у дела важность
  `HIGH` или выбраны дни недели.
- **Хранение / Repository:** `RoutineDao` / `RoutineRepository`, сборка —
  `DayRepository` вместе с `RoutineDayComposer`.
- **ViewModel / UI:** `RoutineViewModel` (`ui/routine/`); открывается из
  шапки AskyaDay.
- **Чтение:** `items(): Flow`, `get(id)`.
- **Создание:** `add(item)`, `addToDay(date, items)`, `applyToDays(item)`.
- **Изменение:** `save(item)`.
- **Удаление:** `delete(id)`.
- **Сборка дня:** `DayRepository.ensureComposed(date)`,
  `ensureStanding(date)`, `recompose(date)`. Их вызывает
  `AskyaDayViewModel.openDay` при открытии дня, и только если включено
  `AppSettings.autoFillDay`.

### Напоминание — `Reminder` (`reminders`)

- **Поля:** `id, uid, title, date, time` (момент звонка), `enabled, silent,
  sound?, soundTitle?, itemId? → schedule_items.id, eventDate, eventStart?,
  eventEnd?, lead?` (за сколько минут), `icon?`. Строится фабрикой
  `reminderOf(title, eventDate, eventStart, remind: RemindAt, …)`, где
  `RemindAt` бывает `Exact(time)` или `Before(minutes)`.
- **Хранение / Repository:** `ReminderDao` / `ReminderRepository`.
  Будильник — интерфейс `ReminderClock`; на телефоне `ReminderAlarms`
  (`AlarmManager` и уведомление), на компьютере `DesktopAlarms`.
- **ViewModel / UI:** `RemindersViewModel` / экран `Routes.REMINDERS`;
  напоминание о деле — из карточки дела в `AskyaDayViewModel`.
- **Чтение:** `reminders(): Flow`, `forItems(): Flow`, `forItems(ids)`,
  `get(id)`, `enabled()`.
- **Создание:** `add(reminder)` и **обязательно** `alarms.schedule(reminder.copy(id = id))`.
- **Изменение:** `save(reminder)`, `RemindersViewModel.setEnabled`,
  `moveReminder(...)`.
- **Удаление:** `delete(reminder)` вместе с `alarms.cancel(id)`;
  `dropReminders(clock, reminders, itemIds)`.

### Заметки и файлы Scroll — `Note` (`notes`), `ScrollTopic` (`scroll_topics`), `ImageAlbum` (`image_albums`)

- **Поля `Note`:** `id, uid, title, body, tags: List<String>, topicId?,
  albumId?, uri?, mime, isImage, durationMs, createdAt, updatedAt,
  removedAt?`. При `uri == null` это текстовая заметка. `mime audio/*` —
  голосовая (`voice`), `isImage` — картинка.
- **Хранение:** `NoteDao`, `TopicDao`, `AlbumDao`.
- **Repository:** `NoteRepository` (заметки, книги-темы, альбомы, картинки,
  голос).
- **ViewModel / UI:** `ScrollViewModel` / `ScrollScreen`, `LibraryScreen`;
  `NoteEditViewModel` / редактор; быстрая заметка — `QuickNoteCard` (вызывает
  `notes.quickNote(...)` напрямую).
- **Чтение:** `notes(): Flow`, `notes(query, tag): Flow` (→
  `observeFiltered`), `note(id): Flow`, `get(id)`, `loose()`,
  `inTopic(topicId)`, `voices()`, `images()`, `topics()`, `shelfOf(id)`.
- **Создание:** `quickNote(title, body): Long` (готовый текст, без книги),
  `createNote(topicId)` (пустая заметка для правки), `create()`,
  `addFile(...)`, `addVoice(...)`, `addTopic(...)`.
- **Изменение:** `save(note)` (сам ставит `updatedAt`), `captionImage`,
  `moveImages`, `updateTopic`.
- **Удаление:** `remove(id)`, `restore(id)`, `delete(note)`,
  `deleteTopic(id)`, `deleteAlbum(id)`.

### Списки Yet — `YetList` (`yet_lists`), `YetItem` (`yet_items`) — «списки»

- **Хранение / Repository:** `YetDao` / `YetRepository`.
- **ViewModel / UI:** `YetViewModel`; `ScrollViewModel` (`lists`,
  `listItems`); маршруты `scroll/lists` и `yet/{listId}`.
- **Чтение:** `lists()`, `recentLists(limit)`, `list(id)`, `items(listId)`,
  `itemsByList()`, `remaining()`, `sizes()`.
- **Создание:** `addList(title, mark)`, `addLines(listId, source)`.
- **Изменение:** `updateList`, `toggle(item)`.
- **Удаление:** `deleteList`, `removeItem`, `restoreItem`, `deleteItem`,
  `clearDone`.

### Финансы Ledger — `LedgerAccount`, `LedgerCategory`, `LedgerEntry` (`ledger_*`)

- **Хранение / Repository:** `LedgerDao` / `LedgerRepository`.
- **ViewModel / UI:** `LedgerViewModel` / `ui/ledger/`.
- **Чтение:** `accounts()`, `wealth()`, `categories()`, `month(ym)`,
  `onAccount(id)`, `stats()`, `edge()`.
- **Запись:** `save(entry)`, `saveAccount`, `saveCategory`,
  `reorderAccounts`, `ensureStarted()`.
- **Удаление:** `remove(id)`, `restore(id)`, `deleteAccount(id)`,
  `forgetCategory(id)`.
- Суммы хранятся в копейках (`Long`). **В v0.1 не трогается** ни на чтение,
  ни на запись (§8).

### Мосты — `Bridge` (`bridges`) — «bridges»

- **Поля:** `id, name, target` (пакет приложения или адрес), `kind: APP | LINK,
  icon: BlockIcon?, usedAt`.
- **Хранение / Repository:** `BridgeDao` / `BridgeRepository` (`bridges()`,
  `all()`, `get`, `save`, `delete`, `markUsed`).
- **UI:** `BridgesScreen` (`app/.../ui/bridges`, маршрут `bridges`, только на
  телефоне), `AppPickCard`.
- **Правило:** `deedTarget(bridges, link, routineLink, title, icon)` в
  `bridges/DeedTarget.kt`, три слоя от частного к общему. Первый — привязка
  дела `ScheduleItem.link`, второй — привязка строки списка дел
  `RoutineItem.link`, третий — мост на знаке дела (`BlockIcons.of(title)`).
  Переход наружу: `crossBridge(context, bridge)` → `BridgeApps.cross`.
- Для агента мосты полезны только на чтение: подсказать, чем делается дело.
  Переход по мосту — это действие на телефоне, его агенту давать нельзя.

### Threads / nodes / edges

**Не существуют.** Раздел и таблицы (`threads`, `thread_nodes`,
`thread_ties`) убраны миграцией 44 → 45 по просьбе человека. Вместе с ними
ушли дела с привязкой `thread:…`, списки Yet и траты. Похожего на граф в
проекте сейчас ничего нет. Ближайшая связь между сущностями — `DeedLink`
(дело → книга, заметка, список или мост).

### Остальное, что может пригодиться агенту позже

| Данные | Где |
|---|---|
| «Прожитое» — год по делам | `ScheduleRepository.lived(from, to)`, `domain/lived/Lived`, `Watched` |
| Голосовые заметки | `Note` с `mime audio/*`, `NoteRepository.voices()`, `VoiceFiles` |
| Погода | `WeatherRepository.state` (app), `WeatherPreferences` |
| Музыка и видео | `EchoRepository`, `VideoRepository` (app) — агенту v0.1 не нужны |
| Отслеживаемые дела | `AppSettings.watchedDeeds` |
| Журнал правок | `sync_state`, `SyncDao` — служебное, агенту не нужно |

---

## 4. Существующие механизмы

| Механизм | Есть ли | Где | Что важно для агента |
|---|---|---|---|
| Глобальный поиск | **Нет, убран** | README «Поиска по всему больше нет»: удалены `SearchRepository` и `search` в шести DAO | Остались поиск по Scroll (`ScrollScreen`: `askOf`, `Note.matches`, `matches(YetList…)`, всё `private`), по полке Библиотеки (`LibraryScreen`) и внутри книги (`BookReaderScreen.search`). `NoteDao.observeFiltered` — единственный поиск в SQL |
| Голосовой ввод | Есть | `ui/components/Dictation.kt`: `expect rememberDictation(onHeard, onProblem)`, `DICTATION_AVAILABLE`; Android — `Dictation.android.kt` (`SpeechRecognizer`) | Готовый микрофон для поля ввода агента. На Windows его нет. Распознавание системное и на части телефонов идёт через сеть (README) |
| Голосовые заметки | Есть | `VoiceRecorder`, `VoiceStore`, виджет `VoiceWidgetProvider`, маршрут `scroll/voice`, флаг `SAY_NOW` | Можно позже: «сказать агенту» из виджета |
| Уведомления | Есть | Каналы: `ReminderAlarms`, `TaskShade`, `DayLockScreen`, `SnapshotAlarms`, `EchoService` | Разрешение `POST_NOTIFICATIONS` спрашивается при первом напоминании |
| Напоминания | Есть | `ReminderClock` → `ReminderAlarms` (точный `AlarmManager`), `ReminderReceiver`, `ReminderBootReceiver` | Точный будильник человек может отнять: проверка `ReminderClock.exact` |
| Фоновые задачи | Только будильники и корутины | WorkManager нет сознательно (комментарии в `ReminderAlarms`, `SnapshotAlarms`); `AskyaApplication` запускает корутины `scope.launch` при старте | Фонового агента в архитектуре нет, и в v0.1 он не нужен |
| Deep links | Намерения, не URI | `Incoming.kt`: `OPEN_ROUTE` (`askya.open`), `OPEN_DEED` (`askya.deed`), `SAY_NOW`; `OpenRoutes.kt`: `today`, `weather`, `voice`, `echo`. Схемы `askya://` нет; `intent-filter` только на файлы и `SEND` | Ответ агента может вести внутрь через `routeOf(DeedLink, note)` (`LinkRoutes.kt`) |
| Виджеты | Есть | `DayWidgetProvider`, `WeatherWidgetProvider`, `VoiceWidgetProvider` | Сами обновятся после записи дела на сегодня |
| Backup / restore | «Слепок» | `Snapshots` (zip: база + хранилища `STORES` + картинки), `SnapshotAlarms`; `allowBackup=false` | Ключ модели нельзя класть в хранилище из `STORES`, иначе он уедет в слепок |
| Синхронизация | Локальная часть готова, сети нет | `data/sync/*`: журнал правок, порции, шифр, `CloudAccount` | Строки, созданные агентом, получат `uid` и попадут в журнал сами |
| Корзина на сутки | Есть | `Trash` (`Kind.DEED, DEED_ROW, NOTE, YET_ROW, MONEY`), `UndoBar` | Основа для «Вернуть» после действия агента (§12) |
| Разбор дат и времени | Есть | `parseTypedDate(raw, today, lean)`, `parseTypedTime`, `parseTypedRange`, `parseTypedRemind` (`ui/components/Typed*.kt`) | Проверка аргументов инструментов и основа офлайн-режима |
| JSON | Свой | `data/sync/Json` (`internal object`: `write`, `read`) в `shared`; в `app` доступен ещё системный `org.json` | Хватит на запросы к модели без библиотек |

---

## 5. Разбор по заданным областям

- **`data/library`** (`AskyaLibrary`, app) — папка `Askya` в корне памяти с
  полками для копий файлов. К пользовательским данным агента отношения не
  имеет, агенту не нужна.
- **`data/preferences`** — настройки, а не данные. Агенту полезны
  `AppSettings.autoFillDay` (соберётся ли день сам), `weekStartsMonday` и
  `watchedDeeds`. Ключ модели и настройки агента нужно держать в **новом
  отдельном** хранилище, которого нет в `Snapshots.STORES` (§12).
- **`app/`** — `AskyaApplication` создаёт `AndroidContainer` и при старте
  запускает уборку и наблюдателей: шторка, виджет, экран блокировки, корзина,
  Слепок, обновления. Там же видна единственная сеть приложения:
  `WeatherService` и `Updates` на `HttpURLConnection`.
- **UI** — общий, в `shared`. Экран агента естественно ложится туда же и
  появится сразу и на Windows.
- **Bridge** — хранилище и чистое правило `deedTarget` в общем коде, UI и
  переход только на телефоне. Агенту на чтение.
- **Threads / nodes / edges** — отсутствуют (§2).

---

## 6. Главный контейнер зависимостей

**`AppContainer`** (`shared/commonMain/.../app/AppContainer.kt`) — ручной DI,
абстрактный класс, все репозитории создаются `by lazy`:

```
AppContainer (abstract)                 ← общий код
 ├─ database (protected abstract)
 ├─ settings, readerPreferences, weatherPreferences, account, alarms, reminderSounds (abstract)
 ├─ gate: AccountGate
 ├─ noteRepository, scheduleRepository, reminderRepository, deedTaskRepository,
 │  routineRepository, yetRepository, ledgerRepository, bridgeRepository, dayRepository
 └─ trash: Trash
AndroidContainer(context) : AppContainer   ← app/…/app/AndroidContainer.kt
 └─ + library, echo*, video*, weather, snapshots, updates, voiceRecorder
DesktopContainer(home) : AppContainer      ← desktop
```

- Создаётся в `AskyaApplication.onCreate`: `container = AndroidContainer(this)`.
- В Compose попадает через `LocalAppContainer`, доступ — `appContainer()` и
  `androidContainer()`.
- `database` защищён: инструменты должны ходить через репозитории, а не в
  DAO. Это правильно, и менять это не нужно.

---

## 7. Куда безопаснее всего добавить агента

**Новый пакет `app.askya.agent` в `shared/src/commonMain`**, одно ленивое поле
в `AppContainer` и один маршрут в `AskyaNavHost`.

Почему именно так:

- Все репозитории, которыми пользуются инструменты, лежат в `shared` и
  доступны из `AppContainer`. Будильник — интерфейс `ReminderClock`, поэтому
  `create_reminder` одинаково работает на телефоне и на Windows.
- Схема базы не меняется: v0.1 работает без миграций. История диалога в v0.1
  живёт в памяти процесса (§12).
- Работающие ViewModel не трогаются. Агент получает свой `AgentViewModel` с
  такой же `factory(container)`.
- Существующие разделы и маршруты не меняются. Новый маршрут `Routes.AGENT`
  (**новое**) и строка в `AppDrawer` рядом с быстрой заметкой.

Чего делать не надо:

- Писать в DAO в обход репозиториев.
- Вызывать методы чужих ViewModel. Их `viewModelScope` принадлежит экрану.
- Класть агента в `app`: Windows-версия его лишится без всякой причины.
- Класть ключ в `SettingsPreferences`. README объясняет, почему ключ оттуда
  уже однажды пришлось вычищать.

---

## 8. Минимальный AI Agent v0.1

**READ:** `get_today`, `get_tasks`, `get_schedule`, `search_notes`.
**WRITE:** `create_task`, `create_reminder`, `create_note`.

Жёсткие границы v0.1:

- никакого удаления, изменения, отметки «сделано» или переноса существующих
  строк;
- никакого Ledger: ни чтения, ни записи;
- ни одной записи без нажатия человека. Модель только **предлагает**, пишет
  приложение по кнопке;
- никаких фоновых запусков, агент работает только на открытом экране;
- не больше 3 предложенных записей за один ответ и не больше 8 вызовов
  инструментов за один ход.

---

## 9. Инструменты

### Общие типы (**новое**, `shared/.../agent/tools/AgentTool.kt`)

```kotlin
package app.askya.agent.tools

enum class ToolKind { READ, WRITE }

/** Описание инструмента для модели: имя, смысл и JSON Schema аргументов. */
interface AgentTool {
    val name: String
    val description: String
    val kind: ToolKind
    /** JSON Schema в виде Map — сериализуется `app.askya.data.sync.Json.write`. */
    val inputSchema: Map<String, Any?>
}

/** Чтение: выполняется сразу, ничего не пишет. */
interface ReadTool : AgentTool {
    suspend fun read(input: Map<String, Any?>): ToolResult
}

/**
 * Запись в два шага. [propose] проверяет аргументы и ничего не пишет;
 * [apply] вызывается только по нажатию человека.
 */
interface WriteTool<P : Proposal> : AgentTool {
    suspend fun propose(input: Map<String, Any?>): ProposalResult<P>
    suspend fun apply(proposal: P): ToolResult
}

sealed interface ToolResult {
    data class Ok(val data: Map<String, Any?>) : ToolResult
    data class Failed(val reason: String) : ToolResult          // → tool_result с is_error
}

sealed interface ProposalResult<out P : Proposal> {
    data class Ready<P : Proposal>(val proposal: P) : ProposalResult<P>
    data class Rejected(val reason: String) : ProposalResult<Nothing>
}

/** То, что человек видит карточкой с кнопками «Сделать» и «Не надо». */
sealed interface Proposal { val summary: String }
```

Аргументы от модели приходят `Map<String, Any?>` (из `Json.read`). Каждый
инструмент сам разбирает их и возвращает `Failed` или `Rejected` словами, без
исключений.

### READ

#### `get_today`

```kotlin
class GetTodayTool(
    private val schedule: ScheduleRepository,
    private val deedTasks: DeedTaskRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsPreferences,
) : ReadTool
```

- **Вход:** ничего.
- **Результат:** `date`; `now`; `deeds[]` — `{id, start, end?, title, note, done,
  link?}`; `tasks{deedId → [{text, done, heading}]}`; `reminders[]` на сегодня;
  `current` — id текущего дела; `autoFillDay`.
- **Вызывает:** `schedule.itemsOnce(today)`, `deedTasks.tasksOnce(today)`,
  `reminders.reminders().first()` с отбором `date == today`,
  `DayPlan(today, items).currentBlock(now.toLocalTime())`,
  `settings.state.value.autoFillDay`.
- **Важно:** `DayRepository.ensureComposed` **не вызывается**, потому что это
  запись. Если день ещё не открывали, дел из распорядка в нём может не быть.
  Поле `autoFillDay` позволяет модели сказать об этом честно.

#### `get_tasks`

```kotlin
class GetTasksTool(
    private val schedule: ScheduleRepository,
    private val deedTasks: DeedTaskRepository,
    private val yet: YetRepository,
) : ReadTool
```

- **Вход:** `date: String?` (ISO, по умолчанию сегодня), `includeDone:
  Boolean = false`, `includeLists: Boolean = false`.
- **Результат:** дела дня вместе со строками их списков; при `includeLists`
  добавляются списки Yet `{title, items[{text, done}]}`.
- **Вызывает:** `schedule.itemsOnce(date)`, `deedTasks.tasksOnce(date)`,
  `yet.lists().first()`, `yet.itemsByList().first()`.

#### `get_schedule`

```kotlin
class GetScheduleTool(
    private val schedule: ScheduleRepository,
    private val reminders: ReminderRepository,
) : ReadTool
```

- **Вход:** `from: String`, `to: String` (ISO, не больше 14 дней, иначе
  `Failed`).
- **Результат:** `days[] = {date, deeds[{id, start, end?, title, done,
  remind?}]}`.
- **Вызывает:** `schedule.itemsOnce(d)` по каждой дате,
  `reminders.forItems(ids)`, `Reminder.remindAt`. `busyDates` не нужен:
  диапазон и так короткий.

#### `search_notes`

```kotlin
class SearchNotesTool(private val notes: NoteRepository) : ReadTool
```

- **Вход:** `query: String` (слова, `#тег` — тег), `limit: Int = 10`
  (не больше 20).
- **Результат:** `[{id, title, snippet (~300 символов вокруг совпадения),
  tags, updatedAt, kind: note|file|voice}]`. Картинки не отдаются.
- **Вызывает:** `notes.notes().first()` и отбор в Kotlin по правилу Scroll:
  все слова и все теги, `lowercase`.
- **Почему не `notes(query, tag)` → `observeFiltered`:** `LIKE` не
  понимает регистр кириллицы и ищет фразу целиком, а не слова (§0.5).
  Чтобы правило поиска не разъехалось с экраном Scroll, `askOf` и
  `Note.matches` лучше вынести из `ScrollScreen.kt` в общий
  `domain/search/NoteQuery.kt` (**новое**; чистый перенос кода, поведение
  экрана не меняется). Для v0.1 можно временно повторить правило в
  инструменте.

### WRITE

Во всех трёх случаях `propose` проверяет аргументы и собирает `Proposal`
**без записи**. `apply` вызывается из `AgentViewModel.confirm()`.

#### `create_task` — новое дело дня

```kotlin
data class DeedProposal(
    val title: String, val date: LocalDate, val start: LocalTime, val end: LocalTime?,
    val note: String, val remind: RemindAt?,
) : Proposal

class CreateTaskTool(
    private val schedule: ScheduleRepository,
    private val reminders: ReminderRepository,
    private val alarms: ReminderClock,
) : WriteTool<DeedProposal>
```

- **Вход:** `title` (обязательно, до 120 символов), `date` (ISO или «завтра»
  / «пт» через `parseTypedDate(lean = AHEAD)`), `time` (обязательно:
  `parseTypedRange` или `parseTypedTime`), `note?`, `remind?`
  (`parseTypedRemind`: «за 15 минут», «9:00»).
- **Отказы в `propose`:** пустое название, нет времени, дата в прошлом,
  нераспознанное напоминание.
- **Результат `apply`:** `{deedId, date, start, remindId?}`.
- **Вызывает** (ровно как `AskyaDayViewModel.save` при `existing == null`):
  1. `schedule.add(ScheduleItem(date, startTime, endTime, title, note, icon = null, link = null))`.
     `icon = null` — штатно: знак угадывается по названию при отрисовке
     (`BlockCard.kt:307`: `chosen ?: BlockIcons.of(title)`).
  2. Если `remind != null`: `reminderOf(title, eventDate = date, eventStart =
     start, eventEnd = end, remind = remind, itemId = deedId)` →
     `reminders.add(...)` → `alarms.schedule(reminder.copy(id = id))`.
- Вызывать `dropReminders` не нужно: у только что созданного дела
  напоминаний нет.
- **Лучше, чем копировать:** вынести шаги 1–2 и `applyRemind` в общую функцию
  рядом с `dropReminders` и `moveReminder` в `reminders/ReminderClock.kt` или
  в `ScheduleRepository`. Тогда её зовут и `AskyaDayViewModel`, и агент.
  Это вторая правка существующего кода, она предлагается шагом 6 плана.

#### `create_reminder` — отдельное напоминание

```kotlin
data class ReminderProposal(
    val title: String, val date: LocalDate, val start: LocalTime, val remind: RemindAt,
) : Proposal

class CreateReminderTool(
    private val reminders: ReminderRepository,
    private val alarms: ReminderClock,
) : WriteTool<ReminderProposal>
```

- **Вход:** `title`, `date`, `time` (время события), `remind?` (по умолчанию
  `RemindAt.Exact(time)`).
- **Результат:** `{reminderId, ringsAt, exact: Boolean}`.
- **Вызывает** (как `RemindersViewModel.save` при `existing == null`):
  `reminderOf(title.trim(), eventDate, eventStart, eventEnd = null, remind)` →
  `reminders.add(r)` → `alarms.schedule(r.copy(id = id))`. Поле `exact`
  берётся из `alarms.exact`.
- **Отказ:** момент звонка (`remindMoment(...)`) уже прошёл. `ReminderClock`
  прошедшее не ставит, и запись без звонка обманула бы человека.
- **Разрешения:** уведомления (`ReminderAlarms.needsPermission()`) экран агента
  спрашивает так же, как экран напоминаний, в момент первого подтверждения.

#### `create_note` — быстрая заметка

```kotlin
data class NoteProposal(val title: String, val body: String) : Proposal

class CreateNoteTool(private val notes: NoteRepository) : WriteTool<NoteProposal>
```

- **Вход:** `title?` (если пусто — первая строка текста, как в
  `QuickNoteCard`), `body` (обязательно, до 20 000 символов).
- **Результат:** `{noteId}`; UI предлагает открыть заметку через
  `Routes.noteEdit(id)`.
- **Вызывает:** `notes.quickNote(title, body)`. Заметка ложится в Библиотеку
  без книги, как быстрая заметка из меню. Теги в v0.1 не ставятся:
  `quickNote` их не принимает, а `get` + `save(copy(tags))` — это уже
  изменение.

---

## 10. Архитектура

```
UI  — AgentScreen (shared/ui/agent, новое)
      лента сообщений · поле ввода + Dictation · карточки предложений [Сделать] [Не надо]
 ↓ события                                  ↑ StateFlow<AgentState>
AgentViewModel (новое; factory(container) как у всех ViewModel)
      держит историю хода, pending-предложения, confirm()/decline(), отмена по уходу с экрана
 ↓ suspend send(text)
AskyaAgent (новое, без Android)
      цикл: запрос к модели → tool_use → READ сразу / WRITE в предложение → tool_result → …
      лимит 8 вызовов на ход; системный промпт + AgentContext
 ↓                                  ↓
LlmClient (интерфейс, новое)      ToolRegistry (новое)
      AnthropicClient / RulesClient      name → AgentTool; список схем для модели; kind
      FakeLlmClient (тесты)            ↓
                                Askya Tools (новое): GetToday, GetTasks, GetSchedule, SearchNotes,
                                                     CreateTask, CreateReminder, CreateNote
                                 ↓
существующий слой: ScheduleRepository · DeedTaskRepository · ReminderRepository + ReminderClock ·
                   NoteRepository · YetRepository · SettingsPreferences   (из AppContainer)
                                 ↓
                   Room (askya.db, v48) · AlarmManager / DesktopAlarms · DataStore
```

Чем ход по кнопке «Сделать» отличается от хода модели: модель вызывает
`create_*` и получает в ответ `tool_result` «предложено, ждёт человека», а не
«создано». Записывает `AgentViewModel.confirm(proposalId)` через
`WriteTool.apply`. Результат дописывается в историю, и модель при следующей
реплике знает, что сделано, а что отклонено.

---

## 11. Абстракция LLM-клиента

```kotlin
package app.askya.agent.llm

interface LlmClient {
    val id: String                       // "anthropic:claude-opus-5", "rules", "fake"
    val online: Boolean                  // уходит ли что-то с устройства
    suspend fun next(request: LlmRequest): LlmReply
}

data class LlmRequest(
    val system: String,
    val messages: List<LlmMessage>,      // только добавляется, прошлое не переписывается
    val tools: List<ToolSpec>,           // name, description, inputSchema
    val maxTokens: Int = 4_000,
)

sealed interface LlmPart {
    data class Text(val text: String) : LlmPart
    data class ToolCall(val id: String, val name: String, val input: Map<String, Any?>) : LlmPart
    data class ToolResult(val callId: String, val content: String, val isError: Boolean) : LlmPart
    /** Блоки, которые клиент обязан вернуть модели как есть (например, thinking). */
    data class Opaque(val raw: Map<String, Any?>) : LlmPart
}
data class LlmMessage(val role: Role, val parts: List<LlmPart>)
enum class Role { USER, ASSISTANT }

sealed interface LlmReply {
    data class Turn(val parts: List<LlmPart>, val stop: Stop) : LlmReply
    data class Failure(val kind: FailureKind, val message: String) : LlmReply
}
enum class Stop { DONE, TOOL_USE, MAX_TOKENS, REFUSAL }
enum class FailureKind { OFFLINE, AUTH, RATE_LIMIT, SERVER, BAD_REQUEST, TIMEOUT }
```

Агент видит только эти типы. Новая модель — новый класс `LlmClient`, сам
`AskyaAgent` при этом не меняется.

**Реализации:**

| Класс | Сеть | Назначение |
|---|---|---|
| `FakeLlmClient` | нет | Тесты `AskyaAgent` и инструментов на `desktopTest` без сети |
| `RulesClient` | нет | Офлайн-режим: разбирает простые фразы («завтра в 9 созвон», «напомни в 18:00 …», «найди дача») существующими `parseTypedDate` / `parseTypedRange` / `parseTypedRemind` и вызывает те же инструменты |
| `AnthropicClient` | да | Claude через Messages API с инструментами |
| (позже) модель на устройстве | нет | Отдельный класс, если найдётся подходящая. Это новая зависимость, решать отдельно |

**`AnthropicClient`, два пути, выбирать вам:**

1. **Официальный Java SDK** `com.anthropic:anthropic-java`, который Kotlin
   использует напрямую. Этот вариант рекомендует Anthropic: готовые типы,
   повторы, разбор ошибок. Но это новая зависимость, а README держит
   принцип «одна библиотека — libVLC». Решение за вами, в этом этапе ничего не
   ставится.
2. **Без библиотек:** `HttpURLConnection` (как `WeatherService` и `Updates`)
   плюс `app.askya.data.sync.Json`. `POST https://api.anthropic.com/v1/messages`,
   заголовки `x-api-key`, `anthropic-version: 2023-06-01`,
   `content-type: application/json`. Тело:
   `{model, max_tokens, system, messages, tools:[{name, description,
   input_schema, strict: true}]}`. Разбор ответа: `stop_reason` (`end_turn`,
   `tool_use`, `max_tokens`, `refusal`); блоки `content[]` вида `text` и
   `tool_use{id, name, input}`. Результат отправляется следующим
   user-сообщением как **все** `tool_result{tool_use_id, content, is_error}`
   **одним** сообщением. Входы инструментов разбирать только как JSON, а не
   сравнением строк.

Общее для обоих путей:

- **Модель:** по умолчанию `claude-opus-5`. Более дешёвые `claude-sonnet-5` и
  `claude-haiku-4-5` — ваш выбор после замера на реальных фразах: так
  советует справочник Claude API, и снижать модель ради цены без замера не
  стоит.
- **`strict: true`** у каждого инструмента, со схемами
  `additionalProperties: false` и `required`. Тогда аргументы гарантированно
  проходят схему, но проверка в `propose` всё равно нужна: схема не знает,
  что дата в прошлом.
- **Возвращать модели её ответ целиком**, включая служебные блоки
  (`LlmPart.Opaque`), и не переписывать прошлые реплики.
- **Отказ модели** (`stop_reason: refusal`) показывается человеку словами, а
  не как ошибка сети.
- Пока ответ короткий, потоковая передача не нужна. Если появятся длинные
  ответы — переходить на поток.

---

## 12. Контекст, история, память, подтверждение, ошибки, офлайн

### Контекст агента

Контекст собирается заново на каждый ход и стоит **после** неизменной части
промпта, чтобы неизменная часть кэшировалась.

- Неизменная часть: роль, правила («только предлагай запись», «не выдумывай
  id», «если нет времени — спроси»), словарь Askya (дело, список дела, Yet,
  Scroll, напоминание), описания инструментов.
- Меняющаяся часть (`AgentContext`, **новое**): сегодняшняя дата и день недели,
  часовой пояс, `weekStartsMonday`, `autoFillDay`. **Данные человека в контекст
  заранее не кладутся**: модель получает их только через READ-инструменты и
  только когда спросила. Так с устройства уходит минимум.

### История диалога

- v0.1: в памяти `AgentViewModel`, пока жив экран. Кнопка «Новый разговор»
  очищает историю. Сохранения нет, миграции базы нет.
- Размер: последние ~20 реплик. Длинные `tool_result` (поиск) обрезаются в
  истории до сводки.
- Позже, если понадобится: таблица `agent_messages` (**новое**, миграция
  48 → 49). **Не** добавлять её в `SyncSchema.TABLES`: разговор с моделью не
  должен уезжать на другие устройства без отдельного решения.

### Память

В v0.1 долговременной памяти нет, и это сознательно. Раньше Askya уже хранила
рассказ человека о себе (`about_me`, таблицы интервью), и потом их пришлось
стирать отдельной миграцией 45 → 46 и уборкой при запуске. «Память» агента —
это сами данные Askya, которые он читает инструментами. Если память
понадобится, она делается как видимая и редактируемая заметка Scroll, а не
как скрытое хранилище.

### Подтверждение опасных действий

- **Любая** WRITE-операция — это карточка предложения: что будет создано,
  дата и время словами (`formatTypedDate`, `formatRemind`) и кнопки
  «Сделать» / «Не надо».
- Автоподтверждения нет ни в каком виде. Предложение живёт до конца хода; если
  человек ушёл с экрана, оно отклоняется.
- После «Сделать» — полоска «Вернуть» (`UndoBar`) на несколько секунд.
  Строго это удаление своей же только что созданной записи
  (`ScheduleRepository.remove` + `dropReminders`, `NoteRepository.remove`,
  `ReminderRepository.delete` + `alarms.cancel`) через `Trash.remembered`.
  Это отмена действия человека, а не инструмент модели, но раз удаление в
  v0.1 запрещено, включать её или нет — решаете вы (вопрос в плане, шаг 9).
- Повтор: если такое же дело на эту дату уже есть, `propose` предупреждает об
  этом в карточке. Признак тот же, что в `sameDeed` (`domain/plan/SameEvent.kt`):
  совпало начало и название без учёта регистра. Сам `sameDeed` сравнивает
  `RoutineItem` с `ScheduleItem`, поэтому для двух дел сравнение пишется в
  инструменте теми же двумя условиями.

### Обработка ошибок

| Где | Что делать |
|---|---|
| Сеть, таймаут | `FailureKind.OFFLINE / TIMEOUT` → сообщение словами и предложение офлайн-режима (`RulesClient`) |
| 401 / 403 | «Ключ не подходит», ведёт в настройки агента |
| 429 / 5xx | Один повтор с паузой, потом сообщение |
| Неверные аргументы | `ToolResult.Failed(reason)` → `tool_result` с `is_error: true`; модель переспрашивает человека |
| Больше 8 вызовов за ход | Ход прерывается: «не получилось, скажите иначе» |
| Исключение в репозитории | Ловится в инструменте, модели уходит `Failed`, в лог — подробности. Экран не падает |
| `apply` не удался | Карточка остаётся с текстом ошибки, повторить можно вручную |

### Офлайн

- Без сети и без ключа агент не исчезает: `RulesClient` понимает узкий набор
  фраз и выдаёт те же карточки подтверждения.
- READ-инструменты работают всегда: данные локальные.
- Выбор клиента виден на экране: «на устройстве» или «через Claude». Человек
  всегда знает, уходит ли фраза с телефона.

### Приватность и ключ (к решению из §0.1)

- Облачный клиент включается **только явно**, в настройках агента, с
  объяснением, что именно уходит в сеть. По умолчанию выключен. На чужом
  телефоне это решает его владелец.
- Ключ хранится в **новом** DataStore `agent` (объявляется рядом с
  `settingsStore` в `PreferenceStores.android.kt` и в desktop-аналоге) и **не**
  добавляется в `Snapshots.STORES`. Так он не попадёт в Слепок и в
  синхронизацию.
- README («Про сеть и приватность», «Без модели») придётся дополнить.
  Написанный там принцип сейчас неправда для включённого агента.

#### Принятое решение (26 сентября 2026)

- **Одно разрешение.** `AgentPolicy.allowCloud` покрывает всё, что агент
  получает инструментами: дела, списки, напоминания и тексты заметок
  (`search_notes`). Отдельных разрешений по видам данных, категорий
  инструментов «только на устройстве» и отдельной модели приватности нет —
  это решено сознательно. По умолчанию `false`.
- **Согласие хранится только на устройстве**: в DataStore `agent`, не в
  синхронизации и не в Слепке. Экран согласия прямо перечисляет, что может
  уйти в сеть: дела, списки, напоминания и тексты заметок.
- **Проверка — на границе отправки.** Перед **каждой** отправкой запроса
  модели отправляющий (будущий `AskyaAgent`) спрашивает
  `policy.allowsClient(client.online)`. «Нельзя» — отдельное состояние
  разговора, а не исключение: запрос не собирается, данные не уходят.
  Инструменты, `AgentContext` и `ToolTurn` о клиенте и сети не знают — это
  проверяет `PrivacyBoundaryTest`.
- **Разговор принадлежит одному клиенту.** Сменился `LlmClient` — новый
  разговор с пустой историей. История, собранная локальным клиентом, никогда
  не уходит облачному сама.
- README переписывается отдельным шагом, когда появится настоящий клиент.

---

## 13. Что не делалось

Зависимости не ставились, `build.gradle.kts` и код не менялись. Этот файл —
единственное изменение.

---

## 14. Сводная таблица

| Tool | Read/Write | Existing API | Нового кода | Риск |
|------|------------|--------------|-------------|------|
| `get_today` | Read | `ScheduleRepository.itemsOnce`, `DeedTaskRepository.tasksOnce`, `ReminderRepository.reminders`, `DayPlan.currentBlock`, `SettingsPreferences.state` | ~80 строк | Низкий. Несобранный день выглядит пустым — отдаётся флаг `autoFillDay` |
| `get_tasks` | Read | `itemsOnce`, `tasksOnce`, `YetRepository.lists` / `itemsByList` | ~70 строк | Низкий |
| `get_schedule` | Read | `ScheduleRepository.itemsOnce` (по дням), `ReminderRepository.forItems(ids)`, `Reminder.remindAt` | ~60 строк | Низкий. Диапазон ограничен 14 днями |
| `search_notes` | Read | `NoteRepository.notes()`; правило из `ScrollScreen` (`askOf`, `Note.matches`) | ~90 строк (+ перенос правила в `domain/search`) | Средний: `observeFiltered` не годится для кириллицы; в модель уходит текст заметок |
| `create_task` | Write | `ScheduleRepository.add`, `reminderOf`, `ReminderRepository.add`, `ReminderClock.schedule` (как `AskyaDayViewModel.save`) | ~110 строк | Средний: дубль логики ViewModel, пока её не вынесли; напоминание без будильника, если пропустить `schedule` |
| `create_reminder` | Write | `reminderOf`, `remindMoment`, `ReminderRepository.add`, `ReminderClock.schedule` / `exact` (как `RemindersViewModel.save`) | ~80 строк | Средний: разрешение на уведомления и точный будильник |
| `create_note` | Write | `NoteRepository.quickNote` | ~40 строк | Низкий |

Каркас, который нужен всем: `AgentTool` / `ToolRegistry` (~80 строк),
`AskyaAgent` (~200), `LlmClient` с типами (~80), `FakeLlmClient` (~40),
`RulesClient` (~150), `AnthropicClient` (~250 без SDK), `AgentViewModel`
(~150), `AgentScreen` (~350), хранилище `agent` и настройки (~80).

---

## Recommended implementation order

Каждый шаг заканчивается сборкой и проверкой, прежде чем идти дальше.

1. **Решение о приватности** (§0.1, §12): облако или только устройство, кто
   включает, что написать в README. Без этого шаги 7 и дальше не начинаются.
2. **Каркас без сети и UI.** Пакет `shared/commonMain/.../agent/`: `AgentTool`,
   `ToolResult`, `Proposal`, `ToolRegistry`. Тестов нет — пока нечего.
3. **READ-инструменты** `get_today`, `get_tasks`, `get_schedule` и тесты на
   `desktopTest` против настоящей базы, как в `DesktopDataTest`.
4. **Правило поиска.** Перенести `askOf` и `Note.matches` из `ScrollScreen.kt`
   в `domain/search/NoteQuery.kt`, экран вызывает его оттуда. Затем
   `search_notes` и тест на «Дача» / «дача».
5. **`create_note`** (самая безопасная запись) через `propose` / `apply` плюс
   тест.
6. **Вынести создание дела с напоминанием** из `AskyaDayViewModel`
   (`save` при `existing == null` + `applyRemind`) в общую функцию рядом с
   `dropReminders` / `moveReminder`. ViewModel начинает звать её, поведение не
   меняется. Затем `create_task` и `create_reminder` на ней и на
   `reminderOf`, тесты с поддельным `ReminderClock`.
7. **`LlmClient`, `FakeLlmClient` и `AskyaAgent`**: цикл, лимиты, превращение
   WRITE-вызовов в предложения. Тесты сценариев на `FakeLlmClient` без сети.
8. **`RulesClient`** — офлайн-разбор на существующих `parseTyped*`. Агент
   становится полезным без облака.
9. **`AgentViewModel` и `AgentScreen`**, маршрут `Routes.AGENT`, строка в
   `AppDrawer`, `AppContainer.agent by lazy`. Микрофон — `rememberDictation`.
   Решить про «Вернуть».
10. **Проверка на телефоне** через adb: собрать, поставить, пройти сценарии
    «что у меня сегодня», «завтра в 9 созвон, напомни за 15 минут», «запиши
    заметку…», «найди дача».
11. **`AnthropicClient`** (если в шаге 1 выбрано облако): хранилище `agent`
    вне `Snapshots.STORES`, экран ключа, выключатель по умолчанию «выкл», запись
    в README. Выбрать SDK или без библиотек (§11) — если SDK, это первая
    правка `build.gradle.kts`, и только с вашего согласия.
12. **Проверка облачного клиента** на реальных фразах, замер расхода, затем
    выпуск.

### Файлы, которые меняются на первом этапе (шаги 2–9)

Новые файлы (всё в `shared/src/commonMain/kotlin/app/askya/`):

- `agent/tools/AgentTool.kt`, `agent/tools/ToolRegistry.kt`
- `agent/tools/GetTodayTool.kt`, `GetTasksTool.kt`, `GetScheduleTool.kt`,
  `SearchNotesTool.kt`, `CreateTaskTool.kt`, `CreateReminderTool.kt`,
  `CreateNoteTool.kt`
- `agent/AskyaAgent.kt`, `agent/AgentContext.kt`
- `agent/llm/LlmClient.kt`, `agent/llm/RulesClient.kt`
- `domain/search/NoteQuery.kt`
- `ui/agent/AgentViewModel.kt`, `ui/agent/AgentScreen.kt`
- тесты: `shared/src/desktopTest/kotlin/app/askya/agent/…`, `FakeLlmClient`

Существующие файлы:

- `shared/src/commonMain/kotlin/app/askya/app/AppContainer.kt` — поле
  `agent by lazy`
- `shared/src/commonMain/kotlin/app/askya/ui/scroll/ScrollScreen.kt` — правило
  поиска уезжает в `domain/search`
- `shared/src/commonMain/kotlin/app/askya/reminders/ReminderClock.kt` (или
  `data/repository/ScheduleRepository.kt`) — общая функция «дело с
  напоминанием»
- `shared/src/commonMain/kotlin/app/askya/ui/askyaday/AskyaDayViewModel.kt` —
  зовёт эту функцию вместо своей копии
- `shared/src/commonMain/kotlin/app/askya/ui/navigation/Destination.kt` —
  `Routes.AGENT`
- `shared/src/commonMain/kotlin/app/askya/ui/navigation/AskyaNavHost.kt` —
  `composable(Routes.AGENT)`
- `shared/src/commonMain/kotlin/app/askya/ui/navigation/AppDrawer.kt` — вход в
  агента

Во втором этапе (шаг 11) добавятся `agent/llm/AnthropicClient.kt`, новое
хранилище в `shared/src/androidMain/.../data/preferences/PreferenceStores.android.kt`
и его desktop-аналог, настройки агента и `README.md`. `Snapshots.kt` **не**
меняется: новое хранилище в `STORES` не добавляется.
