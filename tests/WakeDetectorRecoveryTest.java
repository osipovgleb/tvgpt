package local.tvgpt.assistant;

import android.content.Context;
import android.media.AudioRecord;
import java.nio.file.*;
import java.util.function.BooleanSupplier;

public final class WakeDetectorRecoveryTest {
  public static void main(String[] args) throws Exception {
    Path files = Files.createTempDirectory("wake-recovery-test");
    Files.createDirectories(files.resolve("wake-ru-0.22"));
    Files.createFile(files.resolve("wake-ru-0.22/ready"));
    WakeDetector detector =
        new WakeDetector(
            new Context(files.toFile()),
            () -> {
              throw new AssertionError("no speech supplied");
            });
    try {
      detector.start();
      await(() -> AudioRecord.created.get() == 1);
      AudioRecord.fail = false;
      await(() -> AudioRecord.created.get() == 2);
      detector.stop();
      AudioRecord.fail = true;
      detector.start();
      await(() -> AudioRecord.created.get() == 3);
      detector.stop();
      Thread.sleep(650);
      if (AudioRecord.created.get() != 3)
        throw new AssertionError("stop must cancel delayed retry");
      detector.start();
      await(() -> AudioRecord.created.get() == 4);
      detector.close();
      Thread.sleep(650);
      if (AudioRecord.created.get() != 4)
        throw new AssertionError("close must cancel delayed retry");
      AudioRecord.fail = false;
      java.util.concurrent.atomic.AtomicInteger wakes =
          new java.util.concurrent.atomic.AtomicInteger();
      WakeDetector speech = new WakeDetector(new Context(files.toFile()), wakes::incrementAndGet);
      try {
        speech.start();
        org.vosk.Recognizer.frames.add("P{\"partial\":\"привет телевизор\"}");
        await(() -> org.vosk.Recognizer.processed.get() >= 1);
        Thread.sleep(100);
        if (wakes.get() != 0) throw new AssertionError("Unstable partial opened Live");
        org.vosk.Recognizer.frames.add("F{\"text\":\"[unk]\"}");
        await(() -> org.vosk.Recognizer.processed.get() >= 2);
        if (wakes.get() != 0) throw new AssertionError("Corrected hypothesis opened Live");
        org.vosk.Recognizer.frames.add("F{\"text\":\"привет телевизор\"}");
        await(() -> wakes.get() == 1);
        Thread.sleep(100);
        if (wakes.get() != 1) throw new AssertionError("Final phrase dispatched twice");
      } finally {
        speech.close();
      }
      System.out.println("WakeDetectorRecoveryTest passed: recovery and final-only invocation");
    } finally {
      detector.close();
      Files.delete(files.resolve("wake-ru-0.22/ready"));
      Files.delete(files.resolve("wake-ru-0.22"));
      Files.delete(files);
    }
  }

  private static void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + 2_000_000_000L;
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
    if (!condition.getAsBoolean()) throw new AssertionError("recorder did not recover/start");
  }
}
