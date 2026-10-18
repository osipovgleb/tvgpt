package org.vosk;

public class Recognizer implements AutoCloseable {
  public Recognizer(Model model, float rate, String grammar) {}

  public boolean acceptWaveForm(byte[] bytes, int count) {
    return false;
  }

  public String getResult() {
    return "{}";
  }

  public String getPartialResult() {
    return "{}";
  }

  public void close() {}
}
