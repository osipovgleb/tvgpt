package local.tvgpt.assistant;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class AdbDispatchTest {
  private static void hello(Socket peer) throws Exception {
    if (AdbWire.read(peer.getInputStream()).command != AdbWire.CNXN)
      throw new AssertionError("missing handshake");
    AdbWire.send(peer.getOutputStream(), AdbWire.CNXN, 0x01000000, 4096, new byte[0]);
  }

  public static void main(String[] args) throws Exception {
    // Disconnect after receiving OPEN: a retry would execute BACK twice.
    try (ServerSocket server = new ServerSocket(0)) {
      Thread peer =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  hello(socket);
                  if (AdbWire.read(socket.getInputStream()).command != AdbWire.OPEN)
                    throw new AssertionError("missing OPEN");
                } catch (Exception failure) {
                  throw new AssertionError(failure);
                }
              });
      peer.start();
      try {
        AdbRemote.shellOnSocket(
            new Socket("127.0.0.1", server.getLocalPort()),
            "input keyevent 4",
            null,
            2000,
            CancellationToken.NONE);
        throw new AssertionError("lost dispatched command claimed success");
      } catch (AdbRemote.DispatchException expected) {
      }
      peer.join();
    }
    // Transport failure before OPEN remains eligible for a safe fallback.
    try (ServerSocket server = new ServerSocket(0)) {
      Thread peer =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  AdbWire.read(socket.getInputStream());
                } catch (Exception failure) {
                  throw new AssertionError(failure);
                }
              });
      peer.start();
      try {
        AdbRemote.shellOnSocket(
            new Socket("127.0.0.1", server.getLocalPort()),
            "input keyevent 4",
            null,
            2000,
            CancellationToken.NONE);
        throw new AssertionError("failed handshake claimed success");
      } catch (AdbRemote.DispatchException wrongPhase) {
        throw new AssertionError("pre-dispatch failure classified as dispatched", wrongPhase);
      } catch (IOException expected) {
      }
      peer.join();
    }
    // Token cancellation closes a blocked socket and propagates cancellation.
    try (ServerSocket server = new ServerSocket(0)) {
      CancellationToken token = new CancellationToken();
      CountDownLatch received = new CountDownLatch(1);
      AtomicReference<Throwable> result = new AtomicReference<>();
      Thread worker =
          new Thread(
              () -> {
                try {
                  AdbRemote.shellOnSocket(
                      new Socket("127.0.0.1", server.getLocalPort()),
                      "input keyevent 4",
                      null,
                      10000,
                      token);
                  result.set(new AssertionError("cancelled socket returned normally"));
                } catch (Throwable failure) {
                  result.set(failure);
                }
              });
      worker.start();
      try (Socket socket = server.accept()) {
        hello(socket);
        AdbWire.read(socket.getInputStream());
        received.countDown();
        token.cancel();
        worker.join(1000);
        if (worker.isAlive()) throw new AssertionError("cancel did not unblock socket");
        if (!(result.get() instanceof CancellationException))
          throw new AssertionError("cancellation became transport fallback", result.get());
      }
    }
    System.out.println("AdbDispatchTest passed");
  }
}
