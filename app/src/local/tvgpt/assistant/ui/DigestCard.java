package local.tvgpt.assistant.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.Closeable;
import local.tvgpt.assistant.digests.DigestImages;
import org.json.*;

/** Native text is readable regardless of whether a cover exists. */
public final class DigestCard extends FrameLayout {
  private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
  private final ImageView image;
  private Bitmap bitmap;
  private Closeable pin;
  private String bound = "";
  private int generation;

  public DigestCard(Context c, JSONObject article) {
    super(c);
    setMinimumHeight(Screens.dp(c, 220));
    setBackground(Screens.rounded(c, 0xff1c2330, 14));
    setClipToOutline(true);
    image = new ImageView(c);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    addView(image, new LayoutParams(-1, -1));
    View shade = new View(c);
    shade.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00101015, 0xe9101015}));
    addView(shade, new LayoutParams(-1, -1));
    LinearLayout caption = new LinearLayout(c);
    caption.setOrientation(1);
    caption.setPadding(Screens.dp(c, 20), Screens.dp(c, 32), Screens.dp(c, 20), Screens.dp(c, 16));
    TextView title = Screens.text(c, article.optString("title"), 21);
    title.setMaxLines(3);
    caption.addView(title);
    String source = "";
    JSONArray sources = article.optJSONArray("sources");
    if (sources != null && sources.length() > 0)
      source = sources.optJSONObject(0).optString("title");
    long date = article.optLong("published_at", 0);
    String time =
        date > 0
            ? java.time.Instant.ofEpochMilli(date)
                .atZone(new local.tvgpt.assistant.settings.GeneralPreferences(c).zone())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
            : "Дата публикации не указана";
    caption.addView(
        Screens.text(
            c,
            source
                + " · "
                + time
                + (article.optString("image_kind").equals("generated") ? " · Иллюстрация ИИ" : ""),
            12));
    addView(caption, new LayoutParams(-1, -2, Gravity.BOTTOM));
    setFocusable(true);
    setForeground(focusForeground(c));
    setContentDescription(article.optString("title"));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Screens.dp(c, 240));
    lp.topMargin = Screens.dp(c, 12);
    setLayoutParams(lp);
    bindImage(article.optString("image_path"));
  }

  private android.graphics.drawable.Drawable focusForeground(Context c) {
    android.graphics.drawable.StateListDrawable s =
        new android.graphics.drawable.StateListDrawable();
    GradientDrawable border = Screens.rounded(c, 0x00000000, 14);
    border.setStroke(Screens.dp(c, 2), 0xffa9caff);
    s.addState(new int[] {android.R.attr.state_focused}, border);
    s.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
    return s;
  }

  public void bindImage(String path) {
    bound = path;
    if (isAttachedToWindow()) load();
  }

  private void load() {
    final int token = ++generation;
    final String path = bound;
    if (path.isEmpty()) return;
    AsyncScreen.IO.submit(
        () -> {
          Closeable held = DigestImages.acquire(path);
          Bitmap loaded = DigestImages.decode(path, 640, 360);
          main.post(
              () -> {
                if (token != generation || !isAttachedToWindow()) {
                  if (loaded != null) loaded.recycle();
                  try {
                    held.close();
                  } catch (Exception ignored) {
                  }
                  return;
                }
                release();
                pin = held;
                bitmap = loaded;
                image.setImageBitmap(bitmap);
              });
        });
  }

  private void release() {
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

  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    load();
  }

  protected void onDetachedFromWindow() {
    generation++;
    release();
    super.onDetachedFromWindow();
  }
}
