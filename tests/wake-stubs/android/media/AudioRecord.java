package android.media;

public class AudioRecord {
  public static final int STATE_INITIALIZED = 1, RECORDSTATE_RECORDING = 3, READ_NON_BLOCKING = 1;
  public static final java.util.concurrent.atomic.AtomicInteger created =
      new java.util.concurrent.atomic.AtomicInteger();
  public static volatile boolean fail = true;
  private boolean released;
  private final boolean failed;

  public AudioRecord(int source, int rate, int channel, int format, int buffer) {
    if (source != 1 || rate != 16000 || channel != 16 || format != 2)
      throw new AssertionError("capture format changed");
    failed = fail;
    created.incrementAndGet();
  }

  public static int getMinBufferSize(int rate, int channels, int encoding) {
    return 3200;
  }

  public int getState() {
    return failed ? 0 : STATE_INITIALIZED;
  }

  public void startRecording() {}

  public int getRecordingState() {
    return RECORDSTATE_RECORDING;
  }

  public int read(byte[] bytes, int offset, int size, int mode) {
    if (released) throw new AssertionError("read after release");
    return 0;
  }

  public void stop() {}

  public void release() {
    released = true;
  }
}
