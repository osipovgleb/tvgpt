package local.tvgpt.assistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

public final class VoiceOrb extends View {
  private static final ColorMatrixColorFilter[] THEMES = new ColorMatrixColorFilter[12];
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF bounds = new RectF();
  private final android.graphics.Path clip = new android.graphics.Path();
  private final Bitmap clouds = OrbTexture.clouds();
  private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final OrbMotion motion = new OrbMotion();
  private final Bitmap texture = OrbTexture.get();
  private final Runnable frame = this::invalidate;
  private final ColorMatrixColorFilter mutedFilter;
  private ColorMatrixColorFilter themeFilter;
  private float target;
  private boolean muted;
  private boolean visible;
  private boolean closing;
  private boolean busy;
  private boolean connecting = true;
  private long phaseStart = SystemClock.uptimeMillis();

  public VoiceOrb(Context context) {
    super(context);
    theme(new local.tvgpt.assistant.settings.AssistantPreferences(context).theme());
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    ColorMatrix colors = new ColorMatrix();
    colors.setSaturation(.15f);
    mutedFilter = new ColorMatrixColorFilter(colors);
  }

  public void theme(String id) {
    // Map original blue to the selected tint while retaining neutral cloud whites.
    themeFilter = filter(id);
    invalidate();
  }

  private static synchronized ColorMatrixColorFilter filter(String id) {
    if ("sky".equals(id)) return null;
    int index = local.tvgpt.assistant.settings.OrbPalette.index(id);
    if (THEMES[index] == null)
      THEMES[index] =
          new ColorMatrixColorFilter(local.tvgpt.assistant.settings.OrbPalette.matrix(id));
    return THEMES[index];
  }

  public void state(boolean connecting, boolean closing) {
    this.connecting = connecting;
    this.closing = closing;
    phaseStart = SystemClock.uptimeMillis();
    invalidate();
  }

  void heard() {
    motion.heard(SystemClock.uptimeMillis());
    invalidate();
  }

  void busy(boolean value) {
    busy = value;
    invalidate();
  }

  void level(float value) {
    target = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
  }

  void muted(boolean value) {
    muted = value;
    invalidate();
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    float radius = Math.min(width, height) * .46f;
    bounds.set(
        width / 2f - radius, height / 2f - radius, width / 2f + radius, height / 2f + radius);
    clip.reset();
    clip.addCircle(width / 2f, height / 2f, radius, android.graphics.Path.Direction.CW);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    motion.step(SystemClock.uptimeMillis(), target, muted || closing, busy);
    target *= .96f;
    paint.setColorFilter(muted || closing ? mutedFilter : themeFilter);
    canvas.save();
    canvas.rotate(motion.rotation, getWidth() / 2f, getHeight() / 2f);
    canvas.scale(motion.scale, motion.scale, getWidth() / 2f, getHeight() / 2f);
    canvas.drawBitmap(texture, null, bounds, paint);
    canvas.restore();
    canvas.save();
    canvas.scale(motion.scale * .98f, motion.scale * .98f, getWidth() / 2f, getHeight() / 2f);
    canvas.clipPath(clip);
    canvas.rotate(motion.cloudRotation, getWidth() / 2f, getHeight() / 2f);
    canvas.translate(bounds.width() * motion.cloudX, bounds.height() * motion.cloudY);
    cloudPaint.setColorFilter(muted || closing ? mutedFilter : themeFilter);
    cloudPaint.setAlpha(muted || closing ? 90 : 175);
    canvas.drawBitmap(clouds, null, bounds, cloudPaint);
    canvas.restore();
    if (connecting || closing) {
      paint.setColorFilter(null);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(getWidth() * .018f);
      paint.setColor(closing ? 0xffcccccc : 0xffa7c8ff);
      float phase = (SystemClock.uptimeMillis() - phaseStart) * .25f;
      canvas.drawArc(bounds, phase, closing ? 100 : 65, false, paint);
      paint.setStyle(Paint.Style.FILL);
    }
    if (visible && isAttachedToWindow()) {
      removeCallbacks(frame);
      postDelayed(frame, 40);
    }
  }

  @Override
  public void onVisibilityAggregated(boolean isVisible) {
    super.onVisibilityAggregated(isVisible);
    visible = isVisible;
    if (isVisible) invalidate();
    else removeCallbacks(frame);
  }

  @Override
  protected void onDetachedFromWindow() {
    visible = false;
    removeCallbacks(frame);
    super.onDetachedFromWindow();
  }
}
