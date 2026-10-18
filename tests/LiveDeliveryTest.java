package local.tvgpt.assistant;

public class LiveDeliveryTest {
  public static void main(String[] args) throws Exception {
    try {
      LiveDelivery.send("receipt", new CancellationToken(), part -> false);
      throw new AssertionError("false ignored");
    } catch (java.io.IOException expected) {
    }
    CancellationToken token = new CancellationToken();
    int[] sent = {0};
    try {
      LiveDelivery.send(
          "я".repeat(800),
          token,
          part -> {
            sent[0]++;
            token.cancel();
            return true;
          });
      throw new AssertionError("late chunks delivered");
    } catch (java.util.concurrent.CancellationException expected) {
    }
    if (sent[0] != 1) throw new AssertionError("cancel chunk boundary");
    System.out.println("LiveDeliveryTest: enqueue failure surfaced and late chunks cancelled");
  }
}
