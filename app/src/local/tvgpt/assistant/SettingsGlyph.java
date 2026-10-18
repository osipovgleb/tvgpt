package local.tvgpt.assistant;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** All icons share a centered 24-unit drawing box; no font glyph baselines. */
public final class SettingsGlyph extends View {
  public static final int ACCOUNT = 0,
      VOICE = 1,
      SPEAKER = 2,
      REMOTE = 3,
      BACK = 4,
      LOGOUT = 5,
      CHEVRON = 6,
      SHIELD = 7,
      CHAT = 8,
      BOOK = 9,
      CLOCK = 10,
      SETTINGS = 11,
      PALETTE = 12,
      PLUS = 13,
      TIMER = 14,
      NEWS = 15,
      REFRESH = 16,
      PLAY = 17,
      DELETE = 18,
      EDIT = 19,
      LINK = 20,
      BELL = 21,
      STOP = 22;
  private final int kind;
  private final Paint p = new Paint(3);

  public SettingsGlyph(Context c, int kind) {
    super(c);
    this.kind = kind;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float scale = Math.min(getWidth(), getHeight()) / 24f;
    c.save();
    c.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
    c.scale(scale, scale);
    p.setColor(0xffdedede);
    p.setStrokeWidth(1.7f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    drawIcon(c);
    c.restore();
  }

  private void drawIcon(Canvas c) {
    if (kind >= PLUS) {
      drawActionIcon(c);
      return;
    }
    if (kind >= CHAT) {
      drawMenuIcon(c);
      return;
    }
    Path path = new Path();
    switch (kind) {
      case ACCOUNT:
        c.drawCircle(12, 8, 3.4f, p);
        c.drawArc(4, 12, 20, 25, 195, 150, false, p);
        break;
      case VOICE:
        c.drawRoundRect(9, 3, 15, 14, 3, 3, p);
        c.drawArc(6, 7, 18, 18, 0, 180, false, p);
        c.drawLine(12, 18, 12, 22, p);
        c.drawLine(9, 22, 15, 22, p);
        break;
      case SPEAKER:
        path.moveTo(4, 9);
        path.lineTo(8, 9);
        path.lineTo(13, 5);
        path.lineTo(13, 19);
        path.lineTo(8, 15);
        path.lineTo(4, 15);
        path.close();
        c.drawPath(path, p);
        c.drawArc(12, 6, 22, 18, -65, 130, false, p);
        break;
      case REMOTE:
        c.drawRoundRect(7, 2, 17, 22, 3, 3, p);
        c.drawCircle(12, 7, 1, p);
        c.drawCircle(12, 14, 3, p);
        break;
      case BACK:
        path.moveTo(15, 5);
        path.lineTo(8, 12);
        path.lineTo(15, 19);
        c.drawPath(path, p);
        break;
      case LOGOUT:
        path.moveTo(10, 4);
        path.lineTo(4, 4);
        path.lineTo(4, 20);
        path.lineTo(10, 20);
        c.drawPath(path, p);
        c.drawLine(9, 12, 21, 12, p);
        path.reset();
        path.moveTo(17, 8);
        path.lineTo(21, 12);
        path.lineTo(17, 16);
        c.drawPath(path, p);
        break;
      case CHEVRON:
        path.moveTo(9, 7);
        path.lineTo(14, 12);
        path.lineTo(9, 17);
        c.drawPath(path, p);
        break;
      case SHIELD:
        path.moveTo(12, 2);
        path.lineTo(20, 6);
        path.lineTo(19, 15);
        path.quadTo(16, 20, 12, 22);
        path.quadTo(8, 20, 5, 15);
        path.lineTo(4, 6);
        path.close();
        c.drawPath(path, p);
        break;
    }
  }

  private void drawMenuIcon(Canvas c) {
    switch (kind) {
      case CHAT:
        c.drawRoundRect(3, 4, 21, 18, 4, 4, p);
        c.drawLine(7, 18, 5, 22, p);
        c.drawLine(5, 22, 12, 18, p);
        break;
      case BOOK:
        c.drawRoundRect(4, 3, 20, 22, 2, 2, p);
        c.drawLine(8, 3, 8, 22, p);
        c.drawLine(12, 8, 17, 8, p);
        c.drawLine(12, 12, 17, 12, p);
        break;
      case CLOCK:
        c.drawCircle(12, 12, 9, p);
        c.drawLine(12, 6, 12, 12, p);
        c.drawLine(12, 12, 16, 14, p);
        break;
      case SETTINGS:
        c.drawCircle(12, 12, 7, p);
        c.drawCircle(12, 12, 2.5f, p);
        for (int n = 0; n < 8; n++) {
          c.save();
          c.rotate(n * 45, 12, 12);
          c.drawLine(12, 2, 12, 5, p);
          c.restore();
        }
        break;
      case PALETTE:
        c.drawCircle(12, 12, 9, p);
        c.drawCircle(8, 9, 1, p);
        c.drawCircle(13, 7, 1, p);
        c.drawCircle(17, 11, 1, p);
        c.drawCircle(8, 15, 1, p);
        break;
    }
  }

  private void drawActionIcon(Canvas c) {
    Path path = new Path();
    switch (kind) {
      case PLUS:
        c.drawLine(12, 5, 12, 19, p);
        c.drawLine(5, 12, 19, 12, p);
        break;
      case TIMER:
        c.drawCircle(12, 14, 7, p);
        c.drawLine(9, 2, 15, 2, p);
        c.drawLine(12, 2, 12, 5, p);
        c.drawLine(17, 7, 19, 5, p);
        c.drawLine(12, 10, 12, 14, p);
        break;
      case NEWS:
        c.drawRoundRect(3, 4, 21, 20, 2, 2, p);
        c.drawRect(6, 8, 11, 13, p);
        c.drawLine(14, 8, 18, 8, p);
        c.drawLine(14, 12, 18, 12, p);
        c.drawLine(6, 16, 18, 16, p);
        break;
      case REFRESH:
        c.drawArc(4, 4, 20, 20, 35, 290, false, p);
        c.drawLine(19, 3, 19, 8, p);
        c.drawLine(14, 8, 19, 8, p);
        break;
      case PLAY:
        path.moveTo(8, 4);
        path.lineTo(20, 12);
        path.lineTo(8, 20);
        path.close();
        c.drawPath(path, p);
        break;
      default:
        drawManagementIcon(c);
        break;
    }
  }

  private void drawManagementIcon(Canvas c) {
    Path path = new Path();
    switch (kind) {
      case DELETE:
        c.drawLine(4, 6, 20, 6, p);
        c.drawLine(9, 3, 15, 3, p);
        path.moveTo(6, 6);
        path.lineTo(7, 21);
        path.lineTo(17, 21);
        path.lineTo(18, 6);
        c.drawPath(path, p);
        c.drawLine(10, 10, 10, 17, p);
        c.drawLine(14, 10, 14, 17, p);
        break;
      case EDIT:
        path.moveTo(4, 15);
        path.lineTo(15, 4);
        path.lineTo(20, 9);
        path.lineTo(9, 20);
        path.lineTo(4, 21);
        path.close();
        c.drawPath(path, p);
        c.drawLine(13, 6, 18, 11, p);
        break;
      case LINK:
        c.save();
        c.rotate(-40, 12, 12);
        c.drawRoundRect(2, 8, 13, 16, 4, 4, p);
        c.drawRoundRect(11, 8, 22, 16, 4, 4, p);
        c.restore();
        break;
      case BELL:
        path.moveTo(4, 17);
        path.lineTo(6, 14);
        path.lineTo(6, 9);
        path.cubicTo(6, 1, 18, 1, 18, 9);
        path.lineTo(18, 14);
        path.lineTo(20, 17);
        path.close();
        c.drawPath(path, p);
        c.drawArc(9, 17, 15, 22, 0, 180, false, p);
        break;
      case STOP:
        c.drawRoundRect(5, 5, 19, 19, 3, 3, p);
        break;
    }
  }
}
