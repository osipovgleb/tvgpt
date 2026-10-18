package local.tvgpt.assistant.digests;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.Closeable;
import java.util.concurrent.*;
import local.tvgpt.assistant.data.DigestRepository;
import local.tvgpt.assistant.ui.Screens;
import org.json.*;

/** Slideshow reads local data only. Detach releases bitmap, pins and timer. */
public final class DigestSlideshow extends FrameLayout {
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService loader = Executors.newSingleThreadExecutor();
  private final ImageView image;
  private final LinearLayout caption;
  private final TextView headline, summary, attribution;
  private final String digestId;
  private Bitmap bitmap, pendingBitmap;
  private Closeable pin, pendingPin;
  private boolean active;
  private int index = -1, generation;
  private final Runnable advance = () -> next();

  public DigestSlideshow(Context c, String id) {
    super(c);
    digestId = id == null ? "" : id;
    setBackgroundColor(0xff10131a);
    image = new ImageView(c);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    addView(image, new LayoutParams(-1, -1));
    View shade = new View(c);
    shade.setBackground(
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0x00101015, 0xe9101015}));
    addView(shade, new LayoutParams(-1, -1));
    caption = new LinearLayout(c);
    caption.setOrientation(LinearLayout.VERTICAL);
    caption.setPadding(Screens.dp(c, 48), Screens.dp(c, 24), Screens.dp(c, 48), Screens.dp(c, 34));
    headline = Screens.text(c, "", 28);
    headline.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
    headline.setMaxLines(2);
    headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
    summary = Screens.text(c, "", 16);
    summary.setMaxLines(2);
    summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
    attribution = Screens.text(c, "", 11);
    attribution.setTextColor(0xffc4cbd4);
    attribution.setMaxLines(1);
    caption.addView(headline);
    caption.addView(summary);
    caption.addView(attribution);
    addView(caption, new LayoutParams(-1, -2, Gravity.BOTTOM));
  }

  public void start() {
    if (active) return;
    active = true;
    next();
  }

  public void stop() {
    active = false;
    generation++;
    main.removeCallbacksAndMessages(null);
    image.animate().cancel();
    caption.animate().cancel();
    clearPending();
    clear();
  }

  public void release() {
    stop();
    loader.shutdownNow();
  }

  private void clearPending() {
    if (pendingBitmap != null) {
      pendingBitmap.recycle();
      pendingBitmap = null;
    }
    if (pendingPin != null)
      try {
        pendingPin.close();
      } catch (Exception ignored) {
      }
    pendingPin = null;
  }

  private void clear() {
    image.setImageDrawable(null);
    if (bitmap != null) {
      bitmap.recycle();
      bitmap = null;
    }
    if (pin != null)
      try {
        pin.close();
      } catch (Exception ignored) {
      }
    pin = null;
  }

  private void next() {
    if (!active || loader.isShutdown()) return;
    final int token = ++generation;
    loader.submit(
        () -> {
          JSONObject article = null;
          Bitmap loaded = null;
          Closeable held = null;
          try {
            DigestRepository repo = new DigestRepository(getContext());
            JSONArray cards = repo.cards(digestId);
            JSONArray selected = DigestSlides.select(cards, System.currentTimeMillis());
            index = DigestSlides.nextIndex(index, selected.length());
            if (index >= 0) {
              article = selected.getJSONObject(index);
              String path = article.optString("image_path");
              if (!path.isEmpty()) {
                held = DigestImages.acquire(path);
                loaded = DigestImages.decode(path);
              }
            }
          } catch (Exception ignored) {
          }
          final JSONObject result = article;
          final Bitmap nextBitmap = loaded;
          final Closeable nextPin = held;
          main.post(
              () -> {
                if (!active || token != generation) {
                  if (nextBitmap != null) nextBitmap.recycle();
                  if (nextPin != null)
                    try {
                      nextPin.close();
                    } catch (Exception ignored) {
                    }
                  return;
                }
                clearPending();
                pendingBitmap = nextBitmap;
                pendingPin = nextPin;
                image
                    .animate()
                    .alpha(0)
                    .setDuration(350)
                    .withEndAction(
                        () -> {
                          if (!active || token != generation) return;
                          clear();
                          bitmap = pendingBitmap;
                          pin = pendingPin;
                          pendingBitmap = null;
                          pendingPin = null;
                          image.setImageBitmap(bitmap);
                          headline.setText(
                              result == null
                                  ? "Свежих новостей пока нет"
                                  : result.optString("title"));
                          summary.setText(result == null ? "" : result.optString("summary"));
                          attribution.setText(result == null ? "" : text(result));
                          caption.setTranslationX(index % 2 == 0 ? 0 : Screens.dp(getContext(), 8));
                          image.animate().alpha(1).setDuration(350).start();
                        })
                    .start();
                main.postDelayed(advance, 20000);
              });
        });
  }

  private String text(JSONObject a) {
    if (a == null) return "Свежих новостей пока нет";
    String source = "";
    JSONArray sources = a.optJSONArray("sources");
    if (sources != null && sources.length() > 0)
      source = sources.optJSONObject(0).optString("title");
    long published = a.optLong("published_at", 0);
    String date =
        published > 0
            ? java.time.Instant.ofEpochMilli(published)
                .atZone(new local.tvgpt.assistant.settings.GeneralPreferences(getContext()).zone())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
            : "Дата не указана";
    return source
        + " · "
        + date
        + (a.optString("image_kind").equals("generated") ? " · Иллюстрация ИИ" : "");
  }

  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    start();
  }

  protected void onDetachedFromWindow() {
    release();
    super.onDetachedFromWindow();
  }

  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility == VISIBLE && isAttachedToWindow()) start();
    else stop();
  }
}
