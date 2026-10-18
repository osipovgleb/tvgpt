package local.tvgpt.assistant;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** A short known D-pad path; validate the entire batch before sending any key. */
final class RemoteSequence {
  interface Key {
    void send(String direction) throws Exception;
  }

  static String execute(JSONArray steps, Key remote, ActionWait.Time time) throws Exception {
    List<String> keys = validate(steps);
    int sent = 0;
    try {
      for (String key : keys) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        remote.send(key);
        sent++;
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (sent < keys.size()) time.sleep(100);
      }
      return "Отправлено нажатий в последовательности: " + sent + ". Проверь результат";
    } catch (java.util.concurrent.CancellationException cancelled) {
      throw cancelled;
    } catch (InterruptedException cancelled) {
      Thread.currentThread().interrupt();
      throw cancelled;
    } catch (Exception unavailable) {
      return "Отправлено нажатий: "
          + sent
          + " из "
          + keys.size()
          + ". Затем пульт стал недоступен. Не повторяй последовательность без проверки экрана";
    }
  }

  private static List<String> validate(JSONArray steps) {
    if (steps == null || steps.length() < 1 || steps.length() > 8)
      throw new IllegalArgumentException("Нужно от 1 до 8 шагов");
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < steps.length(); i++) {
      JSONObject step = steps.optJSONObject(i);
      if (step == null || step.length() != 2 || !step.has("direction") || !step.has("count"))
        throw new IllegalArgumentException("Нужны direction и count для каждого шага");
      Object direction = step.opt("direction"), countValue = step.opt("count");
      if (!(direction instanceof String) || !(countValue instanceof Number))
        throw new IllegalArgumentException("Нужны строка направления и целое число нажатий");
      int count = ((Number) countValue).intValue();
      if (count < 1 || count > 20 || ((Number) countValue).doubleValue() != count)
        throw new IllegalArgumentException("Нужно от 1 до 20 нажатий в шаге");
      String key = (String) direction;
      if (key.equals("select")) {
        if (i != steps.length() - 1 || count != 1)
          throw new IllegalArgumentException("Нужно одно select только в последнем шаге");
      } else if (!key.equals("up")
          && !key.equals("down")
          && !key.equals("left")
          && !key.equals("right")) {
        throw new IllegalArgumentException("Нужны только стрелки и один завершающий select");
      }
      if (keys.size() + count > 30)
        throw new IllegalArgumentException("Нужно не более 30 нажатий в последовательности");
      for (int n = 0; n < count; n++) keys.add(key);
    }
    return keys;
  }
}
