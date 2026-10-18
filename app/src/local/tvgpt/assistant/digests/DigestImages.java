package local.tvgpt.assistant.digests;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.*;

/** Bounded private image cache with pins shared by gallery and dream. */
public final class DigestImages {
  private static final Map<String, Integer> pins = new HashMap<>();
  private final File directory;
  private final Context context;

  public DigestImages(Context c) {
    context = c.getApplicationContext();
    directory = new File(c.getFilesDir(), "digest-images");
    directory.mkdirs();
  }

  public File file(String key) {
    if (!key.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Image key");
    return new File(directory, key + ".img");
  }

  public File temporary(String key) {
    return new File(directory, key + ".tmp");
  }

  public static synchronized Closeable acquire(String path) {
    pins.put(path, pins.getOrDefault(path, 0) + 1);
    return () -> {
      synchronized (DigestImages.class) {
        int n = pins.getOrDefault(path, 1) - 1;
        if (n <= 0) pins.remove(path);
        else pins.put(path, n);
      }
    };
  }

  public static Bitmap decode(String path) {
    return decode(path, 1280, 720);
  }

  public static Bitmap decode(String path, int width, int height) {
    if (path == null || path.isEmpty()) return null;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, bounds);
    if (bounds.outWidth <= 0
        || bounds.outHeight <= 0
        || (long) bounds.outWidth * bounds.outHeight > 16000000L) return null;
    bounds.inJustDecodeBounds = false;
    bounds.inSampleSize = 1;
    while (bounds.outWidth / bounds.inSampleSize > width
        || bounds.outHeight / bounds.inSampleSize > height) bounds.inSampleSize *= 2;
    return BitmapFactory.decodeFile(path, bounds);
  }

  public File siteImage(String key, String url) throws Exception {
    File target = file(key);
    if (target.isFile()) return target;
    byte[] bytes = new NewsSourceClient().download(url, 16 * 1024 * 1024);
    File tmp = temporary(key);
    try {
      try (FileOutputStream out = new FileOutputStream(tmp)) {
        out.write(bytes);
      }
      Bitmap verified = decode(tmp.getAbsolutePath());
      if (verified == null) throw new IOException("Неполное изображение");
      verified.recycle();
      if (!store(tmp, key, 200L * 1024 * 1024)) throw new IOException("Кеш изображений заполнен");
      return target;
    } finally {
      tmp.delete();
    }
  }

  public void trim(long maxBytes) {
    Set<String> referenced;
    try {
      referenced = new local.tvgpt.assistant.data.DigestRepository(context).referencedImages();
    } catch (Exception e) {
      return;
    }
    File[] files = directory.listFiles((dir, name) -> name.endsWith(".img"));
    if (files == null) return;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    long size = 0;
    for (File f : files) size += f.length();
    for (File f : files) {
      if (size <= maxBytes) break;
      synchronized (DigestImages.class) {
        if (pins.containsKey(f.getAbsolutePath()) || referenced.contains(f.getAbsolutePath()))
          continue;
        long bytes = f.length();
        if (f.delete()) size -= bytes;
      }
    }
  }

  /** Admission refuses new covers rather than evicting retained offline releases. */
  public boolean store(File temporary, String key, long maxBytes) throws IOException {
    trim(Math.max(0, maxBytes - temporary.length()));
    synchronized (DigestImages.class) {
      long size = 0;
      File[] images = directory.listFiles((dir, name) -> name.endsWith(".img"));
      if (images != null) for (File image : images) size += image.length();
      File target = file(key);
      if (target.isFile()) return true;
      if (size + temporary.length() > maxBytes) return false;
      return temporary.renameTo(target);
    }
  }
}
