package local.tvgpt.assistant;

import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import local.tvgpt.assistant.settings.SpeechPreset;
import local.tvgpt.assistant.voice.*;
import okhttp3.*;
import org.json.*;

/** Isolated, bounded playback-only call. No Agent, workspace, history or microphone track. */
public final class VoicePreview {
  public interface Listener {
    void status(String text);

    void played(String voice);
  }

  private static VoicePreview active;
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final OkHttpClient client =
      new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();
  private NativeWebRtcTransport transport;
  private WebSocket socket;
  private boolean closed;
  private final CompletableFuture<Void> releaseCompletion = new CompletableFuture<>();
  private boolean audible;
  private boolean spoken;
  private boolean selected;
  private boolean prompted, mediaReady, sessionReady;
  private String voice;
  private final Set<String> eventTypes = new HashSet<>();
  private Listener listener;
  private final PlaybackRecording recording;

  public VoicePreview(Context c) {
    this(c, null);
  }

  public VoicePreview(Context c, PlaybackRecording recording) {
    context = c.getApplicationContext();
    this.recording = recording;
  }

  static synchronized Object owner() {
    return active != null ? active : VoiceSamplePlayer.owner();
  }

  public static synchronized boolean isActive() {
    return active != null || VoiceSamplePlayer.owner() != null;
  }

  public static synchronized void stopActive(Runnable released) {
    VoiceSamplePlayer.stopActive();
    if (active != null) active.stop(released);
    else if (released != null) released.run();
  }

  public void start(String id, SpeechPreset preset, Listener l) {
    synchronized (VoicePreview.class) {
      if (active != null || VoicePanel.active != null) {
        l.status("Сначала завершите текущий разговор или прослушивание");
        return;
      }
      active = this;
    }
    voice = id;
    listener = l;
    AssistantService.conversationStarted(this);
    l.status("Подключаю образец…");
    transport =
        new NativeWebRtcTransport(
            context,
            new VoiceTransport.Listener() {
              public void offer(String sdp) {
                worker.submit(() -> connect(sdp, preset));
              }

              public void event(String raw) {
                eventMessage(raw);
              }

              public void ready() {
                synchronized (VoicePreview.this) {
                  mediaReady = true;
                  promptSample();
                }
              }

              public void playback() {
                synchronized (VoicePreview.this) {
                  audible = true;
                  verifyPlayed();
                }
              }

              public void level(double value) {}

              public void playbackSamples(byte[] pcm, int rate, int channels) {
                if (recording != null) recording.append(pcm, rate, channels);
              }

              public void failure(String text) {
                finish(text);
              }
            },
            true,
            preset.outputGain);
    transport.start();
    main.postDelayed(
        () ->
            finish(audible ? "Образец завершён" : "Образец не прозвучал. Текущий голос сохранён."),
        18000);
  }

  private void connect(String sdp, SpeechPreset preset) {
    try {
      Map<String, String> h = LiveConnection.headers(new AccountStore(context).fresh());
      JSONObject session =
          new JSONObject()
              .put("model", ModelSettings.live(context))
              .put(
                  "instructions",
                  "Ты озвучиваешь короткий образец голоса. Скажи только две фразы: «Привет! Я могу"
                      + " помочь с телевизором. Напомню о важных делах и найду, что посмотреть». "
                      + preset.instructions)
              .put("audio", new JSONObject().put("output", new JSONObject().put("voice", voice)))
              .put("delegation", new JSONObject().put("type", "client").put("ack_filler", false));
      Http.Reply r =
          Http.post(
              "https://chatgpt.com/backend-api/codex/realtime/calls?intent=quicksilver&architecture=avas",
              new JSONObject().put("sdp", sdp).put("session", session).toString(),
              "application/json",
              h);
      if (r.code < 200 || r.code >= 300) {
        finish(PreviewFailure.response(r.code, r.body) + " Текущий голос сохранён.");
        return;
      }
      synchronized (this) {
        if (closed) return;
        Request.Builder req =
            new Request.Builder()
                .url(
                    "wss://api.openai.com/v1/live/"
                        + Protocol.callId(r.location == null ? "" : r.location));
        for (Map.Entry<String, String> e : h.entrySet()) req.header(e.getKey(), e.getValue());
        socket =
            client.newWebSocket(
                req.build(),
                new WebSocketListener() {
                  public void onOpen(WebSocket w, Response response) {
                    synchronized (VoicePreview.this) {
                      socket = w;
                      promptSample();
                    }
                  }

                  public void onMessage(WebSocket w, String text) {
                    eventMessage(text);
                  }

                  public void onFailure(WebSocket w, Throwable t, Response response) {
                    finish("Не удалось прослушать голос. Текущий выбор сохранён.");
                  }
                });
        transport.answer(r.body);
      }
    } catch (Exception e) {
      finish(DeviceLogin.safeError(e) + ". Текущий голос сохранён.");
    }
  }

  private synchronized void promptSample() {
    if (closed || prompted || !mediaReady || !sessionReady || socket == null) return;
    try {
      prompted = true;
      socket.send(
          LiveProtocol.speechOutput(
                  "Привет! Я могу помочь с телевизором. Напомню о важных делах и найду, что"
                      + " посмотреть.")
              .toString());
      android.util.Log.i("TVGPTPreview", "Sample speakable sent voice=" + voice);
    } catch (Exception e) {
      finish("Не удалось запустить образец");
    }
  }

  private synchronized void eventMessage(String raw) {
    if (closed) return;
    try {
      JSONObject e = new JSONObject(raw);
      String type = e.optString("type");
      if (eventTypes.size() < 24 && eventTypes.add(type))
        android.util.Log.i("TVGPTPreview", "Sample event type=" + type);
      if (type.equals("session.started")) {
        sessionReady = true;
        promptSample();
      }
      if (type.equals("turn.done")
          && e.optJSONObject("turn") != null
          && "assistant".equals(e.getJSONObject("turn").optString("role"))) {
        spoken = true;
        verifyPlayed();
      }
      if (type.equals("error"))
        finish(PreviewFailure.response(400, e.toString()) + " Текущий голос сохранён.");
    } catch (Exception ignored) {
    }
  }

  private synchronized void verifyPlayed() {
    if (closed || selected || !audible || !spoken) return;
    selected = true;
    if (recording != null) main.postDelayed(() -> stop(null), 600);
    main.post(
        () -> {
          synchronized (this) {
            if (!closed) listener.played(voice);
          }
        });
  }

  private void finish(String text) {
    main.post(
        () -> {
          synchronized (this) {
            if (closed) return;
          }
          listener.status(text);
          stop(null);
        });
  }

  public void stop(Runnable released) {
    if (released != null) releaseCompletion.whenComplete((ignored, failure) -> released.run());
    NativeWebRtcTransport old;
    synchronized (this) {
      if (closed) return;
      closed = true;
      main.removeCallbacksAndMessages(null);
      if (socket != null) socket.close(1000, "preview closed");
      old = transport;
    }
    worker.shutdownNow();
    client.dispatcher().cancelAll();
    client.connectionPool().evictAll();
    Runnable done =
        () -> {
          synchronized (VoicePreview.class) {
            if (active == this) active = null;
          }
          AssistantService.conversationEnded(this);
          if (recording != null) recording.complete(selected);
          releaseCompletion.complete(null);
        };
    if (old != null) old.close(done);
    else done.run();
  }
}
