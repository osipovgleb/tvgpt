package local.tvgpt.assistant;

import java.util.Iterator;
import org.json.*;

/** Tool contracts shared by the model and the native executor. */
final class AgentTools {
  private static void inputTools(JSONArray result) throws Exception {
    result.put(
        tool(
            "search_app",
            "Передать поисковый запрос прямо приложению. YouTube поддерживает русский запрос через"
                + " прямой поиск; затем проверь результаты.",
            new JSONObject()
                .put("name", new JSONObject().put("type", "string"))
                .put("query", new JSONObject().put("type", "string"))));
    result.put(
        tool(
            "type_text",
            "Ввести целую фразу в активное поле поиска напрямую, включая русский текст; сначала"
                + " открой поиск. Проверь результат после ввода.",
            new JSONObject()
                .put("text", new JSONObject().put("type", "string"))
                .put("submit", new JSONObject().put("type", "boolean"))));
    result.put(
        tool(
            "media_control",
            "Пауза или продолжение через медиасессию, включая фоновое приложение, без открытия его"
                + " экрана. name — название или пакет приложения, null для единственного активного"
                + " проигрывателя. Результат подтверждается состоянием выбранной медиасессии;"
                + " для паузы не нужно открывать приложение или проверять скриншот.",
            new JSONObject()
                .put(
                    "action",
                    new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("pause").put("play")))
                .put(
                    "name",
                    new JSONObject().put("type", new JSONArray().put("string").put("null")))));
  }

  static JSONArray all() throws Exception {
    JSONArray result = new JSONArray();
    result.put(new JSONObject().put("type", "web_search").put("external_web_access", true));
    result.put(
        tool(
            "load_app_skill",
            "Прочитать встроенный навык приложения. Сейчас доступен youtube /"
                + " com.google.android.youtube.tv. Загружай один раз за разговор; для других"
                + " приложений используй общий app-search.",
            new JSONObject().put("name", new JSONObject().put("type", "string"))));
    result.put(
        tool(
            "open_app",
            "Открыть установленное приложение",
            new JSONObject().put("name", new JSONObject().put("type", "string"))));
    result.put(
        tool(
            "volume",
            "Изменить громкость на один шаг",
            new JSONObject()
                .put(
                    "direction",
                    new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray().put("up").put("down")))));
    result.put(
        tool("screenshot", "Посмотреть изображение текущего экрана телевизора", new JSONObject()));
    result.put(
        tool(
            "tap",
            "Нажать по координатам в пикселях последнего снимка screenshot",
            new JSONObject()
                .put("x", new JSONObject().put("type", "integer"))
                .put("y", new JSONObject().put("type", "integer"))));
    result.put(
        tool(
            "mute",
            "Выключить или включить звук телевизора; возвращает фактическое состояние mute",
            new JSONObject().put("enabled", new JSONObject().put("type", "boolean"))));
    inputTools(result);
    observationTools(result);
    navigationTools(result);
    KnowledgeTools.schemas(result);
    ConversationTools.schemas(result);
    ReminderTools.schemas(result);
    return result;
  }

  private static void observationTools(JSONArray result) throws Exception {
    result.put(
        tool(
            "list_apps",
            "Список доступных установленных приложений для выбора места поиска контента",
            new JSONObject()));
    result.put(
        tool(
            "wait_for_screen",
            "Ждать ожидаемое приложение/текст до указанного предела, вернуться сразу при появлении."
                + " Пустое дерево требует визуальной оценки, не означает ошибку.",
            new JSONObject()
                .put("package", new JSONObject().put("type", "string"))
                .put("contains", new JSONObject().put("type", "string"))
                .put("timeout_ms", new JSONObject().put("type", "integer"))));
    result.put(tool("read_screen", "Прочитать доступные элементы экрана", new JSONObject()));
  }

  private static void navigationTools(JSONArray result) throws Exception {
    remoteSequenceTool(result);

    result.put(
        tool(
            "navigate",
            "Нажать кнопку пульта count раз (1–20)",
            new JSONObject()
                .put(
                    "direction",
                    new JSONObject()
                        .put("type", "string")
                        .put(
                            "enum",
                            new JSONArray()
                                .put("home")
                                .put("back")
                                .put("left")
                                .put("right")
                                .put("up")
                                .put("down")
                                .put("select")))
                .put(
                    "count",
                    new JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 20))));
    result.put(
        tool(
            "click",
            "Нажать доступный элемент по его тексту",
            new JSONObject().put("text", new JSONObject().put("type", "string"))));
    result.put(
        tool(
            "end_conversation",
            "Завершить текущий голосовой разговор по просьбе пользователя",
            new JSONObject()));
  }

  private static void remoteSequenceTool(JSONArray result) throws Exception {
    result.put(
        tool(
            "navigate_sequence",
            "Быстро выполнить короткий известный путь пульта одним вызовом, например для"
                + " исчезающего меню. Только по свежему наблюдению или точной последовательности"
                + " пользователя, не угадывай число шагов. До 8 шагов и 30 нажатий всего; только"
                + " стрелки и, при необходимости, один select в самом конце. Без HOME/BACK и"
                + " повторных select. После всей последовательности проверь итоговый экран;"
                + " отправка нажатий не подтверждает выбор или сохранение контента.",
            new JSONObject()
                .put(
                    "steps",
                    new JSONObject()
                        .put("type", "array")
                        .put("minItems", 1)
                        .put("maxItems", 8)
                        .put(
                            "items",
                            new JSONObject()
                                .put("type", "object")
                                .put(
                                    "properties",
                                    new JSONObject()
                                        .put(
                                            "direction",
                                            new JSONObject()
                                                .put("type", "string")
                                                .put(
                                                    "enum",
                                                    new JSONArray()
                                                        .put("up")
                                                        .put("down")
                                                        .put("left")
                                                        .put("right")
                                                        .put("select")))
                                        .put(
                                            "count",
                                            new JSONObject()
                                                .put("type", "integer")
                                                .put("minimum", 1)
                                                .put("maximum", 20)))
                                .put("required", new JSONArray().put("direction").put("count"))
                                .put("additionalProperties", false)))));
  }

  static JSONObject tool(String name, String description, JSONObject props) throws Exception {
    JSONArray required = new JSONArray();
    Iterator<String> keys = props.keys();
    while (keys.hasNext()) required.put(keys.next());
    return new JSONObject()
        .put("type", "function")
        .put("name", name)
        .put("description", description)
        .put("strict", true)
        .put(
            "parameters",
            new JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", required)
                .put("additionalProperties", false));
  }
}
