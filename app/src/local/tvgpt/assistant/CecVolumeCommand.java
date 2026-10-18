package local.tvgpt.assistant;

import java.io.*;
import java.lang.reflect.*;
import java.util.List;
import org.json.JSONObject;

/**
 * Short-lived, authenticated shell helper. No exported service, capture, or vendor device names.
 */
public final class CecVolumeCommand {
  public static void main(String[] args) throws Exception {
    boolean dispatched = false;
    try {
      if (android.os.Process.myUid() != 2000) throw new SecurityException("Shell UID required");
      if (args.length != 1
          || !(args[0].equals("up") || args[0].equals("down") || args[0].equals("status")))
        throw new IllegalArgumentException("Unsupported volume command");
      Class<?> api = Class.forName("android.hardware.hdmi.IHdmiControlService");
      Object binder =
          Class.forName("android.os.ServiceManager")
              .getMethod("getService", String.class)
              .invoke(null, "hdmi_control");
      if (binder == null) {
        System.out.println(new JSONObject().put("route", "android"));
        return;
      }
      Object service =
          Class.forName("android.hardware.hdmi.IHdmiControlService$Stub")
              .getMethod("asInterface", Class.forName("android.os.IBinder"))
              .invoke(null, binder);
      if (!(Boolean) api.getMethod("getSystemAudioMode").invoke(service)) {
        System.out.println(new JSONObject().put("route", "android"));
        return;
      }
      boolean available = false;
      Class<?> info = Class.forName("android.hardware.hdmi.HdmiDeviceInfo");
      for (Object device : (List<?>) api.getMethod("getDeviceList").invoke(service))
        if ((Integer) info.getMethod("getLogicalAddress").invoke(device) == 5) available = true;
      if (!available) throw new IllegalStateException("Active CEC audio system unavailable");
      CecAudioStatus before = CecAudioStatus.parse(dump());
      if (args[0].equals("status")) {
        System.out.println(result(before, false, false));
        return;
      }
      // Relative system-audio press/release: does not change the TV stream or the audio route.
      // Use a non-boundary reference: old==new at the TV's max would suppress a real AVR step.
      Method adjust = api.getMethod("setSystemAudioVolume", int.class, int.class, int.class);
      dispatched = true;
      adjust.invoke(service, 50, args[0].equals("up") ? 51 : 49, 100);
      long deadline = android.os.SystemClock.elapsedRealtime() + 2200;
      CecAudioStatus latest = before;
      do {
        Thread.sleep(200);
        latest = CecAudioStatus.parse(dump());
        if (latest.confirms(before, args[0])) break;
      } while (android.os.SystemClock.elapsedRealtime() < deadline);
      System.out.println(result(latest, true, latest.confirms(before, args[0])));
    } catch (Throwable failure) {
      Throwable cause = failure instanceof InvocationTargetException ? failure.getCause() : failure;
      System.out.println(
          new JSONObject()
              .put("route", "unknown")
              .put("status", "unconfirmed")
              .put("dispatched", dispatched)
              .put("error", cause.getClass().getSimpleName()));
    }
  }

  private static JSONObject result(CecAudioStatus status, boolean dispatched, boolean confirmed)
      throws Exception {
    return ActionObservation.result(
            dispatched ? "Команда громкости аудиосистеме отправлена" : "Состояние аудиосистемы",
            new JSONObject().put("volume_percent", status.percent).put("muted", status.muted),
            confirmed ? "cec_audio_volume_readback" : "")
        .put("route", "hdmi_system_audio")
        .put("dispatched", dispatched)
        .put("requires_visual", false);
  }

  private static String dump() throws Exception {
    Process process =
        new ProcessBuilder("/system/bin/dumpsys", "-t", "1", "hdmi_control")
            .redirectErrorStream(true)
            .start();
    try (InputStream in = process.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int size;
      while ((size = in.read(buffer)) != -1) {
        if (out.size() + size > 262144) throw new IOException("CEC dump exceeds limit");
        out.write(buffer, 0, size);
      }
      if (process.waitFor() != 0) throw new IOException("CEC observation unavailable");
      return out.toString("UTF-8");
    } finally {
      process.destroy();
    }
  }
}
