package android.content;

public final class Context {
  public Context getApplicationContext() {
    return this;
  }

  public <T> T getSystemService(Class<T> type) {
    try {
      return type.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
