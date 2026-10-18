package local.tvgpt.assistant;

import android.content.Context;
import java.io.IOException;

final class AdbRemote {
  static int phase;
  static int volumeCalls;

  static final class DispatchException extends IOException {}

  static String key(Context context, String direction) throws Exception {
    if (phase == 1) throw new DispatchException();
    if (phase == 2) throw new java.util.concurrent.CancellationException();
    throw new IOException("before OPEN");
  }

  static String key(Context context, String direction, CancellationToken token) throws Exception {
    return key(context, direction);
  }

  static String shell(Context context, String command, int timeout) {
    return "";
  }

  static String shell(Context context, String command, int timeout, CancellationToken token)
      throws Exception {
    if (phase == 4 || phase == 5) {
      volumeCalls++;
      if (phase == 5) throw new DispatchException();
      return "{\"route\":\"hdmi_system_audio\",\"status\":\"observed\","
          + "\"confirmed_scope\":\"cec_audio_volume_readback\"}";
    }
    if (phase == 3) {
      if (command.equals("dumpsys media_session"))
        return "    music com.music/music (userId=0)\n"
            + "      active=true\n      ownerPid=42, ownerUid=10082, userId=0\n"
            + "      state=PlaybackState {state=3, position=0}\n";
      throw new DispatchException();
    }
    return "";
  }
}
