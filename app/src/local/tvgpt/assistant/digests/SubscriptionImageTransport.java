package local.tvgpt.assistant.digests;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public final class SubscriptionImageTransport {
  private SubscriptionImageTransport() {}

  public static OkHttpClient client() {
    return new OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build();
  }
}
