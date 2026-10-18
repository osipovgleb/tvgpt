package local.tvgpt.assistant.digests;

import android.service.dreams.DreamService;

public final class DigestDreamService extends DreamService {
  private DigestSlideshow slides;

  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    setInteractive(false);
    setFullscreen(true);
    slides = new DigestSlideshow(this, "");
    setContentView(slides);
  }

  public void onDreamingStarted() {
    super.onDreamingStarted();
    if (slides != null) slides.start();
  }

  public void onDreamingStopped() {
    if (slides != null) slides.stop();
    super.onDreamingStopped();
  }

  public void onDetachedFromWindow() {
    if (slides != null) slides.release();
    slides = null;
    super.onDetachedFromWindow();
  }
}
