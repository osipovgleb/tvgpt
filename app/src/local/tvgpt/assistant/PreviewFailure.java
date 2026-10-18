package local.tvgpt.assistant;

import org.json.*;

final class PreviewFailure {
  static String response(int status, String body) {
    if (status == 401 || status == 403)
      return "Сервер не разрешил прослушивание. Проверьте вход и доступ к подписке.";
    try {
      JSONObject json = new JSONObject(body);
      JSONObject error = json.optJSONObject("error");
      if (error == null) error = json;
      String param = error.optString("param").toLowerCase(java.util.Locale.ROOT);
      String code = error.optString("code").toLowerCase(java.util.Locale.ROOT);
      String message = error.optString("message").toLowerCase(java.util.Locale.ROOT);
      if (param.contains("voice")
          || code.contains("voice")
          || ((status == 400 || status == 422) && message.contains("voice")))
        return "Этот голос недоступен для вашего подключения.";
    } catch (JSONException ignored) {
    }
    return "Не удалось прослушать голос (HTTP " + status + ").";
  }

  private PreviewFailure() {}
}
