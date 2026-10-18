package local.tvgpt.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;

/** Dialog fallback only when the firmware has not bound the system assistant service yet. */
public class ConversationActivity extends Activity {
  private VoicePanel panel;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (VoicePanel.active != null) VoicePanel.active.requestEnd();
    panel = new VoicePanel(this, () -> finish());
    setContentView(panel);
    getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    WindowManager.LayoutParams p = getWindow().getAttributes();
    p.width = (int) (getResources().getDisplayMetrics().density * 103);
    p.height = -2;
    p.gravity = Gravity.RIGHT | Gravity.BOTTOM;
    p.x = 24;
    p.y = 24;
    getWindow().setAttributes(p);
    RemoteFocus.attach(getWindow());
    panel.begin();
    if (getIntent().getBooleanExtra("voice_check", false)) panel.checkVoice();
  }

  @Override
  protected void onDestroy() {
    RemoteFocus.detach(getWindow());
    if (panel != null) panel.close();
    super.onDestroy();
  }
}
