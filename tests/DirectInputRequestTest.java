package local.tvgpt.assistant;

public final class DirectInputRequestTest {
  public static void main(String[] args) {
    DirectInputRequest request = new DirectInputRequest("привет мир", "youtube", true, 100);
    int[] calls = {0};
    check(
        !request.deliver(
            "other",
            50,
            (text, submit) -> {
              calls[0]++;
              return "bad";
            }),
        "wrong package");
    check(
        request.deliver(
            "youtube",
            60,
            (text, submit) -> {
              check(text.equals("привет мир") && submit, "Unicode and submit preserved");
              calls[0]++;
              return "accepted";
            }),
        "delivery");
    check(
        !request.deliver(
            "youtube",
            70,
            (text, submit) -> {
              calls[0]++;
              return "bad";
            }),
        "once");
    check(calls[0] == 1 && request.result().equals("accepted"), "one commit");
    DirectInputRequest expired = new DirectInputRequest("late", "youtube", false, 100);
    check(
        !expired.deliver(
            "youtube",
            100,
            (text, submit) -> {
              throw new AssertionError("late commit");
            }),
        "deadline");
    DirectInputRequest cancelled = new DirectInputRequest("late", "youtube", false, 100);
    cancelled.cancel();
    check(
        !cancelled.deliver(
            "youtube",
            50,
            (text, submit) -> {
              throw new AssertionError("cancelled commit");
            }),
        "cancel");
    DirectInputRequest interrupted = new DirectInputRequest("late", "youtube", false, 100);
    Thread.currentThread().interrupt();
    try {
      check(
          !interrupted.deliver(
              "youtube",
              50,
              (text, submit) -> {
                throw new AssertionError("interrupted owner committed");
              }),
          "interrupted owner");
    } finally {
      Thread.interrupted();
    }
    boolean[] focusMatches = {false};
    DirectInputRequest guarded =
        new DirectInputRequest("query", "youtube", true, 100, () -> focusMatches[0]);
    int[] guardedCalls = {0};
    DirectInputRequest.Delivery commit =
        (text, submit) -> {
          guardedCalls[0]++;
          return "accepted";
        };
    check(!guarded.deliver("youtube", 50, commit), "changed same-app field rejected");
    check(guarded.result() == null && guardedCalls[0] == 0, "failed guard never commits");
    focusMatches[0] = true;
    check(guarded.deliver("youtube", 60, commit), "matching original field accepted");
    check(!guarded.deliver("youtube", 70, commit) && guardedCalls[0] == 1, "guarded delivery once");
    focusMatches[0] = false;
    DirectInputRequest guardedCancelled =
        new DirectInputRequest("query", "youtube", true, 100, () -> focusMatches[0]);
    check(!guardedCancelled.deliver("youtube", 50, commit), "guard denied before cancel");
    guardedCancelled.cancel();
    focusMatches[0] = true;
    check(!guardedCancelled.deliver("youtube", 60, commit), "guard denial remains cancelable");
    focusMatches[0] = false;
    DirectInputRequest guardedExpired =
        new DirectInputRequest("query", "youtube", true, 100, () -> focusMatches[0]);
    check(!guardedExpired.deliver("youtube", 50, commit), "guard denied before expiry");
    focusMatches[0] = true;
    check(!guardedExpired.deliver("youtube", 100, commit), "guard denial still expires");
    check(guardedCalls[0] == 1, "no rejected request committed");
    System.out.println("DirectInputRequestTest passed");
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
