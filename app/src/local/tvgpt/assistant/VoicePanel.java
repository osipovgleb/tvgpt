package local.tvgpt.assistant;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import local.tvgpt.assistant.voice.NativeWebRtcTransport;

final class VoicePanel extends LinearLayout {
  static volatile VoicePanel active;
  final TextView status;
  private final TextView caption, micLabel;
  private final VoiceOrb orb;
  private final VoiceControl mute;
  private VoiceSession session;
  private VoiceSession completedSession;
  private boolean muted;
  private boolean ending;
  private boolean mediaOwned;
  private AudioDucking ducking;
  private volatile boolean closed;
  private boolean started, ready, checkPending;
  private volatile boolean failed;
  private volatile int generation;
  private final Runnable exit;
  private final boolean compact;
  private final Handler ui = new Handler(Looper.getMainLooper());

  VoicePanel(Context c, Runnable exit) {
    this(c, exit, true);
  }

  VoicePanel(Context c, Runnable exit, boolean compact) {
    super(c);
    this.exit = exit;
    this.compact = compact;
    if (compact) {
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER);
      setPadding(dp(8), dp(8), dp(8), dp(8));
      setBackgroundColor(android.graphics.Color.TRANSPARENT);
      status = new TextView(c);
      caption = new TextView(c);
      micLabel = new TextView(c);
      mute = new VoiceControl(c, false);
      orb = new VoiceOrb(c);
      orb.setContentDescription("Голосовой помощник TV GPT");
      orb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      addView(orb, new LinearLayout.LayoutParams(dp(87), dp(87)));
      orb.setOnClickListener(v -> requestEnd());
      return;
    }
    throw new IllegalArgumentException("VoicePanel is an assistant popup; use compact mode");
  }

  private int dp(float value) {
    return (int) (getResources().getDisplayMetrics().density * value + .5f);
  }

  private TextView label(String text, int size, int color) {
    TextView v = new TextView(getContext());
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setGravity(Gravity.CENTER);
    addView(v, new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  boolean failed() {
    return failed;
  }

  void requestEnd() {
    if (closed || ending) return;
    ending = true;
    generation++;
    int token = generation;
    status.setText("Завершаю…");
    orb.setContentDescription("Завершаю голосовой разговор");
    orb.state(false, true);
    if (session != null) session.setMuted(true);
    android.util.Log.i("TVGPTSession", "Closing UI; native microphone mute requested");
    long started = android.os.SystemClock.uptimeMillis();
    releaseMedia(
        () -> {
          android.util.Log.i(
              "TVGPTSession",
              "Media released in " + (android.os.SystemClock.uptimeMillis() - started) + " ms");
          if (!closed && generation == token)
            animate().alpha(0).setDuration(160).withEndAction(exit).start();
        });
  }

  void begin() {
    if (started || closed || ending) return;
    if (!VoicePreflight.ready(
        () -> new AccountStore(getContext()).load() != null,
        () -> {
          failed = true;
          Toast.makeText(getContext(), "Войдите в TV GPT в настройках.", Toast.LENGTH_SHORT).show();
          requestEnd();
        })) return;
    if (VoicePreview.isActive()) {
      VoicePreview.stopActive(() -> ui.post(this::begin));
      return;
    }
    final int attempt = ++generation;
    started = true;
    failed = false;
    ready = false;
    muted = false;
    mediaOwned = true;
    micLabel.setText("Микрофон");
    mute.muted(false);
    orb.muted(false);
    status.setText("Подключение…");
    caption.setText("");
    active = this;
    AssistantService.conversationStarted(this);
    ducking = new AudioDucking(getContext());
    ducking.begin();
    try {
      getContext().startForegroundService(new Intent(getContext(), AudioKeepAlive.class));
    } catch (Exception e) {
      android.util.Log.w(
          "TVGPTAudio", "Foreground audio unavailable: " + e.getClass().getSimpleName());
    }
    createSession(attempt);
    session.start();
    mute.requestFocus();
  }

  private void createSession(int attempt) {
    session =
        new VoiceSession(
            callbacks(attempt),
            callbacks ->
                new NativeWebRtcTransport(
                    getContext(),
                    callbacks,
                    false,
                    new local.tvgpt.assistant.settings.AssistantPreferences(getContext())
                        .preset()
                        .outputGain),
            callbacks -> new LiveSignalling(getContext(), callbacks));
  }

  private VoiceSession.Listener callbacks(final int attempt) {
    return new VoiceSession.Listener() {
      public void ready() {
        ui.post(
            () -> {
              if (valid(attempt)) {
                ready = true;
                orb.state(false, false);
                SessionSounds.ready(getContext());
                status.setText("Слушаю");
                if (checkPending) {
                  checkPending = false;
                  sendVoiceCheck();
                }
              }
            });
      }

      public void status(String text) {
        show(text, attempt);
      }

      public void transcript(String role, String text) {
        ui.post(
            () -> {
              if (valid(attempt) && !text.isEmpty()) {
                if (role.equals("user")) orb.heard();
                caption.setText(text);
                status.setText(role.equals("user") ? "Слушаю" : "Отвечаю");
                if (!role.equals("user")) orb.level(.35f);
              }
            });
      }

      public void level(double value) {
        ui.post(
            () -> {
              if (valid(attempt)) orb.level((float) Math.sqrt(Math.max(0, value)) * 3);
            });
      }

      public void failure(String message) {
        VoicePanel.this.failure(message, attempt);
      }

      public void endRequested() {
        ui.post(
            () -> {
              if (valid(attempt)) requestEnd();
            });
      }
    };
  }

  private boolean valid(int attempt) {
    return !closed && !failed && !ending && attempt == generation;
  }

  private void toggleMute() {
    if (session == null) return;
    muted = !muted;
    session.setMuted(muted);
    mute.muted(muted);
    orb.muted(muted);
    micLabel.setText(muted ? "Выключен" : "Микрофон");
    status.setText(muted ? "Микрофон выключен" : "Слушаю");
  }

  private void show(String text, int attempt) {
    ui.post(
        () -> {
          if (valid(attempt)) {
            orb.busy(text.equals("Выполняю поручение…"));
            status.setText(
                text.toLowerCase(java.util.Locale.ROOT).contains("подключ")
                    ? (ready ? "Слушаю" : "Подключение…")
                    : text);
          }
        });
  }

  private void failure(String message, int attempt) {
    ui.post(
        () -> {
          if (!valid(attempt)) return;
          failed = true;
          ready = false;
          started = false;
          android.util.Log.w("TVGPTAudio", message);
          status.setText(message);
          caption.setText("Нажмите микрофон, чтобы повторить");
          micLabel.setText("Повторить");
          mute.setContentDescription("Повторить подключение");
          if (active == this) active = null;
          if (compact) {
            Toast.makeText(
                    getContext(),
                    "Не удалось продолжить разговор. Попробуйте ещё раз.",
                    Toast.LENGTH_LONG)
                .show();
            requestEnd();
          } else releaseMedia();
        });
  }

  void checkVoice() {
    if (closed || failed || ending) return;
    if (ready) sendVoiceCheck();
    else checkPending = true;
  }

  private void sendVoiceCheck() {
    if (session != null)
      session.userText(
          "Это проверка голосового помощника. Скажи коротко: «Голосовой помощник работает». Не"
              + " выполняй другие действия.");
  }

  void injectTestEvent(String raw) {
    if (session != null) session.serverEvent(raw);
  }

  String diagnostics() {
    return status.getText().toString();
  }

  void injectTestText(String text) {
    if (session != null) session.userText(text);
  }

  private void releaseMedia() {
    releaseMedia(null);
  }

  private void releaseMedia(Runnable finished) {
    if (!mediaOwned) {
      if (finished != null) finished.run();
      return;
    }
    mediaOwned = false;
    VoiceSession ending = session;
    completedSession = ending;
    session = null;
    AudioDucking previousDucking = ducking;
    ducking = null;
    int endingGeneration = generation;
    Runnable released =
        () ->
            ui.post(
                () -> {
                  if (previousDucking != null) previousDucking.close();
                  if (active != this || generation == endingGeneration)
                    AssistantService.conversationEnded(this);
                  if (active == null && !AssistantService.wakeEnabled(getContext()))
                    getContext().stopService(new Intent(getContext(), AudioKeepAlive.class));
                  if (finished != null) finished.run();
                });
    if (ending == null) released.run();
    else ending.close(released);
  }

  void close() {
    if (closed) return;
    closed = true;
    generation++;
    if (active == this) active = null;
    releaseMedia();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    close();
    if (completedSession != null) {
      completedSession.dismissed();
      completedSession = null;
    }
  }

  @Override
  public boolean dispatchKeyEvent(android.view.KeyEvent event) {
    if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
      if (event.getAction() == android.view.KeyEvent.ACTION_UP) requestEnd();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }
}
