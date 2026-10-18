package local.tvgpt.assistant;

import java.nio.*;
import java.nio.file.*;
import local.tvgpt.assistant.voice.PreviewWave;

public final class PreviewWaveTest {
  public static void main(String[] args) throws Exception {
    Path directory = Files.createTempDirectory("tvgpt-preview-test");
    Path file = directory.resolve("cove.wav");
    try {
      PreviewWave wave = new PreviewWave();
      byte[] silence = new byte[3200], voice = new byte[16000];
      for (int i = 0; i < voice.length; i += 2) {
        voice[i] = 0;
        voice[i + 1] = 4;
      }
      wave.append(silence, 16000, 1);
      wave.append(voice, 16000, 1);
      wave.append(silence, 16000, 1);
      if (wave.save(file.toFile(), false) || Files.exists(file))
        throw new AssertionError("unfinished turn persisted");
      if (!wave.save(file.toFile(), true)) throw new AssertionError("verified voice missing");
      byte[] wav = Files.readAllBytes(file);
      ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
      if (b.getInt(24) != 16000 || b.getShort(22) != 1 || b.getInt(40) != wav.length - 44)
        throw new AssertionError("WAV format");
      PreviewWave quiet = new PreviewWave();
      quiet.append(silence, 16000, 1);
      if (quiet.save(directory.resolve("silence.wav").toFile(), true))
        throw new AssertionError("silence accepted");
      PreviewWave mixed = new PreviewWave();
      mixed.append(voice, 16000, 1);
      mixed.append(voice, 48000, 1);
      if (mixed.save(directory.resolve("mixed.wav").toFile(), true))
        throw new AssertionError("mixed format accepted");
      System.out.println(
          "Voice sample WAV: verified-only, atomic file, correct header, silence and mixed-format"
              + " rejection passed");
    } finally {
      try (java.util.stream.Stream<Path> files = Files.list(directory)) {
        files.forEach(
            p -> {
              try {
                Files.delete(p);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
      }
      Files.delete(directory);
    }
  }
}
