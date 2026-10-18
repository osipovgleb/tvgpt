package local.tvgpt.assistant;

import android.content.Context;

class VoicePanel extends android.view.View {
  static VoicePanel active;
  int closes;

  VoicePanel(Context c, Runnable exit, boolean compact) {}

  void close() {
    closes++;
    if (active == this) active = null;
  }

  void requestEnd() {
    close();
  }

  void begin() {
    active = this;
  }

  void checkVoice() {}
}

class TvAccessibility {
  static TvAccessibility current;
  Object owner;
  VoicePanel overlay;
  boolean rejectToken;

  boolean ownsVoiceOverlay(Object owner) {
    return this.owner == owner && overlay != null;
  }

  void hideVoiceOverlay(Object owner) {
    if (this.owner == owner) {
      if (overlay != null) overlay.close();
      overlay = null;
      this.owner = null;
    }
  }

  void showVoiceOverlay(Object owner, Runnable finish) {
    this.owner = owner;
    if (rejectToken) throw new android.view.WindowManager.BadTokenException("stale token");
  }

  void detachVoiceSessionForHome() {
    Object old = owner;
    owner = null;
    if (old != null) ((android.service.voice.VoiceInteractionSession) old).finish();
  }
}

class RemoteFocus {
  static void attach(android.view.Window w) {}

  static void detach(android.view.Window w) {}
}
