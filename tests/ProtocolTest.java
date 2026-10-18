package local.tvgpt.assistant;

public class ProtocolTest {
  public static void main(String[] args) {
    check(Protocol.pending(403), "pending approval");
    check(Protocol.pending(404), "pending code");
    check(!Protocol.pending(401), "unauthorized must stop");
    check(!Protocol.pending(429), "rate limit must stop");
    check(Protocol.interval("0") == 5, "avoid busy polling");
    check(Protocol.interval("12") == 12, "honor server interval");
    check(Protocol.interval("bad") == 5, "bad interval fallback");
    check(Protocol.callId("/v1/live/rtc_test-42?x=1").equals("rtc_test-42"), "call ID");
    try {
      Protocol.callId("/v1/live/not-a-call");
      throw new AssertionError("invalid ID accepted");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println("ProtocolTest: 8 checks passed");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
