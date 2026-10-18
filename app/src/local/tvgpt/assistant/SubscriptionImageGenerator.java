package local.tvgpt.assistant;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import java.io.*;
import java.util.Map;
import local.tvgpt.assistant.digests.*;
import okhttp3.*;
import org.json.*;

/** App-owned subscription request. Never uses a chat/desktop tool or microphone. */
public final class SubscriptionImageGenerator implements ImageGenerator {
  private final Context context;

  public SubscriptionImageGenerator(Context c) {
    context = c.getApplicationContext();
  }

  public Result generate(String operationId, String prompt, File destination) {
    boolean sent = false;
    try {
      JSONObject request = SubscriptionImageProtocol.request(prompt);
      Map<String, String> headers = LiveConnection.headers(new AccountStore(context).fresh());
      Request.Builder builder =
          new Request.Builder()
              .url("https://chatgpt.com/backend-api/codex/images/generations")
              .post(RequestBody.create(MediaType.parse("application/json"), request.toString()));
      for (Map.Entry<String, String> h : headers.entrySet())
        builder.header(h.getKey(), h.getValue());
      builder.header("x-codex-image-turn-id", operationId).header("Accept", "application/json");
      OkHttpClient client = SubscriptionImageTransport.client();
      sent = true;
      try (Response reply = client.newCall(builder.build()).execute()) {
        if (!reply.isSuccessful()) {
          String state =
              reply.code() >= 400 && reply.code() < 500 && reply.code() != 408
                  ? "failed"
                  : "unconfirmed";
          String reason = "Генерация: HTTP " + reply.code();
          if (reply.body() != null) {
            try (InputStream in = reply.body().byteStream()) {
              SubscriptionImageProtocol.read(in, 16384);
            } catch (ImageGenerationProtocol.Rejected rejected) {
              reason += " (" + rejected.code + ")";
            } catch (Exception ignored) {
            }
          }
          return outcome(state, null, "", reason);
        }
        if (reply.body() == null) return outcome("unconfirmed", null, "", "Ответ без изображения");
        JSONObject response;
        try (InputStream in = reply.body().byteStream()) {
          response = SubscriptionImageProtocol.read(in, 32 * 1024 * 1024);
        }
        String encoded = SubscriptionImageProtocol.image(response);
        if (encoded.isEmpty())
          return outcome(
              response == null ? "unconfirmed" : "failed",
              null,
              response == null ? "" : response.optString("model", SubscriptionImageProtocol.MODEL),
              response == null
                  ? "Ответ генератора не завершён"
                  : "Модель завершила запрос без изображения");
        byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
        if (decoded.length > 16 * 1024 * 1024) throw new IOException("Image size limit");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(decoded, 0, decoded.length, bounds);
        if (bounds.outWidth <= 0
            || bounds.outHeight <= 0
            || (long) bounds.outWidth * bounds.outHeight > 16000000L)
          throw new IOException("Image dimensions");
        if (!java.util.Arrays.asList("image/png", "image/jpeg", "image/webp")
            .contains(bounds.outMimeType)) throw new IOException("Image format");
        bounds.inJustDecodeBounds = false;
        bounds.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 1280);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length, bounds);
        if (bitmap == null) throw new IOException("Image decode");
        bitmap.recycle();
        try (FileOutputStream out = new FileOutputStream(destination)) {
          out.write(decoded);
        }
        return outcome(
            "ready", destination, response.optString("model", SubscriptionImageProtocol.MODEL), "");
      }
    } catch (ImageGenerationProtocol.Rejected rejected) {
      destination.delete();
      return outcome("failed", null, "", rejected.getMessage());
    } catch (Exception e) {
      android.util.Log.w("TVGPTDigest", "Imagegen transport: " + e.getClass().getSimpleName());
      destination.delete();
      return outcome(
          sent ? "unconfirmed" : "failed",
          null,
          "",
          sent
              ? "Результат генерации не подтверждён"
              : "Для генерации требуется доступ к подписке");
    }
  }

  private Result outcome(String state, File file, String model, String reason) {
    context
        .getSharedPreferences("digests", 0)
        .edit()
        .putString("imagegen", state.equals("ready") ? "supported" : state)
        .putString("imagegen_reason", reason)
        .putString("imagegen_model", model)
        .putLong("imagegen_checked_at", System.currentTimeMillis())
        .apply();
    android.util.Log.i("TVGPTDigest", "Imagegen state=" + state + "; " + reason);
    return new Result(state, file, model, reason);
  }
}
