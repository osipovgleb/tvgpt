package android.app;

public final class Notification {
  String text;

  public static final class Builder {
    private final Notification n = new Notification();

    public Builder(android.content.Context c, String s) {}

    public Builder setSmallIcon(int i) {
      return this;
    }

    public Builder setContentTitle(String s) {
      return this;
    }

    public Builder setContentText(String s) {
      n.text = s;
      return this;
    }

    public Builder setStyle(BigTextStyle s) {
      return this;
    }

    public Builder setAutoCancel(boolean b) {
      return this;
    }

    public Notification build() {
      return n;
    }
  }

  public static final class BigTextStyle {
    public BigTextStyle bigText(String t) {
      return this;
    }
  }
}
