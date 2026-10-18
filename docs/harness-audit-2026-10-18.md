# Аудит всех путей TV GPT harness · 18 октября 2026

Проверена объединённая ветка `codex/news-digests`, включая актуальные изменения рабочей папки. База аудита — `0958316` с новостными изменениями `c6a4634` и голосовым изменением `854c87b`. Аудит не ограничен завершением звонка. Во время этой проверки APK не устанавливались и состояние телевизора не менялось.

Это аудит кода, контрактов и тестов. Указанные ниже ошибки подтверждены ветвлениями кода; влияние на реальные действия Android отдельно требует воспроизведения на устройстве. Полный host-набор прошёл. Отдельный host-пробник подтвердил потерю неделегированного контекста; он использует настоящие Agent/SessionStore и тестовый HTTP, не обращается к аккаунту или сети.

## Карта всех маршрутов

[Полный машинный инвентарь](harness-tool-inventory-2026-10-18.json): **38 зарегистрированных инструментов**: 16 основных, 3 знания, 2 архив, 4 напоминания/таймеры, 12 дайджесты, 1 hosted web. Дополнительно проверены нетуловые пути Live, native audio, wake, авторизации и фоновой работы.

| Область | Полный маршрут и покрытые входы | Результат аудита |
|---|---|---|
| Запуск | Кнопка / wake / launch / явная проверка → SessionService / AssistantInvocation → VoicePanel → VoiceSession → native WebRTC и LiveSignalling | Основные границы выделены; ошибки preflight, отмены подключения и idle recovery описаны ниже |
| Обычный разговор | Микрофон → WebRTC → Live → динамики и финальные реплики | Не требует Agent; его контекст не передаётся исполнителю следующей задачи |
| Текстовый ввод | VoicePanel / VoiceSession.userText → session.context.append → sideband | Нет подтверждаемого результата отправки; typed input не заменяет испытание речи |
| Делегирование | RTC и sideband → delegation.created → дедупликация item.id → очередь Agent / local media | Исходная реплика ошибочно принимается за управляемую команду; task/revision/cancel-учёта нет |
| Изменение / отмена задачи | Новая делегация → тот же последовательный executor → Agent.run | Новая просьба ожидает предыдущую; не изменяет выполняющуюся задачу |
| Приложения | list_apps, open_app → TvTools → Android → TvActionObserver | Чтение фактического пакета есть; очередь мутаций не отменяется при таймауте |
| Пульт | navigate, navigate_sequence → ADB / accessibility fallback → наблюдение | Неподтверждённая ADB-команда иногда повторяется другим способом |
| Громкость | volume, mute → Android AudioManager → readback | Наблюдение уровня есть; общая проблема отмены очереди затрагивает и этот путь |
| Фоновая музыка | media_control и локальные медиа-пути → MediaSessionControl → dumpsys/targeted command → readback | Выбор сессии и readback есть; отмена перед отправкой и дедупликация разных входов неполны |
| Поиск / ввод | search_app, type_text → ACTION_SEARCH / semantic editor / IME / app URI → наблюдение | search_app имеет более сильные ограничения, чем отдельный type_text; current target может смениться при проверке |
| Экран | read_screen, wait_for_screen → fresh accessibility snapshot; screenshot → native capture; tap/click → действие и наблюдение | Пустое дерево уже приводит к визуальному наблюдению; покрытие Android-факта действия ограничено |
| Навыки | Общий app-search в Agent.instructions; load_app_skill загружает youtube | Это инструкции исполнителю, не инструменты Live и не механизм исполнения AGENTS.md из репозитория |
| Интернет | web_search → hosted Responses tool → итог и WebSources → архив ссылок | Инструмент зарегистрирован; сетевое выполнение hosted, полный результат ожидается буферизацией HTTP |
| Знания | knowledge_list/save/delete → KnowledgeRepository → revision/transaction → tool output | Revision-защита есть; выборка не соблюдает бюджет контекста; явное намерение задано промптом |
| Старые беседы | conversation_search/read → bounded/paged repository, исключая текущую беседу | Ограничения результатов и tombstones есть; нужен отдельный контекст текущей Live-беседы |
| Сохранение беседы | turn.done → AppDataWork → SQLite + workspace → после detach/release отдельная сводка | Дедупликация SQLite не защищает побочные эффекты до записи; сводка неправильно обрезает длинные беседы |
| Напоминания | reminder_list/create/cancel, timer_create → SQLite → non-wakeup alarm → pending → claim → доставка | Доставка, отмена после claim и восстановление после смерти процесса имеют дефекты |
| Плановые действия | reminder action → разрешённые open_app/volume → scoped verification → finish | Только типизированные действия; отмена после claim не проверяется перед выполнением |
| Дайджесты — настройки | digest_list/read/mutate/source_list/source_search/source_mutate/schedule_list/schedule_mutate → DigestCommands → revision/operation UUID | Контракты имеют состояния и idempotency; queued не выдается за готовый результат |
| Дайджесты — работа | digest_refresh/job_read/cover_generate → durable queue → JobScheduler → claim → research → publish → covers | Ревизии, account epoch и tombstones охраняют публикацию; неизвестный результат генерации не повторяется вслепую |
| Просмотр дайджеста | digest_open → экран; DreamService → только локальный cache, без речи | Открытие возвращает requested; факт воспроизведения/выбора не следует из отправки Intent |
| Модель / Fast | ModelCatalogue → отдельные discussion/summary/live настройки → запрос + x-codex-routing-hint | Выборы разделены; returned default не доказывает отсутствие приоритета. Исторические измерения Fast не заменены выводом по одному полю |
| Образцы голоса | Отдельное playback-only воспроизведение private/cache assets | Не требует микрофона/Agent; реальные lifecycle и audio outcomes не проверяются host-фейками |
| Завершение / HOME | Кнопка / модельный путь / fatal → VoiceSession.close → dispose → orb detach → архив; HOME сохраняет Live | Быстрое локальное освобождение проверялось ранее; смысловой переход до close неисправен |
| Logout / ошибки | AccountStore.clear → invalidate summary/digest jobs + close; ошибки сети → fatal / tool failure | Защита поздних публикаций есть; HTTP не имеет общего cancellable handle, refresh не single-flight |

## Общие дефекты orchestration

### H1 · Неверно трактуется содержимое делегирования

`LiveProtocol.java:8–18`, `LiveConnection.java:143–155,206–208`. Прямое закрытие требует точной строки. В реальном handoff пришла исходная реплика пользователя; upstream Codex называет поле `input_transcript`. Промпт не превращает этот текст в типизированный вызов локального инструмента. Это подтверждено двумя реальными неудачными разговорами; см. `model-controlled-close.md`.

Схема настоящего `end_conversation` находится в `AgentTools`, исполнение — в `Agent.executeTool`. Live не получает эту function-схему напрямую. Аналогичный риск относится к ожиданию «полностью переформулированной цели» от handoff. Локальная media-развилка тоже сопоставляет естественные русские фразы, а не типизированное решение модели; это отдельный конкурирующий путь с другим поведением для других языков.

Источник: https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/endpoint/realtime_websocket/protocol_frameless_bidi.rs

### H2 · Теряется контекст между обычным разговором и действием

`Agent.java:55–56,159–168`; `LiveConnection.java:282–327`. Финальные голосовые реплики сохраняются в `workspace.transcript`, а в Responses отправляется только `history` делегированных задач. Снимка текущей беседы к делегации нет.

Воспроизведение: в текущем transcript есть «Мой рейс в 18:37, аэропорт Северный», затем Agent получает «Напомни об этом за час». Host-пробник показал `Current spoken context reached Agent: false`, `Delegation text reached Agent: true`. Это затрагивает поиск с уточнениями, местоимения, запоминание, напоминания и любые действия со ссылкой на обычную беседу. Подтягивание старого архива автоматически эту ошибку не исправляет и нарушило бы установленную политику памяти.

### H3 · Нет пути изменения выполняющейся задачи

`LiveConnection.java:25,31,228–231,332–346`; `Agent.java:34–44`. Все обычные делегации попадают в один executor и сериализуются. Новая просьба «нет, остановись» ожидает предыдущую, а не отменяет/изменяет её. В протоколе задачи отсутствуют cancel/revision/superseded и связь с user_bidi_turn_id. Ожидание очереди нельзя выдавать за выполненную отмену.

### H4 · Нет общей дедупликации побочных эффектов

`LiveConnection.java:225–234,280–294`. item.id защищает зеркало одной делегации. Медиа-действие также запускается из финального user turn; эти два пути не используют общий action ID. Дедупликация записи реплик в SQLite происходит после side effects и их не защищает. Повторяющийся turn, новое ID делегации для той же задачи и осознанный повтор пользователя сейчас не представлены как разные случаи.

### H5 · Ошибки доставки результата теряются

`LiveConnection.java:237–238,268–279,348–370`. Обработчик событий глотает все исключения; ошибка local media лишь логируется; `WebSocket.send` не проверяется, а null socket молча пропускается. Исполнитель может завершить действие, но Live не получит ни результата, ни явного состояния доставки. Правило «не говори готово без результата» не заменяет ack/error-контракт.

### H6 · Нет общего подтверждённого исхода задачи

`Agent.java:98–126,302–312`; `LiveConnection.java:332–359`. Результаты представлены строками, JSON, image arrays или свободным финальным ответом. Голос получает speakable-текст. Для TV observations и digest jobs уже есть хорошие scoped статусы, но общей границы `sent / observed / failed / unconfirmed / cancelled` для делегации нет. Ответ модели без инструмента может завершить поручение независимо от факта действия. Нельзя исправлять это универсальным запретом ответов без tools: часть допустимых задач не требует мутаций.

### H7 · Отмена не прерывает транспорт HTTP

`Agent.java:29–30`; `Http.java:29–60`; `LiveConnection.java:375–385`. cancel — флаг, shutdownNow — interrupt; запросы выполняются отдельным HttpURLConnection, а close отменяет другой OkHttp-клиент. HTTP может продолжать работу после закрытия. Новые независимые беседы не должны ждать его; публикация поздних результатов требует generation/revision guards.

## Конкретные дефекты исполнения

| ID / приоритет | Ошибка и сценарий | Код |
|---|---|---|
| A1 / P1 | Mutating FutureTask уже поставлена в main queue; worker прерван или истёк timeout, но task не отменена. После освобождения main looper действие всё равно может выполниться | TvTools.java:82–85 |
| A2 / P1 | ADB может выдать ошибку после отправки keyevent; accessibility fallback отправляет тот же BACK/arrow повторно | TvTools.java:66–78; AdbRemote.java:91–152 |
| A3 / P2 | Media session read завершается после отмены, затем pause/play отправляется без проверки interrupt перед dispatch | MediaSessionControl.java:47,67–69 |
| A4 / P2 | Обычный type_text допускает focused password/unrelated editor и смену поля внутри того же пакета; search_app проверяет точнее | TvAccessibility.java:161–169; DirectInput.java:43 |
| A5 / P2 | Accessibility navigate не освобождает root/focused/next handles на выходах | TvAccessibility.java:382–396 |
| A6 / P2 | search_app(current) разрешает пакет до действия, но target для проверки выбирает заново после него. При смене приложения app_visible может относиться к другому пакету | TvActionObserver.java:162–169 |
| V1 / P2 | Параллельные fresh() обновляют один expired refresh token; успешное сохранение первого заставляет второй запрос ошибиться по revision | AccountStore.java:96–127 |
| V2 / P2 | close во время refresh не проверяется до Live POST: уже закрытый вызов может создать удалённую сессию и отбросить её ID | LiveConnection.java:71–86 |
| V3 / P2 | Нет аккаунта/нечитаемые данные: begin меняет невидимый в compact режиме status и оставляет инертный orb | VoicePanel.java:42–49,98–104; TvAccessibility.java:57–58 |
| V4 / P2 | setMuted(true) ставится на native worker, затем closed=true; guard worker может выбросить задачу mute. Лог «немедленно» опережает факт | voice/NativeWebRtcTransport.java:137–148,209–214; VoicePanel.java:82–83 |
| V5 / P2 | Однократная ошибка idle recorder вызывает stop без уведомления владельца/восстановления; wake остаётся отключённым до внешнего refresh | WakeDetector.java:162–166; AssistantService.java:62–78 |
| D1 / P1–P2 | Reminder получает completed до уведомления/TTS. После заполнения агрегата 1500 символов следующие body не добавляются, но их строки всё равно завершены | reminders/ReminderDispatcher.java:47–59; ReminderSpeech.java:43–69 |
| D2 / P2 | После смерти процесса между claim и finish reminder остаётся delivering; recovery вызывается только после BOOT_COMPLETED, а не обычного рестарта | data/ReminderRepository.java:57–78,163; reminders/BootReceiver.java:13–14 |
| D3 / P2 | cancel возвращает true для delivering, но dispatcher выполняет действие по уже прочитанной записи и не перепроверяет отмену | data/ReminderRepository.java:149–160; reminders/ReminderDispatcher.java:46–51 |
| D4 / P2 | knowledge_list возвращает до 100 полных заметок × 4000 code points; бюджет context() здесь не действует | KnowledgeTools.java:12; data/KnowledgeRepository.java:19–20; DataPolicy.java:13–14 |
| D5 / P2 | Сводка выбирает tail60 в хронологическом порядке, прекращает чтение при 12000 символах и теряет новейшие решения; первая слишком длинная реплика даёт пустой input | ConversationSummarizer.java:40–47,95–97 |
| D6 / P2, большой backlog | Все due переводятся в pending, но обрабатываются только первые 200; оставшиеся не получают новый alarm и ждут внешнего события | data/ReminderRepository.java:60–67; reminders/AlarmScheduler.java:32 |

## Что проверка не объявляет дефектом без дополнительного факта

- Закрытый код desktop Codex не исследован; публичный prompt/парсер не доказывает устройство его UI закрытия.
- Текстовые и Live модели — разные каталоги; присутствие модели в text catalog не доказывает её доступность для Live.
- Fast возвращает raw tier=default; это не основание объявить снижение приоритета. Ранее измеренная скорость и requested/served продолжают учитываться отдельно.
- Дайджесты имеют реальные revision/operation/account guards и persistent job states. Их queued/uncertain не следует исправлять слепым повтором.
- Снимки и координаты имеют общий mutable размер; влияние конкурентного capture/tap требует отдельного воспроизведения.
- Выбор фонового accessibility window под системным диалогом и editor после interstitial требуют проверки конкретных приложений.
- Явное пользовательское намерение для памяти описано промптом; отдельного runtime объекта разрешения нет. Это пробел границы политик, а не доказанная запись без разрешения в этой проверке.
- App-level reconnect сейчас отсутствует: sideband failure завершается fatal, WebRTC disconnect имеет grace period. Это нужно назвать явно при проектировании восстановления.

## Реальное покрытие тестов

Полный `bash scripts/test.sh` прошёл. Он покрывает policy helpers, bounded наблюдения/ожидания, порядок RemoteSequence, форматы результатов, фальшивый Agent HTTP, fresh workspace, revision/tombstone, отдельные SQLite/digest сценарии. Однако зелёный набор не проверяет всю цепочку реальной Live-модели и Android.

Нужны отдельные regression-сценарии на границах:

1. Обычная голосовая реплика → делегированная ссылка на её содержание; snapshot не содержит старых бесед или будущих реплик.
2. Изменение/отмена задачи во время зависшего HTTP; старый ответ не повторяет действие и не перекрывает новую задачу.
3. RTC + sideband + user turn + делегация одного действия; ровно один эффект, осознанный повтор остаётся возможен.
4. Post-dispatch ADB timeout; результат unconfirmed, без повторного нажатия.
5. Заблокированный main/native worker; после отмены нет поздней мутации, mute не отбрасывается.
6. Single-flight refresh при Live/summary/digest, logout во время refresh; после close нет нового Live POST.
7. Reminder delivery/error/standby/overflow, cancel-after-claim, process restart delivering, backlog >200.
8. Maximum knowledge output, summary с длинной первой репликой и важным последним решением.
9. Речь на разных языках с прощанием, благодарностью с продолжением, отменой задачи, паузой видео. Проверяется реальный инструмент и фактический исход; не подставленный trigger.

## Предлагаемая общая граница исправления

Исправлять только промпты/скиллы недостаточно. Общий слой должен хранить: delegation ID и исходный turn ID, bounded snapshot **текущей** беседы, ID/ревизию задачи, исполняемые action ID, cancel token и подтверждённый outcome. Инструменты сообщают отправку и наблюдение раздельно; Live получает достоверное состояние. Навыки описывают процедуры поверх этих контрактов.

Это не предложение второго распознавателя, второй Live-сессии или запроса модели на каждый аудиофрагмент. Смысл запроса определяет модель. Обработка IDs, отмены, доставки результата и проверки факта — ответственность harness.

Порядок работ: сначала контракт контекста/задачи/результата и отмена; затем устранение поздних/повторных side effects; далее авторизация/жизненный цикл и фоновые доставки; после этого выравнивание инструкций и сценарные испытания всех групп. До отдельного исправления старое правило «закрывать без Agent HTTP» остаётся ограничением, которое нельзя незаметно обойти новым semantic classifier.
