package local.tvgpt.assistant;

import java.util.*;
import org.json.JSONObject;

final class DeviceLogin {
  static final String CLIENT = "app_EMoamEEZ73f0CkXaXp7hrann";
  static final String ISSUER = "https://auth.openai.com";

  interface Listener {
    void code(String code);

    void status(String message);

    void finished(boolean success);
  }

  private volatile boolean cancelled;
  private Thread worker;

  synchronized void cancel() {
    cancelled = true;
    if (worker != null) worker.interrupt();
  }

  void start(AccountStore store, Listener listener) {
    worker =
        new Thread(
            () -> {
              try {
                Http.Reply response =
                    Http.json(
                        ISSUER + "/api/accounts/deviceauth/usercode",
                        new JSONObject().put("client_id", CLIENT));
                response.requireSuccess();
                JSONObject code = response.json();
                String userCode = code.optString("user_code", code.optString("usercode"));
                String device = code.getString("device_auth_id");
                if (userCode.isEmpty()) throw new Exception("Сервер не выдал код");
                if (cancelled) return;
                listener.code(userCode);
                long deadline = android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000;
                int interval = Protocol.interval(code.optString("interval", "5"));
                while (!cancelled && android.os.SystemClock.elapsedRealtime() < deadline) {
                  Thread.sleep(interval * 1000L);
                  if (cancelled) return;
                  Http.Reply poll =
                      Http.json(
                          ISSUER + "/api/accounts/deviceauth/token",
                          new JSONObject()
                              .put("device_auth_id", device)
                              .put("user_code", userCode));
                  if (Protocol.pending(poll.code)) continue;
                  poll.requireSuccess();
                  JSONObject grant = poll.json();
                  if (cancelled) return;
                  Map<String, String> form = new LinkedHashMap<>();
                  form.put("grant_type", "authorization_code");
                  form.put("client_id", CLIENT);
                  form.put("redirect_uri", ISSUER + "/deviceauth/callback");
                  form.put("code", grant.getString("authorization_code"));
                  form.put("code_verifier", grant.getString("code_verifier"));
                  Http.Reply exchange =
                      Http.post(
                          ISSUER + "/oauth/token",
                          Http.form(form),
                          "application/x-www-form-urlencoded",
                          Collections.emptyMap());
                  exchange.requireSuccess();
                  JSONObject tokens = exchange.json();
                  synchronized (this) {
                    if (cancelled) return;
                    store.save(tokens);
                  }
                  listener.status("Аккаунт подключён");
                  listener.finished(true);
                  return;
                }
                if (!cancelled) throw new Exception("Код истёк. Запросите новый код");
              } catch (InterruptedException ignored) {
              } catch (Exception e) {
                if (!cancelled) {
                  listener.status(safeError(e));
                  listener.finished(false);
                }
              }
            },
            "tvgpt-device-login");
    worker.start();
  }

  static String safeError(Exception e) {
    if (e instanceof java.net.UnknownHostException) return "Нет соединения с сервером входа";
    if (e instanceof javax.net.ssl.SSLException)
      return "Не удалось установить защищённое соединение";
    if (e instanceof java.net.SocketTimeoutException)
      return "Сервер не ответил вовремя. Повторите вход";
    if (e instanceof java.io.IOException
        && e.getMessage() != null
        && e.getMessage().startsWith("Сервер отклонил")) return e.getMessage();
    return "Не удалось завершить вход (" + e.getClass().getSimpleName() + ")";
  }
}
