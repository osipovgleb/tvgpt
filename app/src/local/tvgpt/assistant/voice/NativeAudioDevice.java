package local.tvgpt.assistant.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaRecorder;
import org.webrtc.audio.JavaAudioDeviceModule;

final class NativeAudioDevice {
  private final JavaAudioDeviceModule module;
  private long measuredSamples;
  private double measuredEnergy;
  private int measuredPeak;

  NativeAudioDevice(Context context, VoiceTransport.Listener listener, boolean playbackOnly) {
    module = createModule(context, listener, playbackOnly);
  }

  private JavaAudioDeviceModule createModule(
      Context context, VoiceTransport.Listener listener, boolean playbackOnly) {
    return JavaAudioDeviceModule.builder(context)
        .setAudioSource(MediaRecorder.AudioSource.MIC)
        .setInputSampleRate(16000)
        .setUseStereoInput(false)
        .setUseStereoOutput(false)
        .setUseHardwareAcousticEchoCanceler(
            JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
        .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
        .setEnableVolumeLogger(false)
        .setSamplesReadyCallback(this::measureInput)
        .setPlaybackSamplesReadyCallback(
            playbackOnly
                ? samples -> {
                  if (samples.getAudioFormat() == android.media.AudioFormat.ENCODING_PCM_16BIT)
                    listener.playbackSamples(
                        samples.getData(), samples.getSampleRate(), samples.getChannelCount());
                }
                : null)
        .setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
        .setAudioRecordErrorCallback(
            new JavaAudioDeviceModule.AudioRecordErrorCallback() {
              public void onWebRtcAudioRecordInitError(String ignored) {
                listener.failure("Не удалось подготовить микрофон");
              }

              public void onWebRtcAudioRecordStartError(
                  JavaAudioDeviceModule.AudioRecordStartErrorCode code, String ignored) {
                listener.failure("Не удалось запустить микрофон");
              }

              public void onWebRtcAudioRecordError(String ignored) {
                listener.failure("Запись с микрофона прервалась");
              }
            })
        .setAudioTrackErrorCallback(
            new JavaAudioDeviceModule.AudioTrackErrorCallback() {
              public void onWebRtcAudioTrackInitError(String ignored) {
                listener.failure("Не удалось подготовить звук");
              }

              public void onWebRtcAudioTrackStartError(
                  JavaAudioDeviceModule.AudioTrackStartErrorCode code, String ignored) {
                listener.failure("Не удалось включить звук");
              }

              public void onWebRtcAudioTrackError(String ignored) {
                listener.failure("Воспроизведение прервалось");
              }
            })
        .createAudioDeviceModule();
  }

  private void measureInput(JavaAudioDeviceModule.AudioSamples samples) {
    if (samples.getAudioFormat() != android.media.AudioFormat.ENCODING_PCM_16BIT) return;
    byte[] data = samples.getData();
    for (int i = 0; i + 1 < data.length; i += 2) {
      int value = (short) ((data[i] & 255) | (data[i + 1] << 8));
      measuredPeak = Math.max(measuredPeak, Math.abs(value));
      measuredEnergy += (double) value * value;
      measuredSamples++;
    }
    if (measuredSamples >= (long) samples.getSampleRate() * samples.getChannelCount() * 5) {
      android.util.Log.i(
          "TVGPTAudio",
          "capture raw rms="
              + Math.sqrt(measuredEnergy / measuredSamples)
              + " peak="
              + measuredPeak);
      measuredSamples = 0;
      measuredEnergy = 0;
      measuredPeak = 0;
    }
  }

  JavaAudioDeviceModule module() {
    return module;
  }

  void setMuted(boolean muted) {
    module.setMicrophoneMute(muted);
  }

  void close() {
    module.release();
  }
}
