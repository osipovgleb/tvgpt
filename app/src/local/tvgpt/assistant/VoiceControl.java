package local.tvgpt.assistant;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class VoiceControl extends View {
  private final boolean end;
  private boolean muted;
  private final Paint paint = new Paint(3);

  VoiceControl(Context c, boolean end) {
    super(c);
    this.end = end;
    setFocusable(true);
    setClickable(true);
    setContentDescription(end ? "Завершить разговор" : "Выключить микрофон");
  }

  void muted(boolean value) {
    muted = value;
    setContentDescription(value ? "Включить микрофон" : "Выключить микрофон");
    invalidate();
  }

  @Override
  protected void onFocusChanged(boolean gain, int direction, Rect old) {
    super.onFocusChanged(gain, direction, old);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float x = getWidth() / 2f, y = getHeight() / 2f, r = Math.min(x, y) - 3;
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(isFocused() ? 0xffededed : 0xff303030);
    c.drawCircle(x, y, r, paint);
    paint.setColor(isFocused() ? 0xff181818 : 0xfff5f5f5);
    paint.setStrokeWidth(r * .085f);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStyle(Paint.Style.STROKE);
    if (end) {
      float a = r * .27f;
      c.drawLine(x - a, y - a, x + a, y + a, paint);
      c.drawLine(x + a, y - a, x - a, y + a, paint);
    } else {
      c.drawRoundRect(
          x - r * .14f, y - r * .40f, x + r * .14f, y + r * .12f, r * .14f, r * .14f, paint);
      c.drawArc(x - r * .29f, y - r * .20f, x + r * .29f, y + r * .31f, 0, 180, false, paint);
      c.drawLine(x, y + r * .31f, x, y + r * .47f, paint);
      c.drawLine(x - r * .16f, y + r * .47f, x + r * .16f, y + r * .47f, paint);
      if (muted) c.drawLine(x - r * .43f, y - r * .43f, x + r * .43f, y + r * .43f, paint);
    }
  }
}
