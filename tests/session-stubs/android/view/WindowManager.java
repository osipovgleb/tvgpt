package android.view;

public class WindowManager {
  public static class BadTokenException extends RuntimeException {
    public BadTokenException(String message) {
      super(message);
    }
  }
}
