# Разработка и установка TV GPT

Перед изменениями прочитать [правила интерфейса](ui/interface-contract.md) и `AGENTS.md`. Пользовательские уточнения имеют приоритет над старым планом. Репозиторий использует локальный Git; GitHub и контейнеры не требуются.

## Разработка

Цель — самостоятельный TCL Android 12 / ARM32. Приложение: native Java UI и lifecycle, native WebRTC Java/C++ для звука, локальное распознавание фразы, SQLite для данных. Это собственный клиент подписочного протокола, не перенос полного Codex CLI. Не возвращать WebView, PCM/Base64-мост или внешний desktop runtime.

Работать в отдельной ветке/рабочем дереве, не затирать параллельные изменения. Логотип сайдбара — прозрачный Android VectorDrawable `app/res/drawable/sidebar_logo.xml`; редактируемый SVG находится в `docs/ui/assets/sidebar-logo.svg`. При изменении знака синхронизировать оба вектора и проверить его на фоне меню `#1A1A1D`.

Новые страницы добавлять отдельными компонентами, общий стиль — через `ui/`. MainActivity только маршрутизирует страницы. Live, микрофон, хранилище и расписание имеют отдельных владельцев.

Локальная проверка:

```bash
bash scripts/format-java.sh --check
bash scripts/test.sh
bash scripts/build.sh
bash scripts/build-tests.sh
npm run quality
```

AI Slop — dev-зависимость, не часть APK. Не выключать проверки ради оценки. Известные предупреждения о канонических подписочных URL объясняются; нельзя делать произвольный сервер для отправки токенов ради удаления предупреждения.


## Обновление устройства — только когда разрешено

На18 октября пользователь разрешил установку текущей UI-ревизии командой **«выкатывай»**, отменив прежнюю паузу. Для последующих обновлений учитывать актуальное разрешение. Сборка APK сама по себе не означает разрешение установки. Не прерывать текущий разговор тестовой установкой или instrumentation.

Перед обновлением убедиться, что нужные изменения интегрированы и исходный signing key доступен. Не удалять приложение ради смены ключа: так теряются аккаунт и приватные данные. В текущей машине исходный debug key лежит в `/home/gleb/projects/tvgpt/build/debug.keystore`; не коммитить его.

```bash
TVGPT_KEYSTORE=/path/to/original/debug.keystore bash scripts/build.sh
adb -s TV_SERIAL install --no-incremental -r build/tvgpt.apk
python3 scripts/connect-tv.py TV_SERIAL
```

`TV_SERIAL` — пример, подставить проверенный адрес устройства. Скрипт подключения изменяет системные привязки: запускать только в рамках разрешённого обновления. Он сохраняет сторонние accessibility-службы, не должен сбрасывать уже рабочие привязки и проверяет реальные bound-процессы. Не вмешиваться в ожидающий foreground start `AudioKeepAlive` — на TCL это приводило к таймауту службы.

В «Настройках» проверить оба системных поля: `assistant` и `voice_interaction_service`, а также подключение `TvAccessibility`. Одной записи роли ASSISTANT недостаточно. Welcome/setup должен предоставлять явную кнопку настройки и проверку результата.

Google Katniss на этом TCL конкурирует за микрофон. Действие HOTWORD_DISABLE и запрещённый RECORD_AUDIO сами по себе не доказывают остановку аппаратного захвата. Последняя запись проекта: отключение **пакета** Katniss сработало, отдельных native-компонентов — отклонено прошивкой. Не переносить это утверждение на другие устройства; проверять журнал и несколько реальных разговоров. Обратная команда для ранее отключённого пакета — `pm default-state --user 0 com.google.android.katniss`. Приложение Google TV launcher — другой пакет.

## Проверка на телевизоре

Сначала читаемые журналы и снимок экрана. Не использовать `uiautomator dump` без необходимости: на этом TCL он вмешивался в accessibility. Instrumentation основной программы может её перезапустить, поэтому не запускать во время использования. Для SQLite/preferences есть отдельный пакет и `scripts/test-device-data.py`; он тоже требует разрешённого окна тестирования.

Нужно проверить реальную речь → окончательную расшифровку → ответ из динамиков, а не только RTP-счётчики. Минимум три цикла открытия/закрытия с пульта и по фразе; работа под другими приложениями, остановка микрофона, сохранение контекста. Тест UI и standby-напоминаний — по контракту интерфейса.

До/после по нагрузке сравнивать одинаковые версии условий: экран/разговор, прогрев, громкость, сетевое состояние, несколько интервалов CPU и PSS. Не сравнивать холодный процесс с прогретым и не приписывать старое ускорение WebView→native новым косметическим изменениям.

В release-отчёте указывать commit, локальные проверки, что реально проверено на ТВ, что осталось, и факт установки отдельно. Текущий статус — [app-sections-status.md](app-sections-status.md).

## Дайджесты: фон, генерация и заставка

Ветка реализации `codex/news-digests` добавляет самостоятельный Android-модуль: SQLite-конфигурация и очередь, JobScheduler/непробуждающий AlarmManager, news-only подписочный поиск, optional image_generation, gallery и DreamService. Настольный клиент не исполняет задания приложения. Imagegen не является условием работы: фотографии берутся из RSS/Atom/og:image исходных публикаций; без доступного изображения остаётся текст.

Фон не использует историю разговоров, AppDataWork, аудиофокус или микрофон. Изменение настроек/удаление/logout проверяются ревизиями перед публикацией. При неизвестном результате генерация с тем же ключом не посылается повторно вслепую. Расписания восстанавливаются после boot/time/timezone/package replace; накопленные сроки объединяются. Android может задержать выполнение во сне/без сети; точное обновление в указанную минуту не гарантируется.

Новый режим отдельного self-targeted data APK: `adb -s TV_SERIAL shell am instrument -w -e mode digests local.tvgpt.sectionschecks/local.tvgpt.assistant.DataCheck`. Только в разрешённом окне; затем удалить отдельный тестовый пакет. Это не проверка реального интернета или системной заставки.

После разрешённой установки открыть «Дайджесты», создать тему и расписание, добавить HTTPS источник. Голосовые команды должны делать то же через общие команды. Подтвердить настоящий выпуск и фото fallback; генерацию считать доступной только после получения приложением декодируемого изображения. Подробности — `docs/imagegen-subscription-investigation.md`.

В «Дайджестах» выбрать новостную заставку через системные настройки. Приложение не переписывает secure settings и не отключает пакеты Google/TCL. Если прошивка скрывает системный выбор стороннего DreamService, настройка компонента через разрешённое подключение допустима только в рамках явного поручения владельца заменить заставку, с сохранением прежнего значения и проверкой настоящего запуска. Это не действие автоматического обновления и не доказательство автозапуска. Отдельно наблюдать автозапуск, выход пультом, вызов помощника, standby/resume, офлайн-кеш и нагрузку ARM32. До этой проверки не заявлять замену заставки Google выполненной на устройстве.

На TCL использовать только `--no-incremental`:18 октября системный DataLoader после успешного сообщения ADB завершился через `onSystemDataLoaderUnrecoverable`, удалил пакет и его данные. Не повторять инкрементальную установку. После обычного обновления проверить наличие пакета, сохранение UID, аккаунта и реальную привязку служб; Success сам по себе недостаточен.

## Harness repair (18 October)

Run the full test script, including test-data-repair.sh and test-tv-actions.sh. New pure suites cover current-call context, cancellation, task receipts and outcome evidence; HTTP tests abort the actual connection. Source-only and bytecode API31 checks do not prove TV audio compatibility. The user permits the existing Agent end_conversation tool; no canonical delegation phrase or additional speech model is used. Archive memory remains explicit only.

Install only a named immutable source/APK snapshot from the latest six-section Digests worktree. Use streamed --no-incremental -r and preserve signing key, UID and account. Check idle usage first, then verify binding, ordinary first call, repeated calls/closing, HOME and the visible UI. Synthetic delegation injection tests exercise the executor path; they do not prove real Live intent delegation.

## TCL: assistant stops after working for a while

On18 October, exit-info recorded `tguard-kill` at14:09, followed by `TclAppBoot:forbid restart/bind service` for AssistantService/TvAccessibility/SessionService. The default assistant role was still set, but its process was gone. TCL-specific `APP_AUTO_START` and `APP_ASSOC_START` appops were `ignore`. This is distinct from a Live connection error or a microphone capture failure.

The existing assistant handover and `scripts/connect-tv.py` now query these two operations, allow them only for `local.tvgpt.assistant` if supported, verify readback, and persist appops settings. Unsupported operations are skipped. Existing TCL Guard, other apps and global power policy stay unchanged. Original device values were both `ignore`; per-device rollback is `cmd appops set --user 0 local.tvgpt.assistant APP_AUTO_START ignore` and the corresponding APP_ASSOC_START command. Restoring those values reintroduces startup restrictions on this TCL.

Successful readback and immediate invocation do not prove reboot persistence or immunity to future OEM kills. Observe idle/background operation and inspect `dumpsys activity exit-info local.tvgpt.assistant` before declaring long-term recovery verified.

TCL Guard can still kill an allowed-to-start app. Its documented TGuardService --ma mKillWhiteList operation appends this package to the current runtime exemption list without changing other entries. Assistant setup queries support/membership and verifies the addition. AssistantService also makes one bounded background attempt per process start, using the existing paired local ADB connection. This list is not claimed permanent across Guard/TV restarts. If accessibility's window token is unavailable after rebind, the assistant catches BadTokenException, clears the failed overlay and uses its existing native system voice window. onUnbind invalidates the cached accessibility instance.

### Volume target and external audio

Relative volume commands probe Android's current HDMI System Audio mode through a short-lived,
allowlisted shell helper using the app's own paired local ADB connection. An active CEC audio system
(logical address 5) receives a relative `setSystemAudioVolume` command. No device manufacturer,
external bridge package, ARC switch, optical route change, root, or persistent worker is required.
An explicit inactive system-audio result retains the normal Android stream path. An unknown route
or uncertain dispatch never falls back to a second volume mutation. This path requires local pairing;
other transports (including IR-only remotes) are not implemented.

External success requires a fresh CEC Report Audio Status with a volume change in the requested
direction. Stale/missing reports, unknown CEC volume and unchanged boundaries remain unconfirmed;
Android STREAM_MUSIC readback is not evidence of external amplifier volume. OEM dumpsys formatting
can vary; unsupported readback must remain unconfirmed rather than inventing a successful result.
The relative tool does not offer an external absolute-percent setting.

Device check on 18 October: common helper, without manufacturer matching, observed external
27→30→27; injected Android KEYCODE_VOLUME_UP produced no new CEC packet on this TCL. The physical
remote's existing raw-input bridge was left unchanged. This confirms the external backend here,
not compatibility with every TV or IR-controlled audio system.

### Unexpected voice invocation

Inspect `TVGPTWake` together with `TVGPTSession show source=…` before attributing an unexpected
opening to a remote key or service restart. On 18 October the captured opening at 17:31:38 had
`source=wake`. The older log did not distinguish partial from final recognition, so it cannot
prove which hypothesis triggered that particular opening.

The local detector now accepts finalized utterances only. Partial hypotheses are never sufficient
to start Live, even if they temporarily contain the complete wake phrase. The final event is logged
explicitly without storing microphone audio. This preserves the existing single native recorder and
capture format. It can add endpointing latency and does not guarantee rejection of TV audio that
is itself finalized as the wake phrase. The recovery regression exercises partial match → corrected
non-match → final match, checking zero early starts and one final invocation. Acoustic false-positive
rate still needs on-device observation; the host test does not measure it.
