package local.tvgpt.assistant;

import android.content.Context;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

final class LiveConnection {
  interface Listener {
    void answer(String sdp);

    void status(String text);

    void transcript(String role, String text);

    void fatal(String message);

    default void endRequested() {}
  }

  private final Context context;
  private final Listener listener;
  private final Agent agent;
  private final SpeechStop speechStop = new SpeechStop();
  private final ScheduledExecutorService controls = Executors.newSingleThreadScheduledExecutor();
  private final ScheduledExecutorService stopTimer = Executors.newSingleThreadScheduledExecutor();
  private ScheduledFuture<?> pendingStop;
  private String lastMedia = "";
  private long lastMediaAt;
  private final java.util.concurrent.atomic.AtomicBoolean ending =
      new java.util.concurrent.atomic.AtomicBoolean();
  private final SessionStore.State workspace;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final OkHttpClient client =
      new OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.MILLISECONDS)
          .build();
  private final Set<String> eventTypes = Collections.synchronizedSet(new HashSet<>());
  private final LiveStartupTrace startupTrace = new LiveStartupTrace();
  private final Set<String> delegations = Collections.synchronizedSet(new HashSet<>());
  private volatile boolean closed;
  private WebSocket socket;
  private final String archiveCall = UUID.randomUUID().toString();
  private String heard = "", spoken = "";

  LiveConnection(Context c, Listener l) {
    context = c.getApplicationContext();
    listener = l;
    workspace = SessionWorkspace.open(context);
    agent =
        new Agent(
            context,
            workspace,
            () -> {
              requestLocalEnd("agent_end_tool");
            });
  }

  static Map<String, String> headers(JSONObject account) throws Exception {
    Map<String, String> h = new LinkedHashMap<>();
    h.put("Authorization", "Bearer " + account.getString("access_token"));
    h.put("ChatGPT-Account-ID", AccountStore.accountId(account));
    h.put("originator", "tvgpt");
    h.put("OpenAI-Alpha", "quicksilver=v2");
    return h;
  }

  void connect(String sdp) {
    executor.submit(
        () -> {
          try {
            listener.status("Подключаю GPT-Live…");
            JSONObject account = new AccountStore(context).fresh();
            Map<String, String> h = headers(account);
            h.put("session-id", workspace.id);
            h.put("thread-id", workspace.id);
            h.put("x-session-id", UUID.randomUUID().toString());
            JSONObject session = sessionDescription();
            Http.Reply reply =
                Http.post(
                    "https://chatgpt.com/backend-api/codex/realtime/calls?intent=quicksilver&architecture=avas",
                    new JSONObject().put("sdp", sdp).put("session", session).toString(),
                    "application/json",
                    h);
            reply.requireSuccess();
            String id = Protocol.callId(reply.location == null ? "" : reply.location);
            if (closed) return;
            Request.Builder request =
                new Request.Builder().url("wss://api.openai.com/v1/live/" + id);
            for (Map.Entry<String, String> e : h.entrySet())
              request.header(e.getKey(), e.getValue());
            socket =
                client.newWebSocket(
                    request.build(),
                    new WebSocketListener() {
                      @Override
                      public void onOpen(WebSocket ws, Response r) {
                        if (closed) {
                          ws.close(1000, "closed");
                          return;
                        }
                        listener.status("Голосовой канал подключён");
                      }

                      @Override
                      public void onMessage(WebSocket ws, String text) {
                        event(text, "sideband");
                      }

                      @Override
                      public void onFailure(WebSocket ws, Throwable t, Response r) {
                        if (!closed)
                          listener.fatal(
                              "Не подключился канал управления голосом"
                                  + (r == null ? "" : " (HTTP " + r.code() + ")"));
                      }

                      @Override
                      public void onClosed(WebSocket ws, int code, String reason) {
                        if (!closed) listener.fatal("Голосовой сеанс завершён");
                      }
                    });
            listener.answer(reply.body);
          } catch (Exception e) {
            if (!closed) listener.fatal(DeviceLogin.safeError(e));
          }
        });
  }

  private JSONObject sessionDescription() throws Exception {
    JSONObject session = new JSONObject();
    session.put("model", ModelSettings.live(context));
    session.put(
        "instructions",
        "Ты голосовой помощник телевизора. Говори по-русски, кратко и естественно. Телевизор —"
            + " место отдыха. Общайся как доброжелательный собеседник: отвечай и на обычные"
            + " вопросы, поддерживай разговор, учитывай уточнения и не своди общение к командам"
            + " телевизору. Говори естественно, без шаблонных подтверждений. Не озвучивай"
            + " технические подробности, промежуточные шаги и работу инструментов, если"
            + " пользователь явно не спросил. Перед любым поручением один раз кратко подтверди его"
            + " естественной фразой и сразу делегируй само поручение в этом же ходе. Подтверждение"
            + " не заменяет делегирование. Передавай цель, все ограничения и важные уточнения, а не"
            + " только фразу «сейчас сделаю». Затем жди результат. Не объявляй ни успех, ни ошибку"
            + " до результата исполнителя. При подключении молча жди запроса: приветствие и слова"
            + " «да, слушаю» не нужны, приложение играет короткий сигнал. Когда пользователь просит"
            + " открыть приложение, управлять телевизором или выяснить что на экране, передай"
            + " поручение клиенту через делегирование. Если пользователь прощается или просит"
            + " закончить сеанс, немедленно делегируй клиенту просьбу «Завершить текущий голосовой"
            + " разговор». Не произноси эту просьбу вслух и не читай технические команды:"
            + " приложение завершит разговор без прощальной речи. Не заявляй об успешном действии"
            + " без результата исполнителя. Вопросы о текущих событиях, погоде и явный запрос"
            + " поиска в интернете передавай исполнителю: у него есть web_search. Перед поручением"
            + " можно один раз коротко сказать «Сейчас посмотрю» или похожую естественную фразу. Не"
            + " повторяй её и не озвучивай технические шаги или длинные URL. Источники сохраняются"
            + " в истории разговора. Поиск контента для просмотра или прослушивания всегда"
            + " делегируй как задачу найти в подходящем установленном приложении; исполнитель"
            + " проверит доступные сервисы. Не подменяй его веб-поиском. Если приложения нет, можно"
            + " искать в интернете. Справочные вопросы о выходе фильма/дате/фактах можно сразу"
            + " поручать как веб-поиск. Сохраняй ограничения исходного запроса при уточнениях"
            + " (например 4K). Не говори «включил», «нашёл» или «не удалось» до результата"
            + " исполнителя и проверки; промежуточное подтверждение — только намерение.");
    session.put(
        "audio",
        new JSONObject()
            .put(
                "output",
                new JSONObject()
                    .put(
                        "voice",
                        new local.tvgpt.assistant.settings.AssistantPreferences(context).voice())));
    session.put("delegation", new JSONObject().put("type", "client").put("ack_filler", true));
    android.util.Log.i("TVGPTLiveDiag", "startup context=fresh");
    session.put(
        "instructions",
        session.getString("instructions")
            + " Знания, напоминания и таймеры: всегда делегируй чтение и изменение клиенту. Не"
            + " обещай сохранить или напомнить без результата инструмента. Нечёткое время"
            + " уточняй.\n"
            + ConversationTools.MEMORY_INSTRUCTIONS
            + " "
            + new local.tvgpt.assistant.settings.AssistantPreferences(context)
                .preset()
                .instructions);
    return session;
  }

  void event(String raw) {
    event(raw, "rtc");
  }

  private void event(String raw, String origin) {
    if (closed) return;
    try {
      JSONObject event = new JSONObject(raw);
      String diagnostic = startupTrace.describe(origin, event);
      if (diagnostic != null) android.util.Log.i("TVGPTLiveDiag", diagnostic);
      if ("sideband".equals(origin)) {
        String input = startupTrace.inputAudio(event);
        if (input != null) android.util.Log.i("TVGPTLiveDiag", input);
      }
      String type = event.optString("type");
      long now = android.os.SystemClock.uptimeMillis();
      if (speechStop.accept(event, now)) {
        requestLocalEnd("final_user_goodbye");
        return;
      }
      if (type.equals("input_transcript.added")
          || type.equals("session.input_transcript.delta")
          || type.equals("turn.done")) scheduleSpeechStop(now);
      if (type.equals("error")) {
        JSONObject problem = event.optJSONObject("error");
        if (problem != null)
          android.util.Log.w(
              "TVGPTLive",
              "server error code="
                  + problem.optString("code")
                  + " param="
                  + problem.optString("param"));
      }
      if (eventTypes.size() < 60 && eventTypes.add(type))
        android.util.Log.i("TVGPTLive", "received type " + type);
      transcriptEvent(event, type);
      if (type.equals("delegation.created") || type.equals("turn.done") || type.equals("error"))
        android.util.Log.i("TVGPTLive", "event " + type);
      String text = LiveProtocol.delegationText(event);
      if (text != null) {
        if (LiveProtocol.localEndDelegation(text)) {
          android.util.Log.i("TVGPTSession", "Local end delegation: no agent request");
          requestLocalEnd("live_end_delegation");
          return;
        }
        String id = event.getJSONObject("item").getString("id");
        if (delegations.add(id)) {
          String action = LiveProtocol.localMediaAction(text);
          if (action != null) controls.execute(() -> mediaCommand(id, action));
          else if (!ending.get()) executor.submit(() -> delegate(id, text));
        }
      } else if (type.equals("turn.done")) {
        recordTurn(event.optJSONObject("turn"), event.optString("event_id"));
      } else if (type.equals("error")) listener.fatal("Голосовой сервер сообщил об ошибке");
      else if (type.equals("session.closed")) listener.fatal("Голосовой сеанс завершён сервером");
    } catch (Exception ignored) {
    }
  }

  private void transcriptEvent(JSONObject event, String type) {
    if (type.equals("session.input_transcript.delta")) {
      heard = (heard + event.optString("delta"));
      if (heard.length() > 1500) heard = heard.substring(heard.length() - 1500);
      listener.transcript("user", heard);
    }
    if (type.equals("session.output_transcript.delta")) {
      spoken = (spoken + event.optString("delta"));
      if (spoken.length() > 1500) spoken = spoken.substring(spoken.length() - 1500);
      listener.transcript("assistant", spoken);
    }
    if (type.equals("input_transcript.added") || type.equals("output_transcript.added")) {
      JSONObject item = event.optJSONObject("item");
      if (item != null)
        listener.transcript(
            type.startsWith("input") ? "user" : "assistant", item.optString("text"));
    }
  }

  private synchronized void scheduleSpeechStop(long now) {
    if (pendingStop != null) {
      pendingStop.cancel(false);
      pendingStop = null;
    }
    long delay = speechStop.delay(now);
    if (delay >= 0 && !closed && !ending.get())
      pendingStop =
          stopTimer.schedule(
              () -> {
                if (speechStop.due(android.os.SystemClock.uptimeMillis()))
                  requestLocalEnd("stable_partial_goodbye");
              },
              delay,
              TimeUnit.MILLISECONDS);
  }

  private void requestLocalEnd(String reason) {
    if (closed || !ending.compareAndSet(false, true)) return;
    android.util.Log.i(
        "TVGPTSession", "Local close requested; reason=" + reason + "; no agent round");
    agent.cancel();
    listener.endRequested();
  }

  private void mediaCommand(String id, String action) {
    if (closed || ending.get()) return;
    try {
      String result =
          new TvTools(context).execute("media_control", new JSONObject().put("action", action));
      if (id != null) append(id, result);
    } catch (Exception error) {
      android.util.Log.w(
          "TVGPTControl", "Media command failed: " + error.getClass().getSimpleName());
    }
  }

  private void recordTurn(JSONObject turn, String rootEventId) {
    if (turn != null) {
      String role = turn.optString("role"), transcript = turn.optString("transcript");
      listener.transcript(role, transcript);
      if (role.equals("user")) {
        String action = LiveProtocol.localMediaAction(transcript);
        long now = android.os.SystemClock.uptimeMillis();
        if (action != null && (!action.equals(lastMedia) || now - lastMediaAt > 1000)) {
          lastMedia = action;
          lastMediaAt = now;
          controls.execute(() -> mediaCommand(null, action));
        }
      }
      heard = "";
      spoken = "";
      if ((role.equals("user") || role.equals("assistant")) && !transcript.isEmpty())
        local.tvgpt.assistant.data.AppDataWork.submit(
            () -> {
              String eventId = turn.optString("id", rootEventId);
              if (eventId.isEmpty()) eventId = "unidentified-" + turn.toString().hashCode();
              new local.tvgpt.assistant.data.ConversationRepository(context)
                  .appendTurn(
                      workspace.id,
                      archiveCall,
                      eventId,
                      System.currentTimeMillis(),
                      role,
                      transcript);
              synchronized (workspace) {
                JSONObject last =
                    workspace.transcript.optJSONObject(workspace.transcript.length() - 1);
                if (last == null
                    || !last.optString("role").equals(role)
                    || !last.optString("text").equals(transcript))
                  try {
                    workspace.transcript.put(
                        new JSONObject()
                            .put("role", role)
                            .put("at", System.currentTimeMillis())
                            .put(
                                "text",
                                transcript.substring(0, Math.min(1200, transcript.length()))));
                    while (workspace.transcript.length() > 24) workspace.transcript.remove(0);
                    workspace.lastUsed = System.currentTimeMillis();
                  } catch (Exception ignored) {
                  }
              }
              SessionWorkspace.save(workspace);
            });
    }
  }

  private void delegate(String id, String text) {
    if (closed) return;
    listener.status("Выполняю поручение…");
    try {
      append(id, agent.run(text));
      if (!closed) listener.status("Слушаю");
    } catch (Exception e) {
      android.util.Log.w("TVGPTLive", "Agent failed: " + DeviceLogin.safeError(e));
      try {
        append(id, "Не удалось выполнить поручение. " + DeviceLogin.safeError(e));
        listener.status("Не удалось выполнить поручение");
      } catch (Exception ignored) {
      }
    }
  }

  private void append(String id, String text) throws Exception {
    if (closed) return;
    for (String part : LiveProtocol.chunks(text)) {
      JSONObject message =
          new JSONObject()
              .put("type", "delegation.context.append")
              .put("delegation_item_id", id)
              .put("channel", "speakable")
              .put(
                  "content",
                  new JSONArray()
                      .put(new JSONObject().put("type", "input_text").put("text", part)));
      if (socket != null) socket.send(message.toString());
    }
  }

  void userText(String text) {
    try {
      for (String part : LiveProtocol.chunks(text)) {
        JSONObject message = LiveProtocol.userInput(part);
        if (!closed && socket != null) socket.send(message.toString());
      }
    } catch (Exception e) {
      if (!closed) listener.fatal("Не удалось передать текст в разговор");
    }
  }

  void close() {
    if (closed) return;
    closed = true;
    agent.cancel();
    if (socket != null) {
      socket.send("{\"type\":\"session.close\"}");
      socket.close(1000, "closed");
    }
    stopTimer.shutdownNow();
    controls.shutdownNow();
    executor.shutdownNow();
    client.dispatcher().cancelAll();
    client.connectionPool().evictAll();
  }

  void afterDismissed() {
    local.tvgpt.assistant.data.AppDataWork.submit(
        () -> {
          ArchiveEvents.changed(context);
          ConversationSummarizer.request(context, workspace.id);
        });
  }
}
