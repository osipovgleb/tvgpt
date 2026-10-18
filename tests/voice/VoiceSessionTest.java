package local.tvgpt.assistant;

import local.tvgpt.assistant.voice.VoiceTransport;

public final class VoiceSessionTest {
  public static void main(String[] args) {
    Scenario active = new Scenario();
    active.session.start();
    active.session.start();
    check(active.peer.starts == 1, "start must be idempotent");
    active.peer.listener.offer("sdp-offer");
    check(active.signalling.offers == 1, "offer was not sent to signalling");
    active.signalling.listener.answer("sdp-answer");
    check("sdp-answer".equals(active.peer.answer), "answer was not routed to native transport");
    active.peer.listener.ready();
    active.peer.listener.ready();
    check(active.listener.ready == 1, "one connection must produce one startup cue");
    active.session.setMuted(true);
    check(active.peer.muted, "mute must reach the real transport");
    active.peer.listener.event("control-event");
    check(active.signalling.events == 1, "control events must reach Live signalling");
    active.session.close(() -> active.listener.releases++);
    check(active.signalling.closed, "ending must close the Live session");
    check(active.listener.releases == 0, "wake must wait for actual microphone release");
    active.peer.listener.offer("late-offer");
    active.peer.listener.ready();
    active.signalling.listener.failure("late-error");
    check(
        active.signalling.offers == 1 && active.listener.ready == 1 && active.listener.errors == 0,
        "late callbacks must not revive an ended conversation");
    active.peer.released.run();
    active.peer.released.run();
    check(active.listener.releases == 1, "release completion must run once");
    check(
        active.signalling.archives == 0,
        "Media close must not start archive work before the orb disappears");
    active.session.dismissed();
    active.session.dismissed();
    check(
        active.signalling.archives == 1,
        "Detached completed conversation must archive exactly once");
    Scenario earlyDetach = new Scenario();
    earlyDetach.session.start();
    earlyDetach.session.close(null);
    earlyDetach.session.dismissed();
    check(earlyDetach.signalling.archives == 0, "Archive must not compete with microphone release");
    earlyDetach.peer.released.run();
    check(
        earlyDetach.signalling.archives == 1,
        "Detached call must archive when media release completes");

    Scenario failing = new Scenario();
    failing.session.start();
    failing.peer.listener.failure("microphone failed");
    failing.peer.listener.failure("duplicate failure");
    check(
        failing.listener.errors == 1 && failing.signalling.closed,
        "capture failure must close signalling once");
    failing.peer.released.run();
    failing.session.close(() -> failing.listener.releases++);
    check(failing.listener.releases == 1, "a previously released session must notify immediately");
    System.out.println(
        "VoiceSessionTest: native routing, mute, ready, late callbacks and release ordering"
            + " passed");
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static final class Scenario {
    final Peer peer = new Peer();
    final Signals signalling = new Signals();
    final Listener listener = new Listener();
    final VoiceSession session =
        new VoiceSession(
            listener,
            callbacks -> {
              peer.listener = callbacks;
              return peer;
            },
            callbacks -> {
              signalling.listener = callbacks;
              return signalling;
            });
  }

  private static final class Peer implements VoiceTransport {
    VoiceTransport.Listener listener;
    int starts;
    boolean muted;
    String answer;
    Runnable released;

    public void start() {
      starts++;
    }

    public void answer(String sdp) {
      answer = sdp;
    }

    public void setMuted(boolean value) {
      muted = value;
    }

    public void close(Runnable callback) {
      released = callback;
    }
  }

  private static final class Signals implements VoiceSession.Signalling {
    VoiceSession.SignalListener listener;
    int offers, events, archives;
    boolean closed;

    public void connect(String sdp) {
      offers++;
    }

    public void event(String raw) {
      events++;
    }

    public void userText(String text) {}

    public void close() {
      closed = true;
    }

    public void afterDismissed() {
      archives++;
    }
  }

  private static final class Listener implements VoiceSession.Listener {
    int ready, errors, releases;

    public void ready() {
      ready++;
    }

    public void status(String message) {}

    public void transcript(String role, String text) {}

    public void level(double value) {}

    public void failure(String message) {
      errors++;
    }

    public void endRequested() {}
  }
}
