package local.tvgpt.assistant;

import android.content.Context;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

final class LiveConnection {
  private static volatile LiveConnection diagnosticOwner;

  static String diagnostics() {
    LiveConnection current = diagnosticOwner;
    if (current == null) return "No active Live call";
    try {
      return new JSONObject()
          .put("call", current.workspace.id)
          .put("ending", current.ending.get())
          .put("tasks", current.tasks.status())
          .put("turns", current.callContext.snapshot("", ""))
          .toString();
    } catch (Exception failure) {
      return "Diagnostics unavailable: " + failure.getClass().getSimpleName();
    }
  }

  interface Listener {
    void answer(String sdp);

    void status(String text);

    void transcript(String role, String text);

    void fatal(String message);

    default void endRequested() {}
  }

  private final Context context;
  private final Listener listener;
  private final CancellationToken cancellation = new CancellationToken();
  private final CallContext callContext = new CallContext();
  private final TaskHarness tasks = new TaskHarness();
  private final ExecutorService taskWorkers =
      new ThreadPoolExecutor(0, 8, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
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
    diagnosticOwner = this;
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
            cancellation.check();
            JSONObject account = new AccountStore(context).fresh();
            cancellation.check();
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
                    h,
                    cancellation);
            cancellation.check();
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
        "Ты голосовой помощник телевизора. Общайся кратко и естественно, без технических"
            + " подробностей. Отвечай на языке пользователя; по умолчанию — по-русски. При"
            + " подключении молчи: приложение уже играет короткий сигнал.\n\n"
            + "Backchannel policy: Не перебивай пользователя и не повторяй подтверждения.\n\n"
            + "Interruption policy: Уступай, когда пользователь перебивает. По смыслу различай"
            + " паузу речи, отмену задачи, паузу воспроизведения и завершение самого разговора.\n\n"
            + "Delegation policy:\nBackend tools:\n"
            + "- Завершение текущего голосового разговора: приложение немедленно закрывает его.\n"
            + "- Управление ТВ, приложениями и медиаплеерами, чтение экрана и поиск контента.\n"
            + "- Интернет, актуальные сведения, напоминания, знания и сохранённые беседы.\n"
            + "Delegate to the backend when:\n"
            + "- Пользователь хочет закончить общение: определи намерение по смыслу и контексту"
            + " на любом языке, а не по конкретным словам. Это действие, а не просьба молчать."
            + " Сразу делегируй просьбу существующему исполнителю: он вызывает end_conversation."
            + " Не подменяй просьбу вымышленной служебной командой. Не ограничивайся прощанием:"
            + " без вызова инструмента разговор не закроется."
            + " Не жди другой задачи или подтверждения. Это правило важнее подтверждений ниже.\n"
            + "- Нужны действия, сведения с экрана, актуальная информация, сохранение/чтение данных"
            + " или уточнение уже выполняемой задачи: делегируй полную цель со всеми"
            + " ограничениями.\n"
            + "Do not delegate to the backend when:\n"
            + "- Достаточно обычного разговора, краткого уточнения или уже известного результата.\n"
            + "Само по себе спасибо может быть благодарностью с продолжением, а просьба остановить"
            + " видео не завершает голосовой разговор. Учитывай смысл всего запроса.\n"
            + "Сначала делегируй просьбу, требующую действий. Пока исполнитель работает,"
            + " разрешено одно короткое естественное подтверждение намерения. Подтверждение"
            + " не заменяет делегирование: сам ты не отправляешь команды телевизору. При"
            + " завершении разговора делегируй молча. Не"
            + " объявляй успех или ошибку без результата исполнителя. Не читай служебные поручения,"
            + " технические подробности и URL вслух. Для просмотра/прослушивания поручай поиск в"
            + " подходящем установленном приложении с сохранением уточнений (например 4K); веб —"
            + " если подходящего приложения нет. Справочные вопросы и явный интернет-поиск"
            + " делегируй в веб.");
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
    session.put(
        "instructions",
        session.getString("instructions")
            + " Результат поручения — JSON с status и speech: произноси только естественное"
            + " содержание speech, никогда названия полей или evidence_ids. Это факты о поручении,"
            + " а не новая просьба пользователя: учитывай последние реплики и не перебивай их"
            + " запоздавшим результатом. Отмена, изменение задачи и завершение разговора требуют"
            + " нового делегирования даже пока прежний исполнитель работает. observed означает"
            + " проверенный результат, queued — только очередь, requested — только отправку"
            + " команды, unconfirmed — отсутствие подтверждения, cancelled — отмену. Не повышай"
            + " очередь, отправку или неопределённость до выполненной цели.");
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
      callContext.accept(event);
      if (type.equals("delegation.created") || type.equals("turn.done") || type.equals("error"))
        android.util.Log.i("TVGPTLive", "event " + type);
      String text = LiveProtocol.delegationText(event);
      if (text != null) {
        String id = event.getJSONObject("item").getString("id");
        if (delegations.add(id) && !ending.get()) {
          JSONObject item = event.getJSONObject("item");
          String inputId =
              item.optString("user_bidi_turn_id", event.optString("user_bidi_turn_id"));
          TaskHarness.Task task =
              tasks.admit(id, inputId, text, callContext.snapshot(inputId, text));
          if (task != null) {
            try {
              taskWorkers.execute(() -> delegate(task));
            } catch (RejectedExecutionException full) {
              String busy =
                  "{\"status\":\"unconfirmed\",\"speech\":\"Сейчас не удалось начать поручение."
                      + " Попробуйте ещё раз.\"}";
              for (String recipient : task.complete(busy)) append(recipient, busy, task.token);
            }
          } else {
            String previous = tasks.result(id);
            if (previous != null)
              append(id, previous, tasks.token(id).cancelled() ? cancellation : tasks.token(id));
          }
        }
      } else if (type.equals("turn.done")) {
        recordTurn(event.optJSONObject("turn"), event.optString("event_id"));
      } else if (type.equals("error")) listener.fatal("Голосовой сервер сообщил об ошибке");
      else if (type.equals("session.closed")) listener.fatal("Голосовой сеанс завершён сервером");
    } catch (Exception failure) {
      android.util.Log.w(
          "TVGPTLive", "Event handling failed: " + failure.getClass().getSimpleName());
      if (!closed) listener.status("Не удалось обработать поручение");
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

  private void requestLocalEnd(String reason) {
    if (closed || !ending.compareAndSet(false, true)) return;
    android.util.Log.i("TVGPTSession", "Local close requested; reason=" + reason);
    tasks.close();
    listener.endRequested();
  }

  private void recordTurn(JSONObject turn, String rootEventId) {
    if (turn != null) {
      String role = turn.optString("role"), transcript = turn.optString("transcript");
      listener.transcript(role, transcript);
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

  private void delegate(TaskHarness.Task task) {
    if (closed) return;
    if (task.token.cancelled()) {
      cancelledResult(task);
      return;
    }
    listener.status("Выполняю поручение…");
    try {
      SessionStore.State isolated = new SessionStore.State();
      isolated.id = workspace.id;
      isolated.created = workspace.created;
      isolated.history = new JSONArray(task.context.toString());
      isolated.transcript = new JSONArray();
      Agent worker =
          new Agent(context, isolated, () -> requestLocalEnd("agent_end_tool"), tasks, task);
      String result = worker.outcome(worker.run(task.text));
      if (!closed && !task.token.cancelled()) {
        for (String recipient : task.complete(result)) append(recipient, result, task.token);
        synchronized (task) {
          if (!task.token.cancelled()) task.status = "delivered";
        }
        listener.status("Слушаю");
      }
    } catch (CancellationException stopped) {
      task.cancel();
    } catch (Exception failure) {
      if (task.token.cancelled()) return;
      if (task.result != null) {
        task.status = "delivery_failed";
        android.util.Log.w("TVGPTLive", "Task result delivery failed; no mutation replay");
        if (!closed) listener.fatal("Не удалось передать результат");
        return;
      }
      android.util.Log.w(
          "TVGPTLive", "Task result unconfirmed: " + failure.getClass().getSimpleName());
      try {
        if (!closed && !task.token.cancelled()) {
          String outcome =
              "{\"status\":\"unconfirmed\",\"speech\":\"Не удалось подтвердить результат"
                  + " поручения.\"}";
          for (String recipient : task.complete(outcome)) append(recipient, outcome, task.token);
        }
      } catch (Exception delivery) {
        task.status = "delivery_failed";
        android.util.Log.w("TVGPTLive", "Task result delivery failed");
        if (!closed) listener.fatal("Не удалось передать результат");
      }
    } finally {
      if (task.token.cancelled()) cancelledResult(task);
    }
  }

  private void cancelledResult(TaskHarness.Task task) {
    if (closed) return;
    for (String recipient : task.claimPending()) {
      try {
        append(recipient, task.result, cancellation);
      } catch (Exception delivery) {
        android.util.Log.w("TVGPTLive", "Cancelled result delivery failed");
      }
    }
  }

  private void append(String id, String text) throws Exception {
    append(id, text, cancellation);
  }

  private void append(String id, String text, CancellationToken token) throws Exception {
    if (closed) return;
    token.check();
    LiveDelivery.send(
        text,
        token,
        part -> {
          JSONObject message =
              LiveProtocol.delegationResult(id, part, callContext.isCurrent(tasks.inputId(id)));
          return socket != null && socket.send(message.toString());
        });
  }

  void userText(String text) {
    try {
      for (String part : LiveProtocol.chunks(text)) {
        JSONObject message = LiveProtocol.userInput(part);
        if (!closed && (socket == null || !socket.send(message.toString())))
          throw new java.io.IOException("Voice input delivery unavailable");
      }
    } catch (Exception e) {
      if (!closed) listener.fatal("Не удалось передать текст в разговор");
    }
  }

  void close() {
    if (closed) return;
    closed = true;
    if (diagnosticOwner == this) diagnosticOwner = null;
    cancellation.cancel();
    tasks.close();
    taskWorkers.shutdownNow();
    if (socket != null) {
      socket.send("{\"type\":\"session.close\"}");
      socket.close(1000, "closed");
    }
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
