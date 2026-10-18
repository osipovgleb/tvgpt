package local.tvgpt.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import local.tvgpt.assistant.digests.DigestSlideshow;

public final class DigestPreviewActivity extends Activity {
  private DigestSlideshow slides;

  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    slides = new DigestSlideshow(this, getIntent().getStringExtra("digest_id"));
    setContentView(slides);
  }

  protected void onPause() {
    if (slides != null) slides.stop();
    super.onPause();
  }

  protected void onResume() {
    super.onResume();
    if (slides != null) slides.start();
  }

  protected void onDestroy() {
    if (slides != null) slides.release();
    super.onDestroy();
  }
}
