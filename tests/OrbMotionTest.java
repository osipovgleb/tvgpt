package local.tvgpt.assistant;

public final class OrbMotionTest {
  public static void main(String[] args) {
    OrbMotion quiet = new OrbMotion();
    OrbMotion speech = new OrbMotion();
    quiet.step(0, 0, false);
    speech.step(0, 1, false);
    for (int i = 1; i <= 20; i++) {
      quiet.step(i * 40, 0, false);
      speech.step(i * 40, 1, false);
    }
    if (speech.rotation <= quiet.rotation || speech.scale <= quiet.scale)
      throw new AssertionError("Speech must increase rotation and pulse");
    if (quiet.rotation < 10 || Math.abs(quiet.cloudX) < .1f)
      throw new AssertionError("Idle orb must visibly move without microphone input");
    OrbMotion acknowledgement = new OrbMotion();
    acknowledgement.step(0, 0, false);
    acknowledgement.heard(0);
    acknowledgement.step(350, 0, false);
    if (acknowledgement.scale > .80f) throw new AssertionError("Recognition pulse too subtle");
    acknowledgement.step(800, 0, false);
    if (acknowledgement.scale < .94f) throw new AssertionError("Recognition pulse did not settle");
    float low = 2, high = 0;
    for (int i = 1; i <= 100; i++) {
      acknowledgement.step(800 + i * 40, 0, false, true);
      low = Math.min(low, acknowledgement.scale);
      high = Math.max(high, acknowledgement.scale);
    }
    if (high - low < .15f) throw new AssertionError("Working pulse too subtle");
    float beforePause = speech.rotation;
    speech.step(3600000, 1, false);
    if (speech.rotation - beforePause > 3)
      throw new AssertionError("Resuming a hidden view must not fast-forward its animation");
    for (int i = 0; i < 1000; i++) {
      speech.step(3600040L + i * 40, Double.NaN, false);
      if (!Float.isFinite(speech.scale) || speech.scale < .94 || speech.scale > 1.04)
        throw new AssertionError("Invalid audio level corrupted the animation");
    }
    System.out.println("OrbMotionTest: audio response, bounded pulse and pause handling passed");
  }
}
