package local.tvgpt.assistant;

import android.content.Context;
import java.lang.reflect.*;
import java.util.concurrent.CancellationException;
import org.json.JSONObject;

public final class TvRemoteFallbackTest {
  private static final class Screen extends TvAccessibility {
    int actions;

    @Override
    boolean navigate(String direction, CancellationToken token) {
      return navigate(direction);
    }

    @Override
    boolean navigate(String direction) {
      actions++;
      return true;
    }
  }

  public static void main(String[] args) throws Exception {
    Screen screen = new Screen();
    TvAccessibility.current = screen;
    Context context = new Context();
    TvTools tools = new TvTools(context);
    Method raw = TvTools.class.getDeclaredMethod("executeRaw", String.class, JSONObject.class);
    raw.setAccessible(true);
    JSONObject key = new JSONObject().put("direction", "back");
    AdbRemote.phase = 1;
    raw.invoke(tools, "navigate", key);
    if (screen.actions != 0) throw new AssertionError("post-dispatch ADB failure repeated BACK");
    AdbRemote.phase = 0;
    raw.invoke(tools, "navigate", key);
    if (screen.actions != 1) throw new AssertionError("pre-dispatch safe fallback lost");
    AdbRemote.phase = 2;
    try {
      raw.invoke(tools, "navigate", key);
      throw new AssertionError("cancellation swallowed");
    } catch (InvocationTargetException failure) {
      if (!(failure.getCause() instanceof CancellationException)) throw failure;
    }
    if (screen.actions != 1) throw new AssertionError("cancelled ADB fell back to accessibility");
    AdbRemote.phase = 3;
    JSONObject media =
        new JSONObject(tools.execute("media_control", new JSONObject().put("action", "pause")));
    if (!media.optString("status").equals("unconfirmed")
        || !media.optString("dispatch_status").equals("uncertain"))
      throw new AssertionError("post-dispatch media error lost uncertainty: " + media);
    for (int phase : new int[] {4, 5}) {
      AdbRemote.phase = phase;
      int calls = AdbRemote.volumeCalls;
      JSONObject volume =
          new JSONObject(tools.execute("volume", new JSONObject().put("direction", "up")));
      if (context.audio.mutations != 0 || AdbRemote.volumeCalls != calls + 1)
        throw new AssertionError("External volume also changed Android stream or retried");
      if (!volume.optString("status").equals(phase == 4 ? "observed" : "unconfirmed"))
        throw new AssertionError("External result evidence lost");
    }
    System.out.println("TvRemoteFallbackTest passed");
  }
}
