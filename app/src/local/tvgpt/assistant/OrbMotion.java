package local.tvgpt.assistant;

final class OrbMotion {
  float rotation;
  float scale = 1;
  float cloudX, cloudY, cloudRotation;
  private long previous = -1;
  private float level;
  private double phase;
  private long heardAt = Long.MIN_VALUE;

  void heard(long now) {
    if (heardAt == Long.MIN_VALUE || now - heardAt >= 1000) heardAt = now;
  }

  void step(long now, double input, boolean muted) {
    step(now, input, muted, false);
  }

  void step(long now, double input, boolean muted, boolean busy) {
    if (previous < 0) {
      previous = now;
      return;
    }
    float seconds = Math.max(0, Math.min(100, now - previous)) / 1000f;
    previous = now;
    float target = muted || !Double.isFinite(input) ? 0 : (float) Math.max(0, Math.min(1, input));
    level += (target - level) * Math.min(1, seconds * 8);
    rotation = (rotation + seconds * (14 + 16 * level)) % 360;
    phase += seconds;
    cloudX = .24f * (float) Math.sin(phase * .8);
    cloudY = .17f * (float) Math.cos(phase * 1.1);
    cloudRotation = (float) (phase * -18 % 360);
    scale = .98f + .012f * (float) Math.sin(phase * .9) + .025f * level;
    long age = heardAt == Long.MIN_VALUE ? -1 : now - heardAt;
    if (!muted && age >= 0 && age < 700) scale *= 1 - .24f * (float) Math.sin(Math.PI * age / 700);
    else if (!muted && busy) scale *= .88f + .10f * (float) Math.sin(phase * 4.5);
  }
}
