package android.graphics;

/** Cache tests never decode an image. Real image checks stay Android-only. */
public final class BitmapFactory {
  public static class Options {
    public boolean inJustDecodeBounds;
    public int outWidth, outHeight, inSampleSize;
  }

  public static Bitmap decodeFile(String path, Options options) {
    throw new AssertionError("Unexpected host image decoding");
  }
}
