package org.vosk;

public class Recognizer implements AutoCloseable {
  public static final java.util.concurrent.ConcurrentLinkedQueue<String> frames =
      new java.util.concurrent.ConcurrentLinkedQueue<>();
  public static final java.util.concurrent.atomic.AtomicInteger processed =
      new java.util.concurrent.atomic.AtomicInteger();
  private String current = "";

  public Recognizer(Model model, float rate, String grammar) {}

  public boolean acceptWaveForm(byte[] bytes, int count) {
    String next = frames.poll();
    current = next == null ? "" : next;
    processed.incrementAndGet();
    return current.startsWith("F");
  }

  public String getResult() {
    return current.isEmpty() ? "{}" : current.substring(1);
  }

  public String getPartialResult() {
    return current.isEmpty() ? "{}" : current.substring(1);
  }

  public void close() {}
}
