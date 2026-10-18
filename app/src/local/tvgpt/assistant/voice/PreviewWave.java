package local.tvgpt.assistant.voice;

import java.io.*;
import java.nio.*;
import java.nio.file.*;

/**
 * Bounded synthetic-output recorder. Files appear atomically only after a complete verified turn.
 */
public final class PreviewWave {
  private final byte[] pcm = new byte[1024 * 1024];
  private int size, sampleRate, channels, first = -1, last;
  private boolean overflow, formatChanged;

  public synchronized void append(byte[] bytes, int rate, int count) {
    if (rate < 8000 || rate > 48000 || count < 1 || count > 2 || bytes.length % (2 * count) != 0) {
      formatChanged = true;
      return;
    }
    if (sampleRate == 0) {
      sampleRate = rate;
      channels = count;
    }
    if (rate != sampleRate || count != channels) {
      formatChanged = true;
      return;
    }
    if (bytes.length > pcm.length - size) {
      overflow = true;
      return;
    }
    for (int i = 0; i < bytes.length; i += 2) {
      int value = (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
      if (Math.abs(value) > 48) {
        if (first < 0) first = size + i;
        last = size + i + 2;
      }
    }
    if (first < 0) return; // Do not fill the bounded sample with connection-start silence.
    System.arraycopy(bytes, 0, pcm, size, bytes.length);
    size += bytes.length;
  }

  public synchronized boolean save(File target, boolean verified) throws IOException {
    if (!verified
        || first < 0
        || overflow
        || formatChanged
        || last - first < sampleRate * channels / 5) return false;
    int frame = channels * 2, margin = sampleRate * frame / 10;
    int start = Math.max(0, first - margin) / frame * frame;
    int end = Math.min(size, last + margin) / frame * frame;
    int length = end - start;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header
        .put(new byte[] {'R', 'I', 'F', 'F'})
        .putInt(36 + length)
        .put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '})
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) channels)
        .putInt(sampleRate)
        .putInt(sampleRate * frame)
        .putShort((short) frame)
        .putShort((short) 16)
        .put(new byte[] {'d', 'a', 't', 'a'})
        .putInt(length);
    File parent = target.getParentFile();
    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("preview directory");
    File temporary = new File(parent, target.getName() + ".tmp");
    try {
      try (FileOutputStream out = new FileOutputStream(temporary)) {
        out.write(header.array());
        out.write(pcm, start, length);
      }
      Files.move(
          temporary.toPath(),
          target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      return true;
    } finally {
      Files.deleteIfExists(temporary.toPath());
    }
  }
}
