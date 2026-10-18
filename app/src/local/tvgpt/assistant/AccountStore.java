package local.tvgpt.assistant;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

final class AccountStore {
  private final Context context;
  private static final Object AUTH_LOCK = new Object();
  private static long revision;

  AccountStore(Context c) {
    context = c.getApplicationContext();
  }

  private javax.crypto.SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    String alias = "tvgpt.account";
    if (!ks.containsAlias(alias)) {
      KeyGenerator gen =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      gen.init(
          new KeyGenParameterSpec.Builder(
                  alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      gen.generateKey();
    }
    return (javax.crypto.SecretKey) ks.getKey(alias, null);
  }

  void save(JSONObject tokens) throws Exception {
    synchronized (AUTH_LOCK) {
      writeTokens(tokens);
      revision++;
    }
  }

  private void writeTokens(JSONObject tokens) throws Exception {
    if (tokens.optString("access_token").isEmpty() || tokens.optString("refresh_token").isEmpty())
      throw new Exception("Неполный ответ авторизации");
    accountId(tokens);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
    String encrypted =
        Base64.encodeToString(cipher.doFinal(tokens.toString().getBytes("UTF-8")), Base64.NO_WRAP);
    if (!context
        .getSharedPreferences("account", 0)
        .edit()
        .putString("iv", iv)
        .putString("data", encrypted)
        .commit()) throw new Exception("Не удалось сохранить вход");
  }

  JSONObject load() throws Exception {
    synchronized (AUTH_LOCK) {
      return readTokens();
    }
  }

  private JSONObject readTokens() throws Exception {
    android.content.SharedPreferences p = context.getSharedPreferences("account", 0);
    if (!p.contains("data")) return null;
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)));
    return new JSONObject(
        new String(
            cipher.doFinal(Base64.decode(p.getString("data", ""), Base64.NO_WRAP)), "UTF-8"));
  }

  void clear() {
    if (VoicePanel.active != null) VoicePanel.active.requestEnd();
    synchronized (AUTH_LOCK) {
      revision++;
      ConversationSummarizer.invalidate();
      VoicePreview.stopActive(null);
      SessionWorkspace.clear(context);
      context.getSharedPreferences("account", 0).edit().clear().commit();
    }
  }

  JSONObject fresh() throws Exception {
    JSONObject tokens;
    long expectedRevision;
    synchronized (AUTH_LOCK) {
      tokens = readTokens();
      expectedRevision = revision;
    }
    if (tokens == null) throw new Exception("Сначала войдите в аккаунт");
    long expires = claims(tokens.getString("access_token")).optLong("exp", 0);
    if (expires * 1000 > System.currentTimeMillis() + 90000) return tokens;
    java.util.Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("grant_type", "refresh_token");
    form.put("client_id", DeviceLogin.CLIENT);
    form.put("refresh_token", tokens.getString("refresh_token"));
    Http.Reply reply =
        Http.post(
            DeviceLogin.ISSUER + "/oauth/token",
            Http.form(form),
            "application/x-www-form-urlencoded",
            java.util.Collections.emptyMap());
    reply.requireSuccess();
    JSONObject refreshed = reply.json();
    if (!refreshed.has("refresh_token"))
      refreshed.put("refresh_token", tokens.getString("refresh_token"));
    if (!refreshed.has("id_token")) refreshed.put("id_token", tokens.optString("id_token"));
    synchronized (AUTH_LOCK) {
      if (revision != expectedRevision)
        throw new Exception("Вход изменился во время обновления токена");
      writeTokens(refreshed);
      revision++;
    }
    return refreshed;
  }

  static JSONObject claims(String token) throws Exception {
    String[] parts = token.split("\\.");
    if (parts.length < 2) throw new Exception("Некорректный токен аккаунта");
    return new JSONObject(
        new String(
            Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING),
            "UTF-8"));
  }

  static String accountId(JSONObject tokens) throws Exception {
    for (String k : new String[] {"access_token", "id_token"}) {
      if (tokens.optString(k).isEmpty()) continue;
      JSONObject c = claims(tokens.getString(k));
      JSONObject auth = c.optJSONObject("https://api.openai.com/auth");
      String id = auth == null ? "" : auth.optString("chatgpt_account_id");
      if (!id.isEmpty()) return id;
    }
    throw new Exception("Не найден аккаунт ChatGPT");
  }
}
