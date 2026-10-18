package local.tvgpt.assistant;

import android.app.*;
import android.os.Bundle;
import org.json.JSONObject;

public class DeviceCheck extends Instrumentation {
  private Bundle args;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    args = b;
    start();
  }

  private android.view.View find(android.view.View v, String text) {
    if (v instanceof android.widget.TextView
        && text.contentEquals(((android.widget.TextView) v).getText())) return v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View match = find(group.getChildAt(i), text);
        if (match != null) return match;
      }
    }
    return null;
  }

  private android.view.View description(android.view.View v, String text) {
    if (text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View match = description(group.getChildAt(i), text);
        if (match != null) return match;
      }
    }
    return null;
  }

  private void checkModelClose(Bundle result) throws Exception {
    long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
    while ((AssistantService.current == null || TvAccessibility.current == null)
        && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(100);
    if (AssistantService.current == null || TvAccessibility.current == null)
      throw new Exception("Services not bound");
    runOnMainSync(
        () -> {
          Bundle b = new Bundle();
          b.putString("source", "launch");
          AssistantService.current.showSession(b, 0);
        });
    boolean ready = false;
    deadline = android.os.SystemClock.elapsedRealtime() + 30000;
    while (android.os.SystemClock.elapsedRealtime() < deadline) {
      String[] state = {""};
      runOnMainSync(
          () -> {
            if (VoicePanel.active != null) state[0] = VoicePanel.active.diagnostics();
          });
      if (state[0].equals("Слушаю")) {
        ready = true;
        break;
      }
      android.os.SystemClock.sleep(100);
    }
    if (!ready || VoicePanel.active == null) throw new Exception("Live did not become ready");
    long start = android.os.SystemClock.elapsedRealtime();
    runOnMainSync(() -> VoicePanel.active.injectTestText("На сегодня заканчиваем наше общение"));
    deadline = start + 20000;
    while (VoicePanel.active != null && android.os.SystemClock.elapsedRealtime() < deadline)
      android.os.SystemClock.sleep(10);
    if (VoicePanel.active != null)
      throw new Exception("Model close did not finish within20 seconds");
    result.putLong("model_to_close_ms", android.os.SystemClock.elapsedRealtime() - start);
  }

  private boolean ducked(int uid) throws Exception {
    String audio = AdbRemote.shell(getTargetContext(), "dumpsys audio", 700);
    String[] section = audio.split("ducked players piids:", 2);
    if (section.length < 2) return false;
    String current = section[1].split("faded out players", 2)[0];
    return java.util.regex.Pattern.compile("uid:" + uid + " piids:\\s+\\d+")
        .matcher(current)
        .find();
  }

  public void onStart() {
    Bundle result = new Bundle();
    try {
      String mode = args.getString("mode", "status");
      String text = args.getString("text", "");
      if (mode.equals("audit")) {
        java.io.File file =
            new java.io.File(getTargetContext().getFilesDir(), "codex/tv-assistant/session.json");
        JSONObject saved =
            new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8"));
        org.json.JSONArray history = saved.getJSONArray("history"),
            bounded = new org.json.JSONArray();
        for (int i = Math.max(0, history.length() - 45); i < history.length(); i++) {
          JSONObject item = history.getJSONObject(i), row = new JSONObject();
          for (String key :
              new String[] {"type", "role", "phase", "name", "arguments", "content", "output"}) {
            if (!item.has(key)) continue;
            String value = item.opt(key).toString();
            row.put(key, value.substring(0, Math.min(1200, value.length())));
          }
          bounded.put(row);
        }
        result.putString("history", bounded.toString());
        org.json.JSONArray transcript = saved.optJSONArray("transcript"),
            finalTurns = new org.json.JSONArray();
        for (int i = transcript == null ? 0 : Math.max(0, transcript.length() - 12);
            transcript != null && i < transcript.length();
            i++) finalTurns.put(transcript.getJSONObject(i));
        result.putString("final_turns", finalTurns.toString());
        result.putString(
            "models",
            "fast="
                + ModelSettings.fast(getTargetContext())
                + " effort="
                + ModelSettings.reasoning(getTargetContext(), "discussion"));
        result.putBoolean(
            "duck",
            getTargetContext().getSharedPreferences("assistant", 0).getBoolean("duck", true));
      } else if (mode.equals("verify-actions")) {
        startActivitySync(
            new android.content.Intent(getTargetContext(), MainActivity.class)
                .putExtra("section", "SETTINGS")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (TvAccessibility.current == null
            && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(100);
        if (TvAccessibility.current == null) throw new Exception("Accessibility unavailable");
        TvTools tools = new TvTools(getTargetContext());
        JSONObject open =
            new JSONObject(tools.execute("open_app", new JSONObject().put("name", "YouTube")));
        if (!open.optString("confirmed_scope").equals("app_visible")
            || !open.getJSONObject("observation")
                .optString("package")
                .equals("com.google.android.youtube.tv"))
          throw new Exception("App opening not observed");
        result.putBoolean("app_open_observed", true);
        JSONObject missing =
            new JSONObject(
                tools.execute("open_app", new JSONObject().put("name", "TVGPT-MISSING-APP")));
        if (!missing.optString("status").equals("rejected"))
          throw new Exception("Nonexistent app incorrectly confirmed");
        result.putBoolean("missing_app_rejected", true);
        android.media.AudioManager audio =
            (android.media.AudioManager)
                getTargetContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        boolean originalMute = audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC);
        try {
          for (boolean enabled : new boolean[] {true, false}) {
            JSONObject mute =
                new JSONObject(tools.execute("mute", new JSONObject().put("enabled", enabled)));
            if (!mute.optString("confirmed_scope").equals("mute_readback")
                || audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC) != enabled)
              throw new Exception("Mute readback mismatch");
          }
          result.putBoolean("mute_readback", true);
        } finally {
          tools.execute("mute", new JSONObject().put("enabled", originalMute));
        }
        JSONObject pending =
            new JSONObject(
                tools.execute(
                    "wait_for_screen",
                    new JSONObject()
                        .put("package", "not.a.real.package")
                        .put("contains", "")
                        .put("timeout_ms", 300)));
        if (!pending.optString("status").equals("unconfirmed"))
          throw new Exception("Timeout must not become failure/success");
        result.putBoolean("unconfirmed_timeout", true);
        SessionStore.State state = SessionWorkspace.open(getTargetContext());
        result.putString(
            "answer",
            new Agent(getTargetContext(), state)
                .run("Найди грозу с громом и дождём в 4K для просмотра. Не запускай само видео."));
        boolean searched = false, visual = false, web = false;
        for (int i = 0; i < state.history.length(); i++) {
          JSONObject item = state.history.getJSONObject(i);
          if (item.optString("name").equals("search_app")) {
            JSONObject search = new JSONObject(item.getString("arguments"));
            searched |= search.optString("query").toLowerCase().contains("4k");
          }
          web |= item.optString("type").equals("web_search_call");
          visual |=
              item.optString("type").equals("function_call_output")
                  && (item.opt("output") instanceof org.json.JSONArray
                      || item.optString("output").equals("[Снимок экрана предыдущего действия]"));
        }
        if (!searched || web || !visual)
          throw new Exception("Search route, 4K preservation or visual observation missing");
        result.putBoolean("app_search_with_4k_and_visual", true);
      } else if (mode.equals("duck-check")) {
        startActivitySync(
            new android.content.Intent(getTargetContext(), MainActivity.class)
                .putExtra("section", "SETTINGS")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (TvAccessibility.current == null
            && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(100);
        if (TvAccessibility.current == null) throw new Exception("Accessibility unavailable");
        TvTools tools = new TvTools(getTargetContext());
        tools.execute("open_app", new JSONObject().put("name", "YouTube"));
        tools.execute("navigate", new JSONObject().put("direction", "select").put("count", 1));
        int uid =
            getTargetContext()
                .getPackageManager()
                .getApplicationInfo("com.google.android.youtube.tv", 0)
                .uid;
        if (!ActionWait.until(
            () ->
                Boolean.TRUE.equals(
                    PlaybackObservation.active(
                        AdbRemote.shell(getTargetContext(), "dumpsys audio", 700), uid)),
            12000,
            ActionWait.MONOTONIC)) throw new Exception("YouTube media did not start");
        android.media.AudioManager manager =
            (android.media.AudioManager)
                getTargetContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        int volume = manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        AudioDucking[] duck = {null};
        runOnMainSync(
            () -> {
              duck[0] = new AudioDucking(getTargetContext());
              duck[0].begin();
            });
        try {
          if (!ActionWait.until(() -> ducked(uid), 5000, ActionWait.MONOTONIC))
            throw new Exception("Background player not ducked");
          result.putBoolean("other_app_ducked", true);
          AdbRemote.key(getTargetContext(), "pause");
          AdbRemote.key(getTargetContext(), "play");
          if (!ActionWait.until(() -> ducked(uid), 5000, ActionWait.MONOTONIC))
            throw new Exception("Ducking lost after media focus changed");
          result.putBoolean("duck_after_play", true);
          if (manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) != volume)
            throw new Exception("Shared stream volume changed");
          result.putBoolean("shared_volume_unchanged", true);
        } finally {
          runOnMainSync(() -> duck[0].close());
          AdbRemote.key(getTargetContext(), "pause");
        }
      } else if (mode.equals("model-ui")) {
        ModelUiChecks.run(this, result, args.getString("restore_effort"));
      } else if (mode.equals("background-media")) {
        BackgroundMediaChecks.run(this, result);
      } else if (mode.equals("fast-benchmark")) {
        getTargetContext()
            .startActivity(
                new android.content.Intent(getTargetContext(), MainActivity.class)
                    .putExtra("section", "SETTINGS")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        FastTransportChecks.benchmark(getTargetContext(), result);
      } else if (mode.equals("fast-transport")) {
        FastTransportChecks.run(getTargetContext(), result);
      } else if (mode.equals("hermes-fast")) {
        HermesFastChecks.run(getTargetContext(), result);
      } else if (mode.equals("fast-wire")) {
        FastWireChecks.run(getTargetContext(), result);
      } else if (mode.equals("tier-check")) {
        boolean originalFast = ModelSettings.fast(getTargetContext());
        String originalEffort = ModelSettings.reasoning(getTargetContext(), "discussion");
        try {
          for (boolean fast : new boolean[] {false, true}) {
            ModelSettings.fast(getTargetContext(), fast);
            SessionStore.State state = SessionWorkspace.open(getTargetContext());
            String answer =
                new Agent(getTargetContext(), state)
                    .run("Ответь одним словом: сколько будет два плюс два? Без инструментов.");
            if (answer.isEmpty()) throw new Exception("No tier response");
            result.putBoolean(fast ? "fast_request_completed" : "standard_request_completed", true);
          }
        } finally {
          ModelSettings.fast(getTargetContext(), originalFast);
          ModelSettings.reasoning(getTargetContext(), "discussion", originalEffort);
        }
      } else if (mode.equals("catalog")) {
        java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
        String[] catalog = {"{}"};
        runOnMainSync(
            () ->
                ModelCatalogue.load(
                    getTargetContext(),
                    value -> {
                      if (value.contains("\"models\"")) {
                        catalog[0] = value;
                        loaded.countDown();
                      }
                    }));
        if (!loaded.await(20, java.util.concurrent.TimeUnit.SECONDS))
          throw new Exception("Catalog unavailable");
        result.putString(
            "live_models",
            local.tvgpt.assistant.settings.ModelOptions.parse(
                    catalog[0], true, ModelSettings.live(getTargetContext()))
                .toString());
        result.putString(
            "discussion_reasoning",
            local.tvgpt.assistant.settings.ModelOptions.reasoning(
                    catalog[0], ModelSettings.discussion(getTargetContext()))
                .toString());
        result.putString(
            "selected_reasoning", ModelSettings.reasoning(getTargetContext(), "discussion"));
        result.putBoolean("fast", ModelSettings.fast(getTargetContext()));
        org.json.JSONArray catalogModels = new JSONObject(catalog[0]).getJSONArray("models");
        for (int i = 0; i < catalogModels.length(); i++) {
          JSONObject m = catalogModels.getJSONObject(i);
          if (ModelSettings.discussion(getTargetContext()).equals(m.optString("slug")))
            result.putString("discussion_service_tiers", String.valueOf(m.opt("service_tiers")));
        }
        result.putInt(
            "knowledge_count",
            new local.tvgpt.assistant.data.KnowledgeRepository(getTargetContext()).list().length());
      } else if (mode.equals("web-protocol")) {
        JSONObject request =
            new JSONObject()
                .put("model", ModelSettings.discussion(getTargetContext()))
                .put("store", false)
                .put("stream", true)
                .put(
                    "tools",
                    new org.json.JSONArray().put(new JSONObject().put("type", "web_search")))
                .put("tool_choice", new JSONObject().put("type", "web_search"))
                .put(
                    "instructions",
                    "Find the answer using web search. Give one short factual sentence.")
                .put(
                    "input",
                    new org.json.JSONArray()
                        .put(
                            new JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    "Find on developer.android.com which Android class provides"
                                        + " voice assistant interaction.")));
        Http.Reply reply =
            Http.post(
                "https://chatgpt.com/backend-api/codex/responses",
                request.toString(),
                "application/json",
                LiveConnection.headers(new AccountStore(getTargetContext()).fresh()));
        result.putInt("http", reply.code);
        StringBuilder shapes = new StringBuilder();
        LiveStartupTrace trace = new LiveStartupTrace();
        for (String line : reply.body.split("\n")) {
          if (!line.startsWith("data:")) continue;
          try {
            JSONObject event = new JSONObject(line.substring(5).trim());
            String shape = trace.describe("probe", event);
            if (shape != null) shapes.append(shape).append("\n");
          } catch (Exception ignored) {
          }
        }
        result.putString("event_shapes", shapes.toString());
      } else if (mode.equals("web-search")) {
        SessionStore.State state = SessionWorkspace.open(getTargetContext());
        String answer =
            new Agent(getTargetContext(), state)
                .run(
                    "Найди в интернете на официальном сайте Android Developers, какой класс Android"
                        + " отвечает за голосового помощника. Обязательно выполни веб-поиск, не"
                        + " отвечай по памяти. Ответь одной фразой.");
        boolean searched = false;
        for (int i = 0; i < state.history.length(); i++) {
          JSONObject entry = state.history.optJSONObject(i);
          searched |=
              entry != null
                  && entry.optString("type").equals("web_search_call")
                  && entry.optString("status").equals("completed");
        }
        if (!searched || answer.isEmpty()) {
          result.putString("probe_answer", answer.substring(0, Math.min(700, answer.length())));
          StringBuilder types = new StringBuilder();
          for (int i = 0; i < state.history.length(); i++) {
            JSONObject entry = state.history.optJSONObject(i);
            if (entry != null)
              types
                  .append(entry.optString("type"))
                  .append(":")
                  .append(entry.optString("status"))
                  .append(";");
          }
          result.putString("output_types", types.toString());
          throw new Exception("No completed web search");
        }
        result.putBoolean("web_search_completed", true);
        result.putBoolean("web_sources_received", !WebSources.extract(state.history).isEmpty());
      } else if (mode.equals("knowledge-confirmation")) {
        String marker = "tvgpt-check-" + java.util.UUID.randomUUID();
        local.tvgpt.assistant.data.KnowledgeRepository repository =
            new local.tvgpt.assistant.data.KnowledgeRepository(getTargetContext());
        try {
          String confirmation =
              new Agent(getTargetContext())
                  .run(
                      "Сохрани в общие знания тестовую заметку с заголовком «Проверка сохранения»"
                          + " и точным текстом «"
                          + marker
                          + "». Это явный запрос на сохранение.");
          org.json.JSONArray notes = repository.list();
          boolean found = false;
          for (int i = 0; i < notes.length(); i++)
            found |= notes.getJSONObject(i).optString("text").contains(marker);
          if (!found || confirmation.isEmpty())
            throw new Exception("Knowledge confirmation failed");
          result.putBoolean("knowledge_saved_and_confirmed", true);
        } finally {
          org.json.JSONArray notes = repository.list();
          for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.getJSONObject(i);
            if (note.optString("text").contains(marker))
              repository.delete(note.getLong("id"), note.getLong("revision"));
          }
        }
      } else if (mode.equals("features")) {
        FeatureChecks.database(getTargetContext(), result);

      } else if (mode.equals("bundled-voices")) {
        BundledVoiceChecks.run(this, result);
      } else if (mode.equals("preview")) {
        String voice = args.getString("voice", "cove");
        java.util.concurrent.atomic.AtomicBoolean played =
            new java.util.concurrent.atomic.AtomicBoolean();
        VoicePreview[] preview = {null};
        runOnMainSync(
            () -> {
              preview[0] = new VoicePreview(getTargetContext());
              preview[0].start(
                  voice,
                  local.tvgpt.assistant.settings.SpeechPreset.NORMAL,
                  new VoicePreview.Listener() {
                    public void status(String value) {
                      android.util.Log.i("TVGPTPreview", value);
                    }

                    public void played(String id) {
                      played.set(true);
                    }
                  });
            });
        long deadline = android.os.SystemClock.elapsedRealtime() + 19000;
        while (!played.get() && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(100);
        java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
        runOnMainSync(() -> preview[0].stop(released::countDown));
        if (!released.await(5, java.util.concurrent.TimeUnit.SECONDS))
          throw new Exception("preview release timeout");
        result.putBoolean("preview_audio_received", played.get());
        result.putBoolean("preview_released", !VoicePreview.isActive());
      } else if (mode.equals("audio-input")) {
        android.media.AudioManager manager =
            (android.media.AudioManager)
                getTargetContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        int original = manager.getMode();
        try {
          for (int route :
              new int[] {
                android.media.AudioManager.MODE_NORMAL,
                android.media.AudioManager.MODE_IN_COMMUNICATION
              }) {
            manager.setMode(route);
            for (int source :
                new int[] {
                  android.media.MediaRecorder.AudioSource.MIC,
                  android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
                }) {
              int rate = Integer.parseInt(args.getString("sample_rate", "48000"));
              int minimum =
                  android.media.AudioRecord.getMinBufferSize(
                      rate,
                      android.media.AudioFormat.CHANNEL_IN_MONO,
                      android.media.AudioFormat.ENCODING_PCM_16BIT);
              android.media.AudioRecord record =
                  new android.media.AudioRecord(
                      source,
                      rate,
                      android.media.AudioFormat.CHANNEL_IN_MONO,
                      android.media.AudioFormat.ENCODING_PCM_16BIT,
                      Math.max(minimum * 4, rate));
              long energy = 0, count = 0;
              int peak = 0;
              try {
                record.startRecording();
                short[] samples = new short[4800];
                for (int part = 0; part < 15; part++) {
                  int n = record.read(samples, 0, samples.length);
                  if (n < 0) throw new Exception("Record read failed: " + n);
                  for (int i = 0; i < n; i++) {
                    int value = samples[i];
                    energy += (long) value * value;
                    peak = Math.max(peak, Math.abs(value));
                  }
                  count += n;
                }
              } finally {
                try {
                  record.stop();
                } finally {
                  record.release();
                }
              }
              result.putString(
                  "mode_" + route + "_source_" + source,
                  "rms=" + Math.sqrt(energy / (double) Math.max(1, count)) + " peak=" + peak);
            }
          }
        } finally {
          manager.setMode(original);
        }
      } else if (mode.equals("harness-agent")) {
        startActivitySync(
            new android.content.Intent(getTargetContext(), MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        long bindingDeadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (TvAccessibility.current == null
            && android.os.SystemClock.elapsedRealtime() < bindingDeadline)
          android.os.SystemClock.sleep(100);
        if (TvAccessibility.current == null)
          throw new Exception("Instrumentation accessibility did not rebind");
        SessionStore.State isolated = new SessionStore.State();
        isolated.id = java.util.UUID.randomUUID().toString();
        isolated.history = new org.json.JSONArray();
        isolated.transcript = new org.json.JSONArray();
        TaskHarness harness = new TaskHarness();
        TaskHarness.Task task =
            harness.admit("device-task", "device-user", text, new org.json.JSONArray());
        Agent worker =
            new Agent(
                getTargetContext(),
                isolated,
                () -> result.putBoolean("end_tool", true),
                harness,
                task);
        String answer = worker.run(text);
        result.putString("outcome", worker.outcome(answer));
        result.putInt("history_items", isolated.history.length());
        org.json.JSONArray trace = new org.json.JSONArray();
        for (int i = 0; i < isolated.history.length(); i++) {
          JSONObject item = isolated.history.getJSONObject(i);
          if (item.optString("type").equals("function_call"))
            trace.put(
                new JSONObject()
                    .put("name", item.optString("name"))
                    .put("id", item.optString("call_id"))
                    .put("args", item.optString("arguments")));
          else if (item.optString("type").equals("function_call_output")) {
            String value =
                item.opt("output") instanceof org.json.JSONArray
                    ? "[visual content]"
                    : item.optString("output");
            trace.put(
                new JSONObject()
                    .put("receipt", item.optString("call_id"))
                    .put("result", value.substring(0, Math.min(1600, value.length()))));
          }
        }
        result.putString("tool_trace", trace.toString());
      } else if (mode.equals("model-close-ui")) {
        int cycles = Math.max(1, Math.min(3, Integer.parseInt(args.getString("cycles", "1"))));
        for (int cycle = 0; cycle < cycles; cycle++) {
          Bundle sample = new Bundle();
          checkModelClose(sample);
          result.putLong("close_ms_" + cycle, sample.getLong("model_to_close_ms"));
        }
        result.putInt("cycles", cycles);
      } else if (mode.equals("close-ui")) {
        Activity activity =
            startActivitySync(
                new android.content.Intent(getTargetContext(), MainActivity.class)
                    .putExtra("setup", true)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while ((AssistantService.current == null || TvAccessibility.current == null)
            && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(100);
        if (AssistantService.current == null || TvAccessibility.current == null)
          throw new Exception("Services not bound");
        runOnMainSync(
            () -> {
              Bundle b = new Bundle();
              b.putString("source", "launch");
              AssistantService.current.showSession(b, 0);
            });
        deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
          String[] state = {""};
          runOnMainSync(
              () -> {
                if (VoicePanel.active != null) state[0] = VoicePanel.active.diagnostics();
              });
          if (state[0].equals("Слушаю")) break;
          android.os.SystemClock.sleep(100);
        }
        if (VoicePanel.active == null) throw new Exception("No Live panel");
        String goodbyeEvent =
            new JSONObject()
                .put("type", "delegation.created")
                .put(
                    "item",
                    new JSONObject()
                        .put("id", "close-ui-probe")
                        .put("type", "delegation")
                        .put("target", "client")
                        .put(
                            "content",
                            new org.json.JSONArray()
                                .put(
                                    new JSONObject()
                                        .put("type", "input_text")
                                        .put("text", "На сегодня заканчиваем наше общение"))))
                .toString();
        long start = android.os.SystemClock.elapsedRealtime();
        runOnMainSync(() -> VoicePanel.active.injectTestEvent(goodbyeEvent));
        deadline = start + 20000;
        while (VoicePanel.active != null && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(10);
        if (VoicePanel.active != null) throw new Exception("Closing did not release panel");
        result.putLong("delegation_close_ms", android.os.SystemClock.elapsedRealtime() - start);
        runOnMainSync(
            () -> {
              android.view.View label =
                  find(activity.getWindow().getDecorView(), "Локальный пульт");
              if (label == null) throw new AssertionError("No remote row");
              ((android.view.View) label.getParent().getParent()).performClick();
            });
        deadline = android.os.SystemClock.elapsedRealtime() + 15000;
        boolean[] paired = {false};
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
          runOnMainSync(
              () ->
                  paired[0] =
                      find(
                              activity.getWindow().getDecorView(),
                              "Подключён — управление кнопками доступно")
                          != null);
          if (paired[0]) break;
          android.os.SystemClock.sleep(100);
        }
        if (!paired[0]) throw new Exception("Remote pairing did not report success");
        result.putBoolean("remote_paired", true);
        runOnMainSync(
            () -> {
              activity.onBackPressed();
              if (!activity.isFinishing()) activity.onBackPressed();
            });
        if (!activity.isFinishing()) throw new Exception("Back did not finish settings");
        result.putBoolean("back_exits", true);
      } else if (mode.equals("digest-inspect")) {
        local.tvgpt.assistant.data.DigestRepository repo =
            new local.tvgpt.assistant.data.DigestRepository(getTargetContext());
        String id = args.getString("digest_id");
        org.json.JSONArray
            articles = local.tvgpt.assistant.digests.DigestSlides.latest(repo.cards(id)),
            checks = new org.json.JSONArray();
        for (int i = 0; i < articles.length(); i++) {
          JSONObject a = articles.getJSONObject(i);
          java.io.File photo = new java.io.File(a.optString("image_path"));
          android.graphics.BitmapFactory.Options bounds =
              new android.graphics.BitmapFactory.Options();
          bounds.inJustDecodeBounds = true;
          if (photo.isFile())
            android.graphics.BitmapFactory.decodeFile(photo.getAbsolutePath(), bounds);
          checks.put(
              new JSONObject()
                  .put("title", a.optString("title"))
                  .put("url", a.optString("url"))
                  .put("image_kind", a.optString("image_kind"))
                  .put("image_bytes", photo.isFile() ? photo.length() : 0)
                  .put("width", bounds.outWidth)
                  .put("height", bounds.outHeight));
        }
        result.putString(
            "result",
            new JSONObject()
                .put("job", repo.latestJob(id))
                .put("articles", checks)
                .put(
                    "imagegen",
                    getTargetContext()
                        .getSharedPreferences("digests", 0)
                        .getString("imagegen", "unconfirmed"))
                .toString());
      } else if (mode.equals("digest-imagegen-request")) {
        android.content.Context c = getTargetContext();
        String id = args.getString("digest_id");
        JSONObject article =
            local.tvgpt.assistant.digests.DigestSlides.latest(
                    new local.tvgpt.assistant.data.DigestRepository(c).cards(id))
                .getJSONObject(0);
        String skill = AssistantSkills.app(c, "imagegen");
        if (!skill.contains("name: imagegen")) throw new AssertionError("imagegen skill missing");
        JSONObject job =
            new local.tvgpt.assistant.digests.DigestCommands(c)
                .execute(
                    "digest_cover_generate",
                    new JSONObject()
                        .put("digest_id", id)
                        .put("article_id", article.getString("id"))
                        .put("operation_id", java.util.UUID.randomUUID().toString())
                        .put(
                            "prompt",
                            "Горизонтальная редакционная иллюстрация для заставки ТВ, без текста"));
        Bundle progress = new Bundle();
        progress.putString("job_id", job.getString("job_id"));
        sendStatus(0, progress);
        long deadline = android.os.SystemClock.elapsedRealtime() + 220000;
        local.tvgpt.assistant.data.DigestRepository repo =
            new local.tvgpt.assistant.data.DigestRepository(c);
        JSONObject observed;
        do {
          observed = repo.job(job.getString("job_id"));
          String state = observed.optString("state");
          if (state.equals("ready")
              || state.equals("failed")
              || state.equals("unconfirmed")
              || state.equals("cancelled")) break;
          android.os.SystemClock.sleep(250);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        JSONObject cover = repo.persistedArticles(job.getString("job_id")).getJSONObject(0);
        android.graphics.BitmapFactory.Options dimensions =
            new android.graphics.BitmapFactory.Options();
        dimensions.inJustDecodeBounds = true;
        java.io.File image = new java.io.File(cover.optString("image_path"));
        if (image.isFile())
          android.graphics.BitmapFactory.decodeFile(image.getAbsolutePath(), dimensions);
        result.putString(
            "result",
            job.put("digest_model", ModelSettings.digests(c))
                .put("skill_loaded", true)
                .put("state", observed.optString("state"))
                .put("reason", observed.optString("reason"))
                .put("cover_kind", cover.optString("image_kind"))
                .put("width", dimensions.outWidth)
                .put("height", dimensions.outHeight)
                .put("image_bytes", image.isFile() ? image.length() : 0)
                .put(
                    "imagegen_status",
                    c.getSharedPreferences("digests", 0).getString("imagegen", "unconfirmed"))
                .put(
                    "imagegen_reason",
                    c.getSharedPreferences("digests", 0).getString("imagegen_reason", ""))
                .toString());
      } else if (mode.equals("digest-command")) {
        result.putString(
            "result",
            new local.tvgpt.assistant.digests.DigestCommands(getTargetContext())
                .execute(args.getString("name"), new JSONObject(args.getString("arguments", "{}")))
                .toString());
      } else if (mode.equals("agent-news")) {
        result.putString("result", new Agent(getTargetContext()).run(text));
      } else if (mode.equals("agent") || mode.equals("controls")) {
        startActivitySync(
            new android.content.Intent(getTargetContext(), MainActivity.class)
                .putExtra("section", "SETTINGS")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (TvAccessibility.current == null
            && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(100);
        if (TvAccessibility.current == null)
          throw new Exception("Accessibility did not bind to instrumentation process");
        if (mode.equals("controls")) {
          TvTools tools = new TvTools(getTargetContext());
          result.putString("before", tools.execute("read_screen", new JSONObject()));
          result.putString(
              "back", tools.execute("navigate", new JSONObject().put("direction", "back")));
          android.os.SystemClock.sleep(2000);
          result.putString("after", tools.execute("read_screen", new JSONObject()));
          result.putString("click", tools.execute("click", new JSONObject().put("text", "Поиск")));
          android.os.SystemClock.sleep(1500);
          result.putString("clicked_screen", tools.execute("read_screen", new JSONObject()));
        } else result.putString("result", new Agent(getTargetContext()).run(text));
      } else if (mode.equals("remote")) {
        for (String key : args.getString("keys", "up,select").split(",")) {
          result.putString(key, AdbRemote.key(getTargetContext(), key));
          android.os.SystemClock.sleep(400);
        }
      } else if (mode.equals("adb"))
        result.putString("result", AdbRemote.shell(getTargetContext(), "echo TVGPT_REMOTE_READY"));
      else if (mode.equals("tool")) {
        long deadline = android.os.SystemClock.elapsedRealtime() + 15000;
        while (TvAccessibility.current == null
            && android.os.SystemClock.elapsedRealtime() < deadline)
          android.os.SystemClock.sleep(100);
        result.putString(
            "result",
            new TvTools(getTargetContext())
                .execute(
                    args.getString("name"), new JSONObject(args.getString("arguments", "{}"))));
      } else if (mode.equals("live")
          || mode.equals("home-live")
          || mode.equals("physical-home-live")
          || mode.equals("model-intent-close")) {
        long readyDeadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while ((AssistantService.current == null || TvAccessibility.current == null)
            && android.os.SystemClock.elapsedRealtime() < readyDeadline)
          android.os.SystemClock.sleep(100);
        if (AssistantService.current == null || TvAccessibility.current == null)
          throw new Exception("Assistant services did not rebind");
        runOnMainSync(
            () ->
                getTargetContext()
                    .startActivity(
                        new android.content.Intent(getTargetContext(), MainActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)));
        runOnMainSync(
            () -> {
              Bundle invocation = new Bundle();
              invocation.putString("source", "launch");
              AssistantService.current.showSession(invocation, 0);
            });
        long startedAt = android.os.SystemClock.elapsedRealtime();
        boolean connected = false;
        long deadline = startedAt + 45000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
          String[] status = {""};
          runOnMainSync(
              () -> {
                if (VoicePanel.active != null) status[0] = VoicePanel.active.diagnostics();
              });
          if (status[0].equals("Голосовой канал подключён") || status[0].equals("Слушаю")) {
            connected = true;
            break;
          }
          android.os.SystemClock.sleep(250);
        }
        if (VoicePanel.active == null || !connected) throw new Exception("Live did not connect");
        result.putLong("startup_ms", android.os.SystemClock.elapsedRealtime() - startedAt);
        if (mode.equals("home-live") || mode.equals("physical-home-live")) {
          VoicePanel original = VoicePanel.active;
          if (mode.equals("physical-home-live")) {
            AdbRemote.shell(getTargetContext(), "input keyevent 3", 2000);
          } else
            new TvTools(getTargetContext())
                .execute("navigate", new JSONObject().put("direction", "home"));
          android.os.SystemClock.sleep(1500);
          if (VoicePanel.active != original)
            throw new Exception("HOME closed the voice conversation");
          result.putBoolean("home_preserved_live", true);
          long closing = android.os.SystemClock.elapsedRealtime();
          runOnMainSync(original::requestEnd);
          if (!ActionWait.until(
              () -> {
                boolean[] gone = {false};
                runOnMainSync(() -> gone[0] = VoicePanel.active == null);
                return gone[0];
              },
              2500,
              ActionWait.MONOTONIC)) throw new Exception("Native voice close did not finish");
          result.putLong("native_close_ms", android.os.SystemClock.elapsedRealtime() - closing);
        } else {
          runOnMainSync(() -> VoicePanel.active.injectTestText(text));
          if (mode.equals("model-intent-close")) {
            long requested = android.os.SystemClock.elapsedRealtime();
            if (!ActionWait.until(
                () -> {
                  boolean[] ended = {false};
                  runOnMainSync(() -> ended[0] = VoicePanel.active == null);
                  return ended[0];
                },
                15000,
                ActionWait.MONOTONIC))
              throw new Exception("Live did not delegate end of conversation");
            result.putLong(
                "model_decision_and_close_ms",
                android.os.SystemClock.elapsedRealtime() - requested);
          } else android.os.SystemClock.sleep(20000);
        }
        runOnMainSync(
            () ->
                result.putString(
                    "result",
                    VoicePanel.active == null ? "Panel closed" : VoicePanel.active.diagnostics()));
      } else
        runOnMainSync(
            () ->
                result.putString(
                    "result",
                    VoicePanel.active == null
                        ? "No active panel"
                        : VoicePanel.active.diagnostics()));
      finish(Activity.RESULT_OK, result);
    } catch (Exception e) {
      result.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage());
      Throwable cause = e.getCause();
      int depth = 0;
      while (cause != null && depth < 3) {
        result.putString(
            "cause_" + depth, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        cause = cause.getCause();
        depth++;
      }
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
