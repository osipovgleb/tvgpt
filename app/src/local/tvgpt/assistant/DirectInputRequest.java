package local.tvgpt.assistant;

/** One bounded request. Cancellation and delivery share a lock to prevent late text entry. */
final class DirectInputRequest {
  interface Delivery {
    String commit(String text, boolean submit);
  }

  interface DeliveryGate {
    boolean permits();
  }

  private final DeliveryGate gate;
  private final String text;
  private final String targetPackage;
  private final boolean submit;
  private final long deadline;
  private final Thread owner = Thread.currentThread();
  private boolean finished;
  private String result;

  DirectInputRequest(String text, String targetPackage, boolean submit, long deadline) {
    this(text, targetPackage, submit, deadline, () -> true);
  }

  DirectInputRequest(
      String text, String targetPackage, boolean submit, long deadline, DeliveryGate gate) {
    this.gate = gate;
    this.text = text;
    this.targetPackage = targetPackage;
    this.submit = submit;
    this.deadline = deadline;
  }

  synchronized boolean deliver(String editorPackage, long now, Delivery delivery) {
    if (finished
        || owner.isInterrupted()
        || now >= deadline
        || !targetPackage.equals(editorPackage)
        || !gate.permits()) return false;
    finished = true;
    try {
      result = delivery.commit(text, submit);
    } finally {
      notifyAll();
    }
    return true;
  }

  synchronized String result() {
    return result;
  }

  synchronized void cancel() {
    finished = true;
    notifyAll();
  }
}
