package android.service.voice;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

public class VoiceInteractionSession {
  private final Context context;
  public int hides, finishes;

  public VoiceInteractionSession(Context c) {
    context = c;
  }

  public Context getContext() {
    return context;
  }

  public android.app.Dialog getWindow() {
    return new android.app.Dialog();
  }

  public View onCreateContentView() {
    return null;
  }

  public void onShow(Bundle b, int flags) {}

  public void onHide() {}

  public void onDestroy() {}

  public void onBackPressed() {}

  public void setUiEnabled(boolean enabled) {}

  public void setContentView(View v) {}

  public void hide() {
    hides++;
    onHide();
  }

  public void finish() {
    finishes++;
    onHide();
  }

  public void onCloseSystemDialogs() {
    hide();
  }
}
