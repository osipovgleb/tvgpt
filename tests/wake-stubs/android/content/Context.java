package android.content;

public class Context {
  private final java.io.File files;

  public Context(java.io.File files) {
    this.files = files;
  }

  public java.io.File getFilesDir() {
    return files;
  }

  public android.content.res.AssetManager getAssets() {
    return new android.content.res.AssetManager();
  }
}
