# News Digests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execution method awaits user selection; no implementation is authorized by this document alone.

**Goal:** Дать голосовому помощнику управление новостными подборками, источниками, расписаниями и автоматическими иллюстрациями, с просмотром в приложении и системной заставке.

**Architecture:** Один APK и общее SQLite-хранилище; независимый от звонков сборщик с долговечной очередью. UI и DreamService читают опубликованные данные; голосовые инструменты используют тот же сервис команд. Генерация через подписку сначала проверяется отдельно.

**Tech Stack:** Java 8 syntax / Android API 29+, java.time, SQLiteOpenHelper, JobScheduler, AlarmManager без WAKEUP, native Views, DreamService, существующий подписочный Responses SSE. Не добавлять WebView, desktop runtime или тяжёлую UI библиотеку.

**Spec:** [2026-10-18-news-digests-design.md](../specs/2026-10-18-news-digests-design.md)

## Global Constraints

- Целевая платформа: TCL Android 12 / ARM32. VoiceInteractionService и native WebRTC сохраняются; popup — только orb 87 dp.
- Первая capability-проверка должна вернуть завершённое декодируемое изображение. HTTP 200, принятие схемы инструмента или timeout не доказывают поддержку imagegen.
- Нет автоматического переключения на платный API. Нет произвольного сервера для токенов. Нет генерации через desktop Codex.
- Лимиты: 20 активных подборок, 30 источников на подборку, 30 кандидатов и 12 новостей на выпуск, 3 последних выпуска на подборку, 6 новых обложек на выпуск, 24 автоматические генерации в сутки, кеш 200 MiB.
- Актуальность: 48 часов; неизвестное время публикации остаётся null. Повторный сюжет использует готовое изображение.
- Расписания: ежедневно, дни недели, интервал минимум 1 час; зона сохраняется из GeneralPreferences. Просроченные слоты объединяются, экран и звук не включаются.
- Изменения используют expected_revision и operation ID. Удаление и logout запрещают позднюю запись; не отправлять неопределённый генеративный запрос повторно вслепую.
- Фоновые запросы не используют SessionWorkspace, микрофон, историю разговоров, TV actions или AppDataWork. Обновление не мешает завершить звонок.
- Нативный текст поверх изображения; «Иллюстрация ИИ» у генерации; источники и даты доступны. Все редакторы через RightSheet, настройки через SettingsRows.
- Local Git only; не трогать параллельные изменения, не обходить hooks, не публиковать контейнеры, не деплоить. Установка и TV-тесты требуют отдельного разрешённого окна.

## Review Focus

1. Смерть процесса между отправкой генерации и сохранением ответа: unconfirmed без повторного списания/генерации; Tasks 1, 3, 5.
2. Источник перенаправляет на приватный адрес или возвращает огромный XML/картинку: ограниченная безопасная загрузка без утечки авторизации; Tasks 1, 4.
3. Удаление, logout или изменение подборки во время генерации: поздний результат не публикуется; Tasks 2, 5.
4. Переход DST, ручная смена времени, перезагрузка и несколько совпавших расписаний: один актуальный запуск, без burst; Task 3.
5. Удаление кеша во время показа, старые новости и вызов голоса из заставки: нет битой картинки, ложной свежести или захвата фокуса popup; Tasks 5, 8.

## Структура и порядок

Пакет `digests/` владеет сетевой работой, политиками и очередью. Хранилище — `data/DigestRepository`, UI — `ui/`, голосовой адаптер — `DigestTools` в основном пакете, где доступны AccountStore и подписочные helpers. Чистые политики тестируются на host; SQLite — отдельным self-targeted data APK; UI и системная заставка — только на ТВ в разрешённом окне.

Это последовательные части одной функции. Tasks 1–5 дают проверяемый сборщик, Task 6 — голосовой доступ, Tasks 7–8 — два способа просмотра. Если Task 1 явно отклонён сервером, остановить imagegen-зависимую реализацию и согласовать способ подключения; не объявлять функцию готовой с заглушкой.

Перед исполнением прочитать актуальные AGENTS.md, spec и документы UI/setup; создать изолированное рабочее дерево через using-git-worktrees. Текущая рабочая копия содержит параллельные изменения. Не переносить их автоматически и не сбрасывать; согласовать базу выполнения с состоянием локального Git. Номера schema и сигнатуры существующих helpers перепроверить на выбранной базе.

### Task 1: Проверяемый транспорт imagegen через подписку

**Files:** Create `app/src/local/tvgpt/assistant/digests/ImageGenerationProtocol.java`, `digests/ImageGenerator.java`, `app/src/local/tvgpt/assistant/SubscriptionImageGenerator.java`, `tests/ImageGenerationProtocolTest.java`, `docs/imagegen-subscription-investigation.md`; Modify `scripts/test.sh`. Отдельный opt-in device diagnostic включить в `tests/android/DeviceCheck.java` и новый `tests/android/ImageGenerationChecks.java`; не запускать его без окна проверки.

**Interfaces:** `ImageGenerator.generate(String operationId, String prompt, File destination) -> ImageGenerator.Result`; Result содержит `state` (ready/failed/unconfirmed/unsupported), `file`, `reportedModel`, `reason`. `SubscriptionImageGenerator(Context)` реализует интерфейс. `ImageGenerationProtocol.completed(String sse) -> JSONObject` возвращает только завершённый response, восстанавливая output_item.done, если completed.output пуст; `request(String model, String prompt) -> JSONObject` строит отдельный tool-only запрос, без app tools. File destination — временный файл, не окончательная публикация.

- [ ] Добавить failing `ImageGenerationProtocolTest`: completed image_generation_call с валидным base64 принимается; оборванный SSE и HTTP 200 без готового output не ready; failed и явный unsupported различаются; пустой completed.output восстанавливается из done items. Зарегистрировать host compile/run блок в scripts/test.sh с json-test.jar.
- [ ] Запустить `bash scripts/test.sh`; ожидать FAIL именно нового теста/отсутствующих классов. Существующие сбои отдельно зафиксировать, не выдавать за ожидаемый red.
- [ ] Реализовать указанные интерфейсы. Авторизация только через AccountStore/LiveConnection.headers к каноническому endpoint; отдельный bounded streaming transport с пределом 32 MiB wire и 16 MiB decoded, сроком запроса 180 секунд и без логирования тела. Срок означает unconfirmed, не unsupported. Проверить bitmap bounds, максимум 16 мегапикселей, фактический PNG/JPEG/WebP и успешное декодирование; не доверять MIME. Не менять общий Http ради одной операции.
- [ ] Запустить `bash scripts/test.sh` и `bash scripts/build-tests.sh`; ожидать PASS/собранный test APK. По разрешённому окну выполнить ровно одну генерацию нейтрального пейзажа диагностикой; записать статус, наличие локального декодируемого файла, модель, ограничения, без токенов/base64. Если окно отсутствует, отметить live capability unconfirmed и не утверждать проверку на аккаунте.
- [ ] Сделать локальный commit только файлов задачи: `feat: add bounded subscription image generation adapter`. При unsupported или отсутствии возможности подтвердить доступ вынести gate пользователю до imagegen-зависимой реализации. Документация публичного API сама gate не закрывает.

### Task 2: Хранилище дайджестов и ревизии

**Files:** Create `app/src/local/tvgpt/assistant/data/DigestRepository.java`, `app/src/local/tvgpt/assistant/digests/DigestPolicy.java`, `tests/DigestPolicyTest.java`, `tests/isolated/DigestDataChecks.java`; Modify `data/AppDatabase.java`, `tests/isolated/DataCheck.java`, `scripts/build-data-tests.sh`, `scripts/test.sh`.

**Interfaces:** `DigestRepository(Context)`; `list(String kind, String parentId) -> JSONArray`, `read(String kind, String id) -> JSONObject`, `mutate(String operationId, String kind, JSONObject values, long expectedRevision) -> JSONObject`. kind: digest/source/schedule; action в values: create/update/delete/link/unlink. `enqueue(String digestId, String operationId, String scheduleId, long slot, long now) -> JSONObject`; `job(String id) -> JSONObject`; `claim(long now, long leaseUntil) -> JSONObject`; `finish(String jobId, String state, String reason) -> void`; `publish(String jobId, JSONArray articles, long now) -> boolean`; `attachCover(String jobId, String articleId, String path, String key) -> boolean`; `invalidateAuthorizedJobs() -> void`; `freshCards(long now) -> JSONArray`; `prune(long now) -> JSONArray` возвращает пути удаляемых невостребованных файлов. Mutation results содержат id/revision/status, conflict явно отличается от success. Missing read возвращает null. Остальные read/list значения — bounded JSON без секретов.

- [ ] Написать failing host `DigestPolicyTest`: 21-я активная подборка и 31-й источник отклоняются; 13-й материал не публикуется; 4-й выпуск удаляет старейший; null timestamp не превращается в now. В `DigestDataChecks` добавить создание v2 базы и миграцию с сохранением conversations/knowledge/reminders, повторы operation ID, conflict, tombstone против late publish, logout и конкурентный claim.
- [ ] Запустить `bash scripts/test.sh`, затем `bash scripts/build-data-tests.sh`: новые классы отсутствуют, сборка/тесты должны падать в ожидаемых местах. Device assertions пока только подготовлены.
- [ ] Реализовать миграцию v2→v3, если версия базы на execution base всё ещё 2. Добавить таблицы spec, foreign keys и уникальные ключи operation ID / schedule+slot; ревизии всех входов сохранять в job snapshot. Все поздние write проверяют snapshot и account generation в транзакции. Публичные методы валидируют kind/action, lengths (title 120, topic 1000, URL 2048, summary 800) и лимиты spec.
- [ ] Запустить host suite и сборку self-targeted APK. В разрешённом data-test окне проверить SQLite assertions без обновления основного APK; удалить отдельный тестовый пакет после проверки. До окна фиксировать «скомпилировано, на устройстве не выполнено».
- [ ] Commit: `feat: persist digest configuration and revision guarded jobs`.

### Task 3: Расписания и долговечная очередь

**Files:** Create `digests/DigestSchedulePolicy.java`, `digests/DigestScheduler.java`, `digests/DigestJobService.java`, `digests/DigestReceiver.java`, `digests/DigestWorker.java`, `tests/DigestSchedulePolicyTest.java`; Modify `app/AndroidManifest.xml`, `AccountStore.java`, `scripts/test.sh`, `tests/isolated/DigestDataChecks.java`.

**Interfaces:** `DigestSchedulePolicy.next(JSONObject schedule, long after) -> long` epoch millis; `slot(JSONObject schedule, long now) -> long` идентифицирует последний наступивший срок или -1; `DigestScheduler.reconcile(Context) -> void`; `DigestWorker.run(Context, String jobId, java.util.concurrent.atomic.AtomicBoolean stopped) -> void`. До Task 5 worker помечает работу pending capability/collector, не имитирует успешное обновление. `DigestRepository.dueSchedules(long now) -> JSONArray` и `recover(long now) -> void` дополняют Task 2. Recovery никогда не превращает in-flight image generation в разрешение слепого повтора.

- [ ] Написать failing `DigestSchedulePolicyTest`: Europe/Berlin 2026-03-29 02:30 → первый допустимый момент после DST gap; повтор часа 2026-10-25 → один слот; 30-минутный интервал отклонён; задержка на 3 дня даёт один актуальный slot. SQLite assertions: совпадающие расписания одной подборки не дают две активные работы, expired lease восстанавливается, dispatched generation остаётся unconfirmed.
- [ ] Запустить host suite; ожидать FAIL новых schedule tests.
- [ ] Реализовать политики daily/weekly/interval, next/slot на java.time с сохранённой zone. JobScheduler с NETWORK_TYPE_ANY и non-WAKEUP AlarmManager только для reconcile; receiver вызывает goAsync и быстро планирует job. Ресивер обрабатывает boot/time/timezone/MY_PACKAGE_REPLACED. JobService отменяет сеть через stopped, не использует AudioKeepAlive. AccountStore.clear вызывает invalidateAuthorizedJobs; offline/logout показываются как waiting/requires-login. Отдельное новое разрешение на пробуждение не добавлять.
- [ ] Запустить `bash scripts/test.sh`, `bash scripts/build.sh`, `bash scripts/build-data-tests.sh`; PASS и ARM32 APK. Проверку реальной задержки в standby оставить для Task 9.
- [ ] Commit: `feat: schedule persistent digest updates without waking the TV`.

### Task 4: Безопасный сбор источников и новостная сводка

**Files:** Create `digests/NewsSourceClient.java`, `digests/NewsPolicy.java`, `app/src/local/tvgpt/assistant/DigestResearch.java`, `tests/NewsPolicyTest.java`, `tests/DigestResearchTest.java`; Modify `scripts/test.sh`, добавить изолированные research test stubs рядом с существующими summary-stubs.

**Interfaces:** `NewsSourceClient.read(String httpsUrl) -> JSONArray` кандидатов title/url/published_at/text/image_url; `NewsPolicy.normalizeUrl(String url) -> String`; `NewsPolicy.select(JSONArray candidates, long now) -> JSONArray`; `DigestResearch.collect(JSONObject snapshot, long now) -> JSONArray` валидированных материалов (id/story_key/title/summary/published_at/discovered_at/sources/image_url). snapshot — данные job из Task 2. `DigestResearch.discover(String query) -> JSONArray` ограниченных source suggestions. Оба метода делают отдельные подписочные запросы, без SessionWorkspace.

- [ ] Добавить failing tests: одинаковый URL с tracking параметрами объединяется без удаления значимых параметров; два источника одного сюжета сохраняют обе ссылки; публикация старше 48 часов исключена; неизвестная дата остаётся null. Промпт-инъекция статьи не создаёт tools; модельный output без sources отклонён; оборванный SSE не публикуется; redirect на 127.0.0.1/::1/private и XML external entities блокируются.
- [ ] Запустить `bash scripts/test.sh`; ожидать FAIL новых tests.
- [ ] Реализовать RSS/Atom и ограниченный HTML adapter без JS/paywall bypass. HTTPS redirects максимум 3, body максимум 2 MiB на источник, 30 s на чтение, текст до 12000 символов; проверять DNS и каждый redirect, запретить private/loopback/link-local/multicast destinations, credentials и нестандартные порты. HTTP source client никогда не получает headers подписки. Результаты ограничивать до 30 кандидатов и 12 материалов. Research имеет только web_search, максимум 3 rounds; валидировать URL и provenance, не исполнять function calls. Сохранять исходные даты и citations, distinction published/discovered. Тему/контент обрамлять как данные.
- [ ] Запустить host suite и ARM32 сборку; проверить fixtures, что useful tracking-free URLs не испорчены. Live поиск в разрешённом окне — отдельное evidence, не условный PASS mock-теста.
- [ ] Commit: `feat: collect bounded sourced news for digest releases`.

### Task 5: Сборка выпуска и автоматические обложки

**Files:** Create `digests/DigestAssembler.java`, `digests/DigestImages.java`, `digests/DigestRunPolicy.java`, `tests/DigestRunPolicyTest.java`; Modify `DigestWorker.java`, `DigestRepository.java`, `scripts/test.sh`, `tests/isolated/DigestDataChecks.java`.

**Interfaces:** `DigestAssembler(Context, ImageGenerator)`; `run(String jobId, AtomicBoolean stopped) -> void`; `DigestImages.acquire(String path) -> Closeable` pin для показа; `trim(long maxBytes) -> void`; `DigestRunPolicy.coverKey(String storyKey, String style) -> String`. Репозиторий добавляет `reserveGeneration(String jobId, String key, long now) -> JSONObject` с state reserved/cached/limit/unconfirmed, `generationResult(String operationId, String state, String path, String reportedModel) -> void`; reservation атомарно учитывает суточную квоту и записывается до отправки запроса. Cache files — app-private, имена по SHA-256 ключа, запись tmp+rename.

- [ ] Написать failing tests: 7-я обложка выпуска и 25-я за сутки не отправляются; cached cover не расходует генерацию; одинаковый key не отправляется двумя workers; crash после reservation не вызывает повторную отправку; редактирование/удаление/logout во время ответа отклоняет attachCover. Pin защищает показ от eviction; лимит 200 MiB удаляет только неприкреплённые и не pinned файлы. Текст выпуска доступен до завершения генерации.
- [ ] Запустить host suite и data APK build, ожидать FAIL новых тестов/сборки.
- [ ] Подключить Task 1–4 в assembler: claim → collect → guarded publish → reserved cover generation → guarded attach → finish. Отдельный network/generation worker один; готовые тексты публиковать без ожидания обложек. Unknown generation outcome сохранить unconfirmed; fallback photo через безопасный source client с лимитом 16 MiB и 16 MP, иначе текст. Generation prompt — стилизованная иллюстрация сюжета без встроенных заголовков. После pruning удалять только пути, которые не referenced и не pinned; при logout остановить работу без удаления опубликованного архива.
- [ ] Запустить host suite, ARM32 и data APK build; только capability-supported путь может считаться готовым к проверке настоящей генерации. Fixture success не закрывает live gate.
- [ ] Commit: `feat: assemble digest releases with automatic cached illustrations`.

### Task 6: Голосовые операции и статус заданий

**Files:** Create `app/src/local/tvgpt/assistant/DigestTools.java`, `digests/DigestCommands.java`, `tests/DigestToolsTest.java`; Modify `AgentTools.java`, `Agent.java`, `AssistantSkills.java` (если этот компонент присутствует на execution base), `tests/agent-stubs/AgentFakes.java`, `scripts/test.sh`.

**Interfaces:** `DigestTools.schemas(JSONArray out) -> void`; `execute(Context c, String name, JSONObject args) -> String` JSON. Tools: `digest_list`, `digest_read`, `digest_mutate`, `digest_source_list`, `digest_source_search`, `digest_source_mutate`, `digest_schedule_list`, `digest_schedule_mutate`, `digest_refresh`, `digest_job_read`, `digest_cover_generate`, `digest_open`. `DigestCommands(Context).execute(String tool, JSONObject args) -> JSONObject` используется и UI. Mutations требуют operation_id, expected_revision и типизированные values; refresh возвращает job_id/state. Open требует digest_id и mode detail/preview, UI только по явному запросу.

- [ ] Написать failing `DigestToolsTest`: неизвестная операция/некорректный UUID/revision отклонены; повтор operation_id возвращает прежний результат; JSON result именно строка; queued не выдаётся за ready. AgentLoopTest fixture: два вызова источника и расписания, затем закрытие звонка — сохранённые задания остаются, рабочая история фону не передаётся. Tool schemas strict и bounded.
- [ ] Запустить `bash scripts/test.sh`; ожидать FAIL новых tests, не маскировать ошибки старых stubs.
- [ ] Реализовать dispatch в Agent и общий DigestCommands. Tools не поддерживают shell и произвольные scheduled actions. Ограничение явного намерения включить в инструкции; серверная валидация обязательна. Не расширять фоновые tools полномочиями AgentTools.all. Источники не становятся общими знаниями. Долгие задания возвращают короткое подтверждение постановки и id, без блокировки звонка и незапрошенной TTS.
- [ ] Запустить host suite и ARM32 build; в разрешённом окне проверить «создай подборку → добавь источник → обновляй ежедневно → обнови сейчас → заверши разговор», затем job status в следующем звонке.
- [ ] Commit: `feat: expose digest sources schedules and generation to the voice assistant`.

### Task 7: Раздел и редакторы D-pad

**Files:** Create `ui/DigestsScreen.java`, `ui/DigestSheet.java`, `ui/DigestCard.java`; Modify `AppNavigation.java`, `MainActivity.java`, `ui/AppShell.java`, `SettingsGlyph.java`, `tests/AppNavigationTest.java`, `docs/ui/interface-contract.md`.

**Interfaces:** `DigestsScreen(Context)`; `refresh() -> void`; `DigestSheet.open(Context, String kind, String id, Runnable changed) -> void`; `DigestCard(Context, JSONObject article)`; `bindImage(String path) -> void`. MainActivity обрабатывает Route.DIGESTS и explicit digest_id. UI вызывает только DigestCommands и repository reads, не сетевой transport.

- [ ] Добавить failing AppNavigationTest на DIGESTS с сохранением setup/default/старых alias маршрутов; запустить host suite, ожидать FAIL. Не писать UI-тесты, повторяющие разметку; D-pad проверяется наблюдением на ТВ.
- [ ] Реализовать шестой пункт после Напоминаний, исключить hardcoded array length 5 в AppShell. Сохранить 180/42 dp и distinct focus/selection. Карточки показывают заголовок, суть, источники, время публикации отдельно от сборки и отметку ИИ. RightSheet редактирует темы, режимы, источники, zone-preserving daily/weekly/interval schedule и участие в заставке. Общие SettingsRows и существующие date/time D-pad элементы; без вложенных picker dialogs. Показать точные queued/partial/unconfirmed/failed статусы и последнее/следующее обновление. Подписки UI снимаются при detach, bitmap декодируется off-main.
- [ ] Обновить interface-contract: новая утверждённая навигация и UI; пометить, что установка/TV-проверка ещё не выполнены.
- [ ] Запустить host suite и ARM32 build. В разрешённом окне пройти пультом каждый редактор, Back и возврат фокуса; проверить читаемость обложки с дивана и открытие citations.
- [ ] Commit: `feat: add TV digest gallery and remote friendly editors`.

### Task 8: Системная заставка и ручной просмотр

**Files:** Create `digests/DigestDreamService.java`, `digests/DigestSlides.java`, `app/src/local/tvgpt/assistant/DigestPreviewActivity.java`, `app/res/xml/digest_dream.xml`, `tests/DigestSlidesTest.java`; Modify `app/AndroidManifest.xml`, `ui/DigestsScreen.java`, `scripts/test.sh`, `docs/installation-and-development.md`.

**Interfaces:** `DigestSlides.select(JSONArray cards, long now) -> JSONArray` только свежие opted-in материалы; `DigestSlides.nextIndex(int current, int count) -> int`; Dream и preview используют общий slide controller с `start()/stop()/release()`, pin от DigestImages и максимум двумя декодированными картинками. Preview Activity non-exported; DreamService exported с BIND_DREAM_SERVICE и metadata. Кнопка выбора использует системную screensaver settings Activity с проверкой resolveActivity.

- [ ] Написать failing DigestSlidesTest: возраст >48 часов исключён, null published_at использует discovered_at, disabled digest исключён, пустой список безопасен, future timestamp не становится вечной свежей новостью. Policy ограничивает допустимый future skew 5 минут, большее значение трактует как неизвестную дату. Запустить host suite, ожидать FAIL новых cases.
- [ ] Реализовать общий native slideshow: 20 секунд на карточку, fade 700 ms, текст/ИИ-отметка/источник в safe area; анимация только при видимости, небольшое перемещение подписи. Пустой список — нейтральный фон с коротким состоянием. Dream не инициирует сеть, не захватывает микрофон/AudioFocus, не удерживает wake lock. Не включать интерактивный режим Dream; обычное действие пульта завершает заставку. stop/release отменяют callbacks и отпускают pin/bitmap. Preview выходит по Back.
- [ ] Добавить выбор системной заставки и предпросмотр. Если прошивка не показывает selector, сообщить неподтверждённую замену и оставить preview; не менять secure settings или Google/TCL пакеты автоматически. В runbook отделить компонент выбран/система действительно показала заставку.
- [ ] Запустить host suite и ARM32 build; actual auto-start, пульт, вызов помощника и teardown проверить только в Task 9 TV window. Build не доказывает поддержку TCL.
- [ ] Commit: `feat: display cached digest news as an Android TV screensaver`.

### Task 9: Сквозная проверка и статус реализации

**Files:** Modify `docs/app-sections-status.md`, `docs/installation-and-development.md`, `docs/native-device-results.md`, `docs/imagegen-subscription-investigation.md`; при выявленных дефектах исправлять только owning components и повторять релевантные проверки.

**Interfaces:** Итоговый отчёт перечисляет commit, host/build results, imagegen evidence, установленную версию и реально проверенные TV сценарии отдельно.

- [ ] Запустить `npm ci --ignore-scripts`, `bash scripts/format-java.sh --check`, `bash scripts/test.sh`, `bash scripts/build.sh`, `bash scripts/build-tests.sh`, `bash scripts/build-data-tests.sh`, `npm run quality`. Требование: PASS команд; warnings описать, канонические подписочные URL не заменять произвольным host. Анализировать новые findings, не отключать правила.
- [ ] Получить/использовать актуальное разрешённое окно установки и TV-проверки, не прерывая использование ТВ. При его отсутствии закончить локальную реализацию и честно оставить device evidence pending, не запускать adb install/instrumentation.
- [ ] В окне проверить один настоящий выпуск по голосу и расписанию с доступной imagegen, cached повтор без генерации, offline fallback, удаление/изменение во время работы и logout. Проверить реальный auto-start Dream, пульт, читаемость, вызов помощника и 3 цикла закрытия/открытия, отсутствие лишнего микрофона, standby/resume без burst, память/CPU в одинаковых условиях. Отдельный data APK удалить после SQLite checks.
- [ ] Обновить статусы и ограничения в документах; не маркировать imagegen или системную заставку проверенными при отсутствии соответствующего evidence. Commit: `docs: record digest and screensaver verification results`.

## Самопроверка плана

- Покрытие spec: хранилище/ревизии Task 2; расписания/фон Task 3; источники/безопасность Task 4; публикация/обложки/лимиты Task 5; голос Task 6; UI Task 7; заставка Task 8; фактические проверки и документация Task 9.
- Capability gate и отсутствие автоматического платного fallback — Task 1; завершённый результат обязателен.
- Review Focus привязан к tests Tasks 1–5 и 8; ручные проверки отделены от host fixtures и сборки.
- Все новые методы, используемые последующими задачами, объявлены в Interfaces; дополнительные repo методы вводятся в owning task. JSON не попадает bare object в function_call_output.output.
- Исполнитель использует один выбранный пользователем способ выполнения. Параллельное изменение общих Agent/UI/DB файлов несколькими исполнителями не входит в этот план.
