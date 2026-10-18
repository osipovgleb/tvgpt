package local.tvgpt.assistant.ui;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import local.tvgpt.assistant.AppDataEvents;

/** One bounded shared worker, never a database read on an audio frame. */
public abstract class AsyncScreen extends LinearLayout {
  protected static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "tvgpt-app-data"));
  protected final Handler main = new Handler(Looper.getMainLooper());
  protected final LinearLayout body;
  protected final TextView status;
  protected boolean disposed;
  private long revision;
  private String observedSection;
  private Runnable dataRefresh;
  private boolean observing;
  private final Runnable reload =
      () -> {
        if (!disposed && dataRefresh != null) {
          android.util.Log.i("TVGPTData", "Refresh section=" + observedSection);
          dataRefresh.run();
        }
      };
  private final BroadcastReceiver changes =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
          if (!disposed
              && observedSection != null
              && observedSection.equals(intent.getStringExtra("section"))) {
            main.removeCallbacks(reload);
            main.postDelayed(reload, 80);
          }
        }
      };

  protected final void observeData(String section, Runnable refresh) {
    observedSection = section;
    dataRefresh = refresh;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    disposed = false;
    if (observedSection != null) {
      IntentFilter filter = new IntentFilter(AppDataEvents.ACTION);
      if (Build.VERSION.SDK_INT >= 33)
        getContext().registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
      else getContext().registerReceiver(changes, filter);
      observing = true;
    }
  }

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
                  if (!disposed && revision == token) {
                    View focus = body.findFocus();
                    Object key = focus == null ? null : focus.getTag();
                    CharSequence label = focus == null ? null : focus.getContentDescription();
                    render.accept(value);
                    if (hasWindowFocus() && focus != null) restoreFocus(body, key, label);
                  }
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

  private boolean restoreFocus(View view, Object key, CharSequence label) {
    boolean matches =
        key instanceof String
            ? key.equals(view.getTag())
            : label != null && label.equals(view.getContentDescription());
    if (matches && view.isFocusable()) return view.requestFocus();
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (restoreFocus(group.getChildAt(i), key, label)) return true;
    }
    return false;
  }

  @Override
  protected void onDetachedFromWindow() {
    disposed = true;
    if (observing) {
      getContext().unregisterReceiver(changes);
      observing = false;
    }
    revision++;
    main.removeCallbacksAndMessages(null);
    super.onDetachedFromWindow();
  }
}
