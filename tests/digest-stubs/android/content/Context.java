package android.content;

public final class Context {
  public Context getApplicationContext() {
    return this;
  }

  public java.io.File getFilesDir() {
    java.io.File f = new java.io.File(System.getProperty("digest.test.root"));
    f.mkdirs();
    return f;
  }
}
