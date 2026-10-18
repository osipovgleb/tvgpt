package local.tvgpt.assistant;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class MediaSessionControlTest {
  private static String session(String tag, String pkg, int uid, int state, boolean active) {
    return "    "
        + tag
        + " "
        + pkg
        + "/"
        + tag
        + " (userId=0)\n"
        + "      ownerPid=42, ownerUid="
        + uid
        + ", userId=0\n"
        + "      package="
        + pkg
        + "\n      active="
        + active
        + "\n"
        + "      state=PlaybackState {state="
        + state
        + ", position=0}\n";
  }

  private static final String PLAYING = session("music", "com.music", 10082, 3, true);
  private static final String PAUSED = session("music", "com.music", 10082, 2, true);

  private static final class Fake implements MediaSessionControl.Remote, ActionWait.Time {
    final List<String> commands = new ArrayList<>();
    String before = PLAYING, after = PAUSED;
    long now;
    boolean sent;

    public String shell(String command, int timeout) {
      commands.add(command);
      if (command.equals("dumpsys media_session")) return sent ? after : before;
      sent = true;
      return "V2Monitoring session music...";
    }

    public long now() {
      return now;
    }

    public void sleep(long millis) {
      now += millis;
    }
  }

  private static JSONObject run(Fake f, String pkg) throws Exception {
    return MediaSessionControl.execute(f, f, "pause", pkg);
  }

  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    check(
        MediaSessionControl.requestedName(new JSONObject().put("name", JSONObject.NULL)).isEmpty(),
        "nullable default became literal null");
    check(MediaSessionControl.requestedName(new JSONObject()).isEmpty(), "missing name rejected");
    check(
        MediaSessionControl.requestedName(new JSONObject().put("name", "  Music  "))
            .equals("Music"),
        "explicit app name lost");
    final int[] cancelledCommands = {0};
    try {
      MediaSessionControl.execute(
          (command, timeout) -> {
            cancelledCommands[0]++;
            if (command.equals("dumpsys media_session")) {
              Thread.currentThread().interrupt();
              return PLAYING;
            }
            throw new AssertionError("media mutation dispatched after cancelled initial read");
          },
          new Fake(),
          "pause",
          "com.music");
      throw new AssertionError("cancelled read returned normally");
    } catch (InterruptedException | java.util.concurrent.CancellationException expected) {
      check(cancelledCommands[0] == 1, "read cancellation performed mutation");
    } finally {
      Thread.interrupted();
    }
    CancellationToken token = new CancellationToken();
    final int[] tokenCommands = {0};
    try {
      MediaSessionControl.execute(
          (command, timeout) -> {
            tokenCommands[0]++;
            token.cancel();
            return PLAYING;
          },
          new Fake(),
          "pause",
          "com.music",
          token);
      throw new AssertionError("explicit cancellation after read accepted");
    } catch (java.util.concurrent.CancellationException expected) {
      check(tokenCommands[0] == 1, "explicit cancellation dispatched media command");
    }
    Fake f = new Fake();
    f.before +=
        session("Bluetooth", "com.bluetooth", 1002, 7, false)
            + "Audio playback (lastly played comes first)\n uid=10118 packages=assistant\n";
    JSONObject result = run(f, "");
    check(result.getString("status").equals("observed"), "background music not verified");
    check(
        result.getJSONObject("observation").getString("package").equals("com.music"),
        "wrong target");
    check(
        f.commands.get(1).equals("printf '%s\\n' 'pause' 'q' | cmd media_session monitor 'music'"),
        "not targeted");
    check(f.commands.size() == 3, "unnecessary observation work");

    f = new Fake();
    f.after = PLAYING;
    check(run(f, "").getString("status").equals("unconfirmed"), "receipt counted as pause");
    f = new Fake();
    f.after = "Audio playback (lastly played comes first)\n uid=10082 packages=com.music";
    result = run(f, "");
    check(result.getString("status").equals("unconfirmed"), "absence/history counted as pause");
    check(result.getJSONObject("observation").isEmpty(), "stale state reported as fresh");
    f = new Fake();
    f.after = session("music", "com.other", 999, 2, true);
    check(run(f, "").getString("status").equals("unconfirmed"), "another owner counted as pause");
    f = new Fake();
    f.before += session("video", "com.video", 333, 3, true);
    check(
        run(f, "").getString("status").equals("rejected") && !f.sent,
        "ambiguous sessions dispatched");
    check(
        run(f, "com.music").getString("status").equals("observed"), "explicit target not selected");
    f = new Fake();
    f.before += session("music", "com.other", 333, 2, true);
    check(
        run(f, "com.music").getString("status").equals("rejected") && !f.sent,
        "ambiguous monitor tag dispatched");
    f = new Fake();
    f.before +=
        session("music", "com.other", 333, 2, true)
            .replace("state=PlaybackState {state=2, position=0}", "state=null");
    check(
        run(f, "com.music").getString("status").equals("rejected") && !f.sent,
        "tag collision with unreadable state dispatched");
    f = new Fake();
    f.after = session("music", "com.music", 10082, 1, true);
    check(run(f, "").getString("status").equals("unconfirmed"), "stopped counted as paused");
    f = new Fake();
    f.before = "";
    check(
        run(f, "com.music").getString("status").equals("rejected") && !f.sent,
        "missing session dispatched");
    f = new Fake();
    f.before = PAUSED;
    check(
        run(f, "").getString("status").equals("observed") && !f.sent, "already paused dispatched");
    f = new Fake();
    f.before = PAUSED;
    f.after = PLAYING;
    check(
        MediaSessionControl.execute(f, f, "play", "com.music")
            .getString("status")
            .equals("observed"),
        "play failed");
    f = new Fake();
    check(
        MediaSessionControl.execute(f, f, "toggle", "").getString("status").equals("rejected")
            && f.commands.isEmpty(),
        "untyped action");
    check(MediaSessionControl.quote("a'b;$(id)").equals("'a'\\''b;$(id)'"), "unsafe tag quoting");
    System.out.println(
        "Media session control: background target, exact readback, ambiguity and no false"
            + " confirmation passed");
  }
}
