package local.tvgpt.assistant;

public final class AudioFocusLeaseTest {
  public static void main(String[] args) {
    AudioFocusLease lease = new AudioFocusLease();
    if (lease.reclaim(0)) throw new AssertionError("Inactive call reclaimed focus");
    lease.open();
    if (!lease.reclaim(0) || lease.reclaim(100))
      throw new AssertionError("Loss must reclaim once without a tight loop");
    if (lease.delay(100) != 900)
      throw new AssertionError("Rapid loss needs a delayed retry, not abandonment");
    if (!lease.reclaim(1200) || !lease.reclaim(2400) || lease.reclaim(3600))
      throw new AssertionError("Focus contention must be bounded");
    lease.close();
    if (lease.reclaim(10000))
      throw new AssertionError("Closed call reclaimed microphone/audio focus");
    System.out.println(
        "Audio focus: active lease, contention bound and closed-call isolation passed");
  }
}
