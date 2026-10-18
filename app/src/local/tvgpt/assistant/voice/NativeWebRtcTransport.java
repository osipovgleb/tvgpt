package local.tvgpt.assistant.voice;

import android.content.Context;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.webrtc.*;

/** All native resource creation and destruction is serialized off the Android UI thread. */
public final class NativeWebRtcTransport implements VoiceTransport {
  private static final ScheduledExecutorService WORKER =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "tvgpt-native-rtc");
            thread.setDaemon(true);
            return thread;
          });
  private static boolean initialized;
  private final Context context;
  private final Listener listener;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean offerSent = new AtomicBoolean();
  private final AtomicBoolean failureSent = new AtomicBoolean();
  private final CompletableFuture<Void> releaseCompletion = new CompletableFuture<>();
  private NativeAudioDevice audio;
  private PeerConnectionFactory factory;
  private PeerConnection peer;
  private AudioSource source;
  private AudioTrack microphone;
  private final List<DataChannel> channels = new ArrayList<>();
  private ScheduledFuture<?> stats;
  private ScheduledFuture<?> disconnect;
  private ScheduledFuture<?> setupTimeout;
  private boolean muted;
  private int statsTicks;
  private final boolean playbackOnly;
  private final double outputGain;

  public NativeWebRtcTransport(Context context, Listener listener) {
    this(context, listener, false, 1);
  }

  public NativeWebRtcTransport(
      Context context, Listener listener, boolean playbackOnly, double gain) {
    this.context = context.getApplicationContext();
    this.listener = listener;
    this.playbackOnly = playbackOnly;
    outputGain = Math.max(0, Math.min(1, gain));
  }

  public void start() {
    if (!started.compareAndSet(false, true)) return;
    submit(
        () -> {
          if (!initialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions());
            initialized = true;
          }
          audio = new NativeAudioDevice(context, new AudioCallbacks(), playbackOnly);
          factory =
              PeerConnectionFactory.builder()
                  .setAudioDeviceModule(audio.module())
                  .createPeerConnectionFactory();
          PeerConnection.RTCConfiguration config =
              new PeerConnection.RTCConfiguration(Collections.emptyList());
          config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
          config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
          config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
          peer = factory.createPeerConnection(config, new PeerObserver());
          if (peer == null) throw new IllegalStateException("Peer connection unavailable");
          MediaConstraints processing = new MediaConstraints();
          processing.optional.add(
              new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
          processing.optional.add(
              new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
          processing.optional.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
          if (!playbackOnly) {
            source = factory.createAudioSource(processing);
            microphone = factory.createAudioTrack("tvgpt-microphone", source);
            microphone.setEnabled(!muted);
            audio.setMuted(muted);
            peer.addTrack(microphone, Collections.singletonList("tvgpt-audio"));
          } else
            peer.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                new RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
          bindChannel(peer.createDataChannel("oai-events", new DataChannel.Init()));
          MediaConstraints offer = new MediaConstraints();
          offer.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
          offer.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
          peer.createOffer(
              new DescriptionObserver() {
                public void onCreateSuccess(SessionDescription sdp) {
                  submit(
                      () ->
                          peer.setLocalDescription(
                              new DescriptionObserver() {
                                public void onSetSuccess() {
                                  WORKER.schedule(
                                      NativeWebRtcTransport.this::sendOffer, 2, TimeUnit.SECONDS);
                                }
                              },
                              sdp));
                }
              },
              offer);
          setupTimeout =
              WORKER.schedule(
                  () -> fail("Не удалось подключить голос за отведённое время"),
                  25,
                  TimeUnit.SECONDS);
        });
  }

  public void answer(String sdp) {
    submit(
        () ->
            peer.setRemoteDescription(
                new DescriptionObserver() {},
                new SessionDescription(SessionDescription.Type.ANSWER, sdp)));
  }

  public void setMuted(boolean value) {
    submit(
        () -> {
          muted = value;
          if (microphone != null) microphone.setEnabled(!value);
          if (audio != null) audio.setMuted(value);
        });
  }

  public void close(Runnable released) {
    if (released != null) releaseCompletion.thenRun(released);
    if (!closed.compareAndSet(false, true)) return;
    WORKER.execute(
        () -> {
          try {
            releaseResources();
          } finally {
            releaseCompletion.complete(null);
          }
        });
  }

  private void releaseResources() {
    if (stats != null) stats.cancel(false);
    if (disconnect != null) disconnect.cancel(false);
    if (setupTimeout != null) setupTimeout.cancel(false);
    // Every owner is released even when a device-specific dispose call fails.
    for (DataChannel channel : channels) {
      release(channel::unregisterObserver);
      release(channel::close);
      release(channel::dispose);
    }
    channels.clear();
    release(
        () -> {
          if (peer != null) peer.close();
        });
    release(
        () -> {
          if (peer != null) peer.dispose();
        });
    release(
        () -> {
          if (microphone != null) microphone.dispose();
        });
    release(
        () -> {
          if (source != null) source.dispose();
        });
    release(
        () -> {
          if (factory != null) factory.dispose();
        });
    release(
        () -> {
          if (audio != null) audio.close();
        });
    peer = null;
    microphone = null;
    source = null;
    factory = null;
    audio = null;
  }

  private void release(Runnable operation) {
    try {
      operation.run();
    } catch (RuntimeException failure) {
      Log.w("TVGPTAudio", "Native audio cleanup: " + failure.getClass().getSimpleName());
    }
  }

  private void submit(Runnable operation) {
    if (closed.get()) return;
    WORKER.execute(
        () -> {
          if (closed.get()) return;
          try {
            operation.run();
          } catch (RuntimeException | LinkageError failure) {
            Log.w("TVGPTAudio", "Native transport: " + failure.getClass().getSimpleName());
            fail("Не удалось запустить нативный голосовой канал");
          }
        });
  }

  private void fail(String message) {
    if (!closed.get() && failureSent.compareAndSet(false, true)) listener.failure(message);
  }

  private void sendOffer() {
    if (closed.get() || peer == null || peer.getLocalDescription() == null) return;
    if (offerSent.compareAndSet(false, true))
      listener.offer(peer.getLocalDescription().description);
  }

  private void connected() {
    if (setupTimeout != null) setupTimeout.cancel(false);
    if (disconnect != null) disconnect.cancel(false);
    listener.ready();
    if (stats == null)
      stats = WORKER.scheduleAtFixedRate(this::collectStats, 1, 1, TimeUnit.SECONDS);
  }

  private void collectStats() {
    if (closed.get() || peer == null) return;
    peer.getStats(
        report -> {
          if (closed.get()) return;
          double level = 0;
          double inputLevel = 0;
          double inputEnergy = 0;
          long sent = 0;
          long received = 0;
          for (RTCStats stat : report.getStatsMap().values()) {
            Map<String, Object> values = stat.getMembers();
            if (playbackOnly
                && "inbound-rtp".equals(stat.getType())
                && values.get("totalAudioEnergy") instanceof Number
                && ((Number) values.get("totalAudioEnergy")).doubleValue() > 0) listener.playback();
            if ("media-source".equals(stat.getType()) || "inbound-rtp".equals(stat.getType())) {
              Object candidate = values.get("audioLevel");
              if (candidate instanceof Number)
                level = Math.max(level, ((Number) candidate).doubleValue());
            }
            if ("media-source".equals(stat.getType())) {
              Object input = values.get("audioLevel");
              Object energy = values.get("totalAudioEnergy");
              if (input instanceof Number) inputLevel = ((Number) input).doubleValue();
              if (energy instanceof Number) inputEnergy = ((Number) energy).doubleValue();
            }
            if ("outbound-rtp".equals(stat.getType()) && values.get("bytesSent") instanceof Number)
              sent += ((Number) values.get("bytesSent")).longValue();
            if ("inbound-rtp".equals(stat.getType())
                && values.get("bytesReceived") instanceof Number)
              received += ((Number) values.get("bytesReceived")).longValue();
          }
          listener.level(level);
          if (++statsTicks % 5 == 0)
            Log.i(
                "TVGPTAudio",
                "native WebRTC sent="
                    + sent
                    + " received="
                    + received
                    + " inputLevel="
                    + inputLevel
                    + " inputEnergy="
                    + inputEnergy);
        });
  }

  private void bindChannel(DataChannel value) {
    if (value == null) throw new IllegalStateException("Voice control channel unavailable");
    channels.add(value);
    value.registerObserver(
        new DataChannel.Observer() {
          public void onBufferedAmountChange(long amount) {}

          public void onStateChange() {}

          public void onMessage(DataChannel.Buffer buffer) {
            if (!closed.get() && !buffer.binary && buffer.data.remaining() <= 1_048_576)
              listener.event(
                  StandardCharsets.UTF_8.decode(buffer.data.asReadOnlyBuffer()).toString());
          }
        });
  }

  private class DescriptionObserver implements SdpObserver {
    public void onCreateSuccess(SessionDescription description) {}

    public void onSetSuccess() {}

    public void onCreateFailure(String ignored) {
      fail("Не удалось подготовить голосовое соединение");
    }

    public void onSetFailure(String ignored) {
      fail("Сервер не принял параметры голосового соединения");
    }
  }

  private final class PeerObserver implements PeerConnection.Observer {
    public void onSignalingChange(PeerConnection.SignalingState state) {}

    public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}

    public void onIceConnectionReceivingChange(boolean receiving) {}

    public void onIceCandidate(IceCandidate candidate) {}

    public void onIceCandidatesRemoved(IceCandidate[] candidates) {}

    public void onAddStream(MediaStream stream) {}

    public void onRemoveStream(MediaStream stream) {}

    public void onRenegotiationNeeded() {}

    public void onDataChannel(DataChannel remote) {
      WORKER.execute(
          () -> {
            if (closed.get()) {
              release(remote::close);
              release(remote::dispose);
            } else bindChannel(remote);
          });
    }

    public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
      if (state == PeerConnection.IceGatheringState.COMPLETE)
        submit(NativeWebRtcTransport.this::sendOffer);
    }

    public void onConnectionChange(PeerConnection.PeerConnectionState state) {
      submit(
          () -> {
            if (state == PeerConnection.PeerConnectionState.CONNECTED) connected();
            else if (state == PeerConnection.PeerConnectionState.FAILED
                || state == PeerConnection.PeerConnectionState.CLOSED)
              fail("Голосовое соединение потеряно");
            else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
              if (disconnect != null) disconnect.cancel(false);
              disconnect =
                  WORKER.schedule(() -> fail("Голосовое соединение потеряно"), 5, TimeUnit.SECONDS);
            }
          });
    }

    public void onTrack(RtpTransceiver transceiver) {
      submit(
          () -> {
            MediaStreamTrack track = transceiver.getReceiver().track();
            if (track != null && MediaStreamTrack.AUDIO_TRACK_KIND.equals(track.kind())) {
              track.setEnabled(true);
              ((AudioTrack) track).setVolume(outputGain);
            }
          });
    }
  }

  private final class AudioCallbacks implements Listener {
    public void offer(String sdp) {}

    public void event(String raw) {}

    public void ready() {}

    public void level(double value) {}

    public void playbackSamples(byte[] pcm, int rate, int channels) {
      if (!closed.get()) listener.playbackSamples(pcm, rate, channels);
    }

    public void failure(String message) {
      fail(message);
    }
  }
}
