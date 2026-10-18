package local.tvgpt.assistant.digests;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import okhttp3.*;

public final class SubscriptionImageTransportTest {
  public static void main(String[] args) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Thread responder =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  BufferedReader input =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.US_ASCII));
                  String line;
                  while ((line = input.readLine()) != null && !line.isEmpty()) {}
                  Thread.sleep(11000);
                  byte[] body =
                      "{\"data\":[{\"b64_json\":\"fixture\"}]}".getBytes(StandardCharsets.US_ASCII);
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 200 OK\r\nContent-Length: "
                                  + body.length
                                  + "\r\nConnection: close\r\n\r\n")
                              .getBytes(StandardCharsets.US_ASCII));
                  socket.getOutputStream().write(body);
                  socket.getOutputStream().flush();
                } catch (Exception ignored) {
                }
              });
      responder.setDaemon(true);
      responder.start();
      try (Response response =
          SubscriptionImageTransport.client()
              .newCall(
                  new Request.Builder()
                      .url("http://127.0.0.1:" + server.getLocalPort() + "/fixture")
                      .build())
              .execute()) {
        if (!response.isSuccessful() || !response.body().string().contains("fixture"))
          throw new AssertionError("late generation response lost");
      }
      responder.join(2000);
    }
    System.out.println(
        "SubscriptionImageTransportTest: delayed image response survives default read timeout");
  }
}
