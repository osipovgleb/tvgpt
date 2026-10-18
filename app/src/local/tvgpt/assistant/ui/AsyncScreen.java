package local.tvgpt.assistant.ui;

import android.content.Context;
import android.os.*;
import android.widget.*;
import java.util.concurrent.*;

/** One bounded shared worker, never a database read on an audio frame. */
public abstract class AsyncScreen extends LinearLayout {
  protected static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "tvgpt-app-data"));
  protected final Handler main = new Handler(Looper.getMainLooper());
  protected final LinearLayout body;
  protected final TextView status;
  protected boolean disposed;
  private long revision;

  public AsyncScreen(Context c, String title) {
    super(c);
    setOrientation(1);
    setPadding(Screens.dp(c, 25), Screens.dp(c, 16), Screens.dp(c, 25), Screens.dp(c, 16));
    status = Screens.text(c, "", 13);
    status.setVisibility(GONE);
    addView(status);
    body = new LinearLayout(c);
    body.setOrientation(1);
    addView(Screens.scroll(c, body), new LayoutParams(-1, 0, 1));
  }

  protected <T> void load(Callable<T> task, java.util.function.Consumer<T> render) {
    long token = ++revision;
    IO.submit(
        () -> {
          try {
            T value = task.call();
            main.post(
                () -> {
                  if (!disposed && revision == token) render.accept(value);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (!disposed && revision == token) {
                    status.setVisibility(VISIBLE);
                    status.setText(
                        e.getMessage() == null ? "Не удалось выполнить действие" : e.getMessage());
                  }
                });
          }
        });
  }

  @Override
  protected void onDetachedFromWindow() {
    disposed = true;
    revision++;
    main.removeCallbacksAndMessages(null);
    super.onDetachedFromWindow();
  }
}
