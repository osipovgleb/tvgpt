# Imagegen в приложении TV GPT

Генерация выполняется самим Android-приложением. Desktop runtime, Python CLI и автоматический переход на платный API не используются. Навык app/assets/skills/imagegen/SKILL.md доступен агенту через load_app_skill и описывает очередь digest_cover_generate, проверку результата и фото fallback.

## Исправленный подписочный протокол

Обычный Responses-запрос с image_generation не является реализацией встроенного standalone imagegen Codex. В актуальном открытом коде Codex его ImagesClient отправляет отдельный JSON-запрос images/generations с моделью gpt-image-2, prompt, background=opaque, quality=auto, size=auto. TV GPT использует тот же маршрут относительно своего фиксированного подписочного backend: https://chatgpt.com/backend-api/codex/images/generations. Авторизация остаётся аккаунтом приложения; дополнительные заголовки — originator=tvgpt и x-codex-image-turn-id. Подмена originator другим клиентом и пользовательский server override не используются.

Первичные источники:
- https://github.com/openai/codex/blob/main/codex-rs/ext/image-generation/src/tool.rs
- https://github.com/openai/codex/blob/main/codex-rs/ext/image-generation/src/backend.rs
- https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/endpoint/images.rs
- https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/images.rs

Ответ должен содержать inline data[].b64_json; произвольная URL-ссылка не считается полученным изображением. Проверяются формат, размер и декодирование BitmapFactory. Только проверенный файл означает supported. HTTP/SSE-отказ означает failed; незавершённый сетевой исход — unconfirmed. Ошибка хранит ограниченный код, но не полный ответ, токены или base64. Фотографии сайта остаются при любом неуспехе.

Транспорт ограничен 180 секундами на запрос, 32 MiB wire и 16 MiB/16 MP на изображение; redirect и автоматический retry выключены. Предел готового кеша 200 MiB защищает ссылки архива и активные pins. Фиксированный канонический URL намерен: произвольный override может отправить подписочный токен третьей стороне.

Модель дайджестов выбирается независимо от обсуждения. Она обрабатывает статьи и источники; генератор изображений использует собственную специализированную модель gpt-image-2, как встроенный imagegen. Выбор текстовой модели не подменяет модель изображения.


## Подтверждение на TCL — 18 октября, 10:46 МСК

Исправлен отдельный read timeout: одного callTimeout(180s) недостаточно, поскольку OkHttp по умолчанию прерывает чтение через 10 секунд. Host-регрессия с ответом через 11 секунд сначала завершилась SocketTimeoutException, затем прошла с явным readTimeout(180s); connect=15s, write=30s, общий call=180s. Сетевой retry и redirects остались выключены.

Приложение выполнило cover-only задание f667915f-438e-4bb8-8964-e76ed74a2871. Наблюдение: ready; imagegen supported; image_kind generated; BitmapFactory 1672×941; файл 1 982 026 байт. На настоящем DigestDreamService показана эта обложка с подписью «Иллюстрация ИИ». Прежние девять новостей сохранены; отдельный выпуск ради замены картинки не создавался. Это подтверждение одной реальной генерации, не гарантия будущей доступности/квоты аккаунта. Результаты: build/imagegen-correct-timeout-device-check.log и build/imagegen-dream-ready.png.

Проверочный instrumentation runner ждёт конечного состояния задания до своего завершения: немедленное finish() перезапускает target-процесс и само обрывает начавшуюся сеть, оставляя корректный unconfirmed. Приложение не повторяет такие неизвестные запросы автоматически. Проверки с исправленной реализацией были отдельными явно запущенными операциями.
