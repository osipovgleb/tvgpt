package local.tvgpt.assistant;

import android.os.Bundle;
import android.service.voice.*;
import android.view.*;

public class SessionService extends VoiceInteractionSessionService {
  @Override
  public VoiceInteractionSession onNewSession(Bundle args) {
    return new TvSession(this);
  }

  static class TvSession extends VoiceInteractionSession {
    private static final AssistantInvocation invocation = new AssistantInvocation();
    private VoicePanel panel;

    TvSession(android.content.Context context) {
      super(context);
    }

    @Override
    public View onCreateContentView() {
      android.widget.FrameLayout frame = new android.widget.FrameLayout(getContext());
      frame.setPadding(24, 0, 24, 24);
      panel = new VoicePanel(getContext(), () -> finish(), true);
      frame.addView(
          panel,
          new android.widget.FrameLayout.LayoutParams(
              (int) (getContext().getResources().getDisplayMetrics().density * 103),
              -2,
              Gravity.RIGHT | Gravity.BOTTOM));
      return frame;
    }

    @Override
    public void onShow(Bundle args, int flags) {
      super.onShow(args, flags);
      String source = args == null ? "" : args.getString("source", "");
      android.util.Log.i(
          "TVGPTSession", "show source=" + source + " active=" + (VoicePanel.active != null));
      if (VoicePanel.active == null
          && invocation.ignoreRestart(source, android.os.SystemClock.uptimeMillis())) {
        android.util.Log.i("TVGPTSession", "Ignored duplicate show after key hide");
        finish();
        return;
      }
      if (VoicePanel.active != null && !source.equals("wake") && !source.equals("launch")) {
        VoicePanel.active.requestEnd();
        finish();
        return;
      }
      if (TvAccessibility.current != null) {
        if (panel != null) {
          panel.close();
          panel = null;
        }
        setUiEnabled(false);
        TvAccessibility.current.showVoiceOverlay(this, () -> finish());
        if (args != null && args.getBoolean("voice_check") && VoicePanel.active != null)
          VoicePanel.active.checkVoice();
        return;
      }
      if (panel == null) setContentView(onCreateContentView());
      setUiEnabled(true);
      RemoteFocus.attach(getWindow().getWindow());
      panel.begin();
      if (args != null && args.getBoolean("voice_check")) panel.checkVoice();
    }

    @Override
    public void onHide() {
      android.util.Log.i("TVGPTSession", "hide active=" + (VoicePanel.active != null));
      if (VoicePanel.active != null
          && (panel == VoicePanel.active
              || (TvAccessibility.current != null
                  && TvAccessibility.current.ownsVoiceOverlay(this))))
        invocation.hidden(android.os.SystemClock.uptimeMillis());
      RemoteFocus.detach(getWindow().getWindow());
      if (TvAccessibility.current != null) TvAccessibility.current.hideVoiceOverlay(this);
      if (panel != null) {
        panel.close();
        panel = null;
      }
      super.onHide();
    }

    @Override
    public void onDestroy() {
      if (TvAccessibility.current != null) TvAccessibility.current.hideVoiceOverlay(this);
      if (panel != null) panel.close();
      super.onDestroy();
    }

    @Override
    public void onBackPressed() {
      if (VoicePanel.active != null) VoicePanel.active.requestEnd();
      finish();
    }
  }
}
