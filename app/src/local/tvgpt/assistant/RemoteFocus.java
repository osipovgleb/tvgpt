package local.tvgpt.assistant;

import android.os.*;
import android.view.*;
import java.util.concurrent.*;

/** Route injected remote keys to the TV app underneath our assistant window. */
final class RemoteFocus {
  private static Window window;
  private static int generation;
  private static final Handler main = new Handler(Looper.getMainLooper());

  static void attach(Window value) {
    window = value;
  }

  static void detach(Window value) {
    if (window == value) window = null;
  }

  static void begin() throws Exception {
    begin("");
  }

  static void begin(String direction) throws Exception {
    begin(direction, CancellationToken.NONE);
  }

  static void begin(String direction, CancellationToken token) throws Exception {
    java.util.concurrent.Callable<Void> action =
        () -> {
          token.check();
          generation++;
          if (TvAccessibility.current != null) {
            if (direction.equals("home")) TvAccessibility.current.detachVoiceSessionForHome();
            token.check();
            TvAccessibility.current.releaseAppFocus();
          }
          if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
          return null;
        };
    if (Looper.myLooper() == Looper.getMainLooper()) action.call();
    else ActionDispatch.onQueue(main::post, action, token, 2000);
  }

  static void end() {
    main.post(
        () -> {
          int token = generation;
          Window target = window;
          main.postDelayed(
              () -> {
                if (token == generation) {
                  if (TvAccessibility.current != null) TvAccessibility.current.restoreRemoteFocus();
                  if (target != null && target == window)
                    target.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                }
              },
              400);
        });
  }
}
