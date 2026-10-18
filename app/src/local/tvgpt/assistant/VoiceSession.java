package local.tvgpt.assistant;

import java.util.ArrayList;
import java.util.List;
import local.tvgpt.assistant.voice.VoiceTransport;

/** Owns one call; the UI owns neither network signalling nor microphone resources. */
final class VoiceSession {
  interface Listener {
    void ready();

    void status(String text);

    void transcript(String role, String text);

    void level(double value);

    void failure(String message);

    void endRequested();
  }

  interface Signalling {
    void connect(String sdp);

    void event(String raw);

    void userText(String text);

    void close();

    default void afterDismissed() {}
  }

  interface SignalListener {
    void answer(String sdp);

    void status(String text);

    void transcript(String role, String text);

    void failure(String message);

    void endRequested();
  }

  interface TransportFactory {
    VoiceTransport create(VoiceTransport.Listener listener);
  }

  interface SignallingFactory {
    Signalling create(SignalListener listener);
  }

  private final Listener listener;
  private final TransportFactory transportFactory;
  private final SignallingFactory signallingFactory;
  private final List<Runnable> releaseCallbacks = new ArrayList<>();
  private VoiceTransport transport;
  private Signalling signalling;
  private boolean started;
  private boolean ready;
  private boolean closed;
  private boolean released;
  private boolean dismissed, archiveScheduled;

  VoiceSession(
      Listener listener, TransportFactory transportFactory, SignallingFactory signallingFactory) {
    this.listener = listener;
    this.transportFactory = transportFactory;
    this.signallingFactory = signallingFactory;
  }

  synchronized void start() {
    if (started || closed) return;
    started = true;
    try {
      signalling = signallingFactory.create(new Signals());
      transport = transportFactory.create(new Audio());
      transport.start();
    } catch (RuntimeException failure) {
      fail("Не удалось запустить голосовой сеанс");
    }
  }

  synchronized void setMuted(boolean value) {
    if (!closed && transport != null) transport.setMuted(value);
  }

  synchronized void userText(String text) {
    if (!closed && signalling != null) signalling.userText(text);
  }

  synchronized void serverEvent(String raw) {
    if (!closed && signalling != null) signalling.event(raw);
  }

  void dismissed() {
    synchronized (this) {
      dismissed = true;
    }
    archiveIfDismissed();
  }

  private void archiveIfDismissed() {
    Signalling completed;
    synchronized (this) {
      if (!released || !dismissed || archiveScheduled || signalling == null) return;
      archiveScheduled = true;
      completed = signalling;
    }
    completed.afterDismissed();
  }

  void close(Runnable onReleased) {
    VoiceTransport ending;
    synchronized (this) {
      if (released) {
        if (onReleased != null) onReleased.run();
        return;
      }
      if (onReleased != null) releaseCallbacks.add(onReleased);
      if (closed) return;
      closed = true;
      ending = transport;
      if (signalling != null) signalling.close();
    }
    if (ending == null) finishRelease();
    else ending.close(this::finishRelease);
  }

  private void finishRelease() {
    List<Runnable> callbacks;
    synchronized (this) {
      if (released) return;
      released = true;
      callbacks = new ArrayList<>(releaseCallbacks);
      releaseCallbacks.clear();
    }
    for (Runnable callback : callbacks) callback.run();
    archiveIfDismissed();
  }

  private synchronized void fail(String message) {
    if (closed) return;
    close(null);
    listener.failure(message);
  }

  private final class Audio implements VoiceTransport.Listener {
    public void offer(String sdp) {
      synchronized (VoiceSession.this) {
        if (!closed) signalling.connect(sdp);
      }
    }

    public void event(String raw) {
      synchronized (VoiceSession.this) {
        if (!closed) signalling.event(raw);
      }
    }

    public void ready() {
      synchronized (VoiceSession.this) {
        if (closed || ready) return;
        ready = true;
        listener.ready();
      }
    }

    public void level(double value) {
      synchronized (VoiceSession.this) {
        if (!closed) listener.level(value);
      }
    }

    public void failure(String message) {
      fail(message);
    }
  }

  private final class Signals implements SignalListener {
    public void answer(String sdp) {
      synchronized (VoiceSession.this) {
        if (!closed && transport != null) transport.answer(sdp);
      }
    }

    public void status(String text) {
      synchronized (VoiceSession.this) {
        if (!closed) listener.status(text);
      }
    }

    public void transcript(String role, String text) {
      synchronized (VoiceSession.this) {
        if (!closed) listener.transcript(role, text);
      }
    }

    public void failure(String message) {
      fail(message);
    }

    public void endRequested() {
      synchronized (VoiceSession.this) {
        if (!closed) listener.endRequested();
      }
    }
  }
}
