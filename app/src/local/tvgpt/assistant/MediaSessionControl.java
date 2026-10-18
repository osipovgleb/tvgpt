package local.tvgpt.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Shell-privileged Android media controller, independent of foreground UI and audio history. */
final class MediaSessionControl {
  interface Remote {
    String shell(String command, int timeoutMs) throws Exception;
  }

  private static final Pattern HEADER =
      Pattern.compile("(?m)^ +(.+) ([a-zA-Z0-9_.]+)/(.+) \\(userId=(\\d+)\\)\\s*$");

  private static final class Session {
    String tag, pkg, owner, user;
    int state;
    boolean active;

    boolean same(Session other) {
      return tag.equals(other.tag)
          && pkg.equals(other.pkg)
          && owner.equals(other.owner)
          && user.equals(other.user);
    }

    JSONObject observation() throws Exception {
      return new JSONObject()
          .put("package", pkg)
          .put("session_tag", tag)
          .put("session_owner", owner)
          .put("playback_state", state);
    }
  }

  static String requestedName(JSONObject args) {
    return args.isNull("name") ? "" : args.optString("name").trim();
  }

  static JSONObject execute(Remote remote, ActionWait.Time time, String action, String pkg)
      throws Exception {
    if (!action.equals("pause") && !action.equals("play"))
      return rejected("Нужна команда pause или play");
    List<Session> sessions = parse(remote.shell("dumpsys media_session", 1500));
    List<Session> candidates = new ArrayList<>();
    for (Session session : sessions)
      if (session.active && (pkg.isEmpty() || pkg.equals(session.pkg))) candidates.add(session);
    if (pkg.isEmpty() && candidates.size() > 1) {
      List<Session> playing = new ArrayList<>();
      for (Session session : candidates) if (session.state == 3) playing.add(session);
      if (!playing.isEmpty()) candidates = playing;
    }
    if (candidates.isEmpty()) return rejected("Активная медиасессия приложения не найдена");
    if (candidates.size() != 1) return rejected("Несколько медиасессий: уточни приложение");
    Session target = candidates.get(0);
    // Android's monitor selects only by tag. A collision must never control a different app.
    int sameTags = 0;
    for (Session session : sessions)
      if (session.active && session.tag.equals(target.tag)) sameTags++;
    if (sameTags != 1) return rejected("Не удалось однозначно выбрать медиасессию");
    if (target.owner.isEmpty()) return rejected("Не удалось определить владельца медиасессии");
    int desired = action.equals("pause") ? 2 : 3;
    if (target.state == desired) return result(target, true, "Состояние уже установлено");
    String command =
        "printf '%s\\n' " + quote(action) + " 'q' | cmd media_session monitor " + quote(target.tag);
    String receipt = remote.shell(command, 2000);
    if (receipt.contains("No session found")
        || receipt.contains("Failed to dispatch")
        || receipt.contains("Error")
        || receipt.contains("Invalid command")) return rejected("Медиасессия не приняла команду");
    final Session[] latest = {target};
    try {
      boolean observed =
          ActionWait.until(
              () -> {
                latest[0] = null;
                for (Session session : parse(remote.shell("dumpsys media_session", 1000))) {
                  if (session.same(target)) {
                    latest[0] = session;
                    return session.state == desired;
                  }
                }
                return false;
              },
              1800,
              time);
      return result(latest[0], observed, "Команда мультимедиа отправлена: " + action);
    } catch (InterruptedException cancelled) {
      Thread.currentThread().interrupt();
      throw cancelled;
    } catch (Exception unavailable) {
      return result(null, false, "Команда мультимедиа отправлена: " + action)
          .put("observation_error", unavailable.getClass().getSimpleName());
    }
  }

  private static JSONObject result(Session session, boolean observed, String receipt)
      throws Exception {
    return ActionObservation.result(
            receipt,
            session == null ? new JSONObject() : session.observation(),
            observed ? (session.state == 2 ? "playback_paused" : "playback_active") : "")
        .put("requires_visual", false);
  }

  private static JSONObject rejected(String message) throws Exception {
    return ActionObservation.result(message, new JSONObject(), "")
        .put("status", "rejected")
        .put("requires_visual", false);
  }

  static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  private static List<Session> parse(String dump) {
    List<Session> result = new ArrayList<>();
    Matcher headers = HEADER.matcher(dump);
    List<Integer> starts = new ArrayList<>();
    List<Session> records = new ArrayList<>();
    while (headers.find()) {
      if (!headers.group(1).equals(headers.group(3))) continue;
      Session record = new Session();
      record.tag = headers.group(3);
      record.pkg = headers.group(2);
      record.user = headers.group(4);
      starts.add(headers.end());
      records.add(record);
    }
    for (int i = 0; i < records.size(); i++) {
      String block =
          dump.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : dump.length());
      Session record = records.get(i);
      record.active = Pattern.compile("(?m)^ +active=true\\s*$").matcher(block).find();
      Matcher owner =
          Pattern.compile("(?m)^ +ownerPid=(\\d+), ownerUid=(\\d+), userId=\\d+\\s*$")
              .matcher(block);
      Matcher state = Pattern.compile("(?m)^ +state=PlaybackState \\{state=(\\d+),").matcher(block);
      record.owner = owner.find() ? owner.group(1) + ":" + owner.group(2) : "";
      record.state = state.find() ? Integer.parseInt(state.group(1)) : -1;
      result.add(record);
    }
    return result;
  }
}
