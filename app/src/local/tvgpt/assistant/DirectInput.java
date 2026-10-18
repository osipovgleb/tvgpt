package local.tvgpt.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class DirectInput {
  private static final String IME = "local.tvgpt.assistant/.DirectInputIme";

  static String enter(Context context, String text, boolean submit) throws Exception {
    return enter(context, text, submit, null, false);
  }

  static synchronized String enter(
      Context context, String text, boolean submit, String searchPackage, boolean searchOpened)
      throws Exception {
    if (Looper.myLooper() == Looper.getMainLooper())
      return "Прямой ввод должен выполняться в фоновом потоке";
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    if (text.isEmpty() || text.length() > 4096 || text.indexOf('\u0000') >= 0)
      return "Нужен непустой текст до 4096 символов без NUL";
    java.util.concurrent.atomic.AtomicReference<android.view.accessibility.AccessibilityNodeInfo>
        expected = new java.util.concurrent.atomic.AtomicReference<>();
    try {
      String[] first = accessibleText(text, submit, searchPackage, searchOpened, expected);
      if (first[1] != null) return first[1];
      if (first[0].isEmpty() || first[0].equals(context.getPackageName()))
        return "Не удалось определить приложение для ввода. Открой поле поиска";
      String previous = AdbRemote.shell(context, "settings get secure default_input_method").trim();
      if (!previous.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))
        return "Не удалось сохранить текущую клавиатуру; ввод отменён";
      boolean wasEnabled = isEnabled(context);
      DirectInputRequest request =
          new DirectInputRequest(
              text,
              first[0],
              submit,
              SystemClock.elapsedRealtime() + 15000,
              () -> {
                if (searchPackage == null) return true;
                TvAccessibility access = TvAccessibility.current;
                return access != null && access.sameFocusedEditor(searchPackage, expected.get());
              });
      String result = null;
      String restoreFailure = "";
      try {
        AdbRemote.shell(context, "ime enable " + IME);
        AdbRemote.shell(context, "ime set " + IME);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        DirectInputIme.pending = request;
        new Handler(Looper.getMainLooper())
            .post(
                () -> {
                  DirectInputIme service = DirectInputIme.current;
                  if (service != null) service.deliverPending();
                });
        synchronized (request) {
          long deadline = SystemClock.elapsedRealtime() + 3000;
          while (request.result() == null && SystemClock.elapsedRealtime() < deadline) {
            request.wait(Math.max(1, deadline - SystemClock.elapsedRealtime()));
          }
          result = request.result();
        }
      } finally {
        request.cancel();
        DirectInputIme.pending = null;
        try {
          AdbRemote.shell(context, "ime set '" + previous + "'");
        } catch (Exception e) {
          restoreFailure =
              " Не удалось восстановить предыдущую клавиатуру; выбери её в настройках.";
        }
        if (!wasEnabled
            && !previous.equals(IME)
            && !previous.equals("local.tvgpt.assistant/local.tvgpt.assistant.DirectInputIme")) {
          try {
            AdbRemote.shell(context, "ime disable " + IME);
          } catch (Exception e) {
            restoreFailure += " Временная клавиатура осталась включена.";
          }
        }
      }
      return (result == null
              ? "Прямой ввод не подтверждён: приложение не предоставило активный текстовый"
                  + " редактор. Открой поле поиска"
              : result)
          + restoreFailure;
    } finally {
      new Handler(Looper.getMainLooper())
          .post(
              () -> {
                android.view.accessibility.AccessibilityNodeInfo snapshot =
                    expected.getAndSet(null);
                if (snapshot != null) snapshot.recycle();
              });
    }
  }

  private static String[] accessibleText(
      String text,
      boolean submit,
      String searchPackage,
      boolean searchOpened,
      java.util.concurrent.atomic.AtomicReference<android.view.accessibility.AccessibilityNodeInfo>
          expected)
      throws Exception {
    FutureTask<String[]> accessible =
        new FutureTask<>(
            () -> {
              TvAccessibility access = TvAccessibility.current;
              if (access == null) return new String[] {"", null};
              String target = access.currentAppPackage();
              if (searchPackage != null) {
                if (!searchPackage.equals(target))
                  return new String[] {
                    target, "Приложение для поиска потеряло фокус; ввод отменён"
                  };
                expected.set(access.captureSearchEditor(searchPackage, searchOpened));
                if (expected.get() == null)
                  return new String[] {target, "Поле поиска потеряло фокус; ввод отменён"};
              }
              return new String[] {target, access.setFocusedText(text, submit)};
            });
    new Handler(Looper.getMainLooper()).post(accessible);
    String[] first;
    try {
      first = accessible.get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      accessible.cancel(false);
      throw e;
    }
    return first;
  }

  private static boolean isEnabled(Context context) throws Exception {
    String enabled = AdbRemote.shell(context, "settings get secure enabled_input_methods").trim();
    boolean wasEnabled = false;
    for (String entry : enabled.split(":")) {
      if (entry.split(";", 2)[0].equals(IME)
          || entry.split(";", 2)[0].equals(
              "local.tvgpt.assistant/local.tvgpt.assistant.DirectInputIme")) wasEnabled = true;
    }
    return wasEnabled;
  }
}
