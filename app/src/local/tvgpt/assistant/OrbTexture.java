package local.tvgpt.assistant;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** One 256 KiB texture shared by the popup and full-screen voice UI. */
final class OrbTexture {
  private static Bitmap texture;
  private static Bitmap clouds;

  static synchronized Bitmap clouds() {
    if (clouds != null) return clouds;
    clouds = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(clouds);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    for (int i = 0; i < 4; i++) {
      float x = 40 + i * 14, y = 82 + (float) Math.sin(i * 1.7) * 19;
      p.setShader(
          new RadialGradient(
              x,
              y,
              42,
              new int[] {0xeefaffff, 0x99eef8ff, 0x00e5f5ff},
              new float[] {0, .38f, 1},
              Shader.TileMode.CLAMP));
      c.drawCircle(x, y, 42, p);
    }
    return clouds;
  }

  static synchronized Bitmap get() {
    if (texture != null) return texture;
    int size = 256;
    Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float x = size / 2f, y = x, radius = x - 2;
    paint.setShader(
        new LinearGradient(
            0,
            0,
            size,
            size,
            new int[] {0xff839dfd, 0xff3478fc, 0xff3e86fb},
            null,
            Shader.TileMode.CLAMP));
    canvas.drawCircle(x, y, radius, paint);
    canvas.save();
    android.graphics.Path clip = new android.graphics.Path();
    clip.addCircle(x, y, radius, android.graphics.Path.Direction.CW);
    canvas.clipPath(clip);
    for (int i = 0; i < 5; i++) {
      double phase = i * 1.23;
      float cloudX = x - radius * .48f + (float) Math.sin(phase) * radius * .33f;
      float cloudY = y + radius * (.51f + (float) Math.cos(phase * .8 + i) * .16f);
      float cloudRadius = radius * (.64f + (float) Math.sin(phase + i) * .10f);
      paint.setShader(
          new RadialGradient(
              cloudX,
              cloudY,
              cloudRadius,
              new int[] {0xeef4faff, 0xcce2f0ff, 0x00d9eeff},
              new float[] {0, .37f, 1},
              Shader.TileMode.CLAMP));
      canvas.drawCircle(cloudX, cloudY, cloudRadius, paint);
    }
    canvas.restore();
    texture = image;
    return texture;
  }
}
