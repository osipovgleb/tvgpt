package local.tvgpt.assistant;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;

final class Http {
  static final class Reply {
    final int code;
    final String body, location;

    Reply(int c, String b, String l) {
      code = c;
      body = b;
      location = l;
    }

    JSONObject json() throws Exception {
      return new JSONObject(body);
    }

    void requireSuccess() throws IOException {
      if (code < 200 || code >= 300)
        throw new IOException("Сервер отклонил запрос (HTTP " + code + ")");
    }
  }

  static Reply post(String url, String body, String contentType, Map<String, String> headers)
      throws Exception {
    HttpsURLConnectionSafe check = new HttpsURLConnectionSafe(url);
    HttpURLConnection c = (HttpURLConnection) check.url.openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(45000);
    c.setInstanceFollowRedirects(false);
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    c.setRequestProperty("Content-Type", contentType);
    c.setRequestProperty("User-Agent", "tvgpt/0.1 Android");
    for (Map.Entry<String, String> h : headers.entrySet())
      c.setRequestProperty(h.getKey(), h.getValue());
    try {
      try (OutputStream out = c.getOutputStream()) {
        out.write(body.getBytes("UTF-8"));
      }
      int code = c.getResponseCode();
      InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      if (in != null)
        try (InputStream stream = in) {
          byte[] b = new byte[8192];
          int n;
          while ((n = stream.read(b)) != -1) {
            if (data.size() + n > 8 * 1024 * 1024) throw new IOException("Ответ слишком большой");
            data.write(b, 0, n);
          }
        }
      return new Reply(code, data.toString("UTF-8"), c.getHeaderField("Location"));
    } finally {
      c.disconnect();
    }
  }

  static Reply json(String url, JSONObject body) throws Exception {
    return post(url, body.toString(), "application/json", Collections.emptyMap());
  }

  static String form(Map<String, String> values) throws Exception {
    StringBuilder s = new StringBuilder();
    for (Map.Entry<String, String> e : values.entrySet()) {
      if (s.length() > 0) s.append('&');
      s.append(URLEncoder.encode(e.getKey(), "UTF-8"))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), "UTF-8"));
    }
    return s.toString();
  }

  private static class HttpsURLConnectionSafe {
    final URL url;

    HttpsURLConnectionSafe(String address) throws Exception {
      url = new URL(address);
      if (!url.getProtocol().equals("https")) throw new IOException("Требуется HTTPS");
    }
  }
}
