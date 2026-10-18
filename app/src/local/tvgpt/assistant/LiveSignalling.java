package local.tvgpt.assistant;

import android.content.Context;

final class LiveSignalling implements VoiceSession.Signalling {
  private final LiveConnection connection;

  LiveSignalling(Context context, VoiceSession.SignalListener listener) {
    connection =
        new LiveConnection(
            context,
            new LiveConnection.Listener() {
              public void answer(String sdp) {
                listener.answer(sdp);
              }

              public void status(String text) {
                listener.status(text);
              }

              public void transcript(String role, String text) {
                listener.transcript(role, text);
              }

              public void fatal(String message) {
                listener.failure(message);
              }

              public void endRequested() {
                listener.endRequested();
              }
            });
  }

  public void connect(String sdp) {
    connection.connect(sdp);
  }

  public void event(String raw) {
    connection.event(raw);
  }

  public void userText(String text) {
    connection.userText(text);
  }

  public void close() {
    connection.close();
  }

  public void afterDismissed() {
    connection.afterDismissed();
  }
}
