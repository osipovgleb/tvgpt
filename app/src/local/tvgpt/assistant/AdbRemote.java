package local.tvgpt.assistant;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Device-local ADB key, never imports a developer machine's private key. */
final class AdbRemote {
  private static synchronized KeyPair keys(Context c) throws Exception {
    File privateFile = new File(c.getFilesDir(), "remote-adb-private"),
        publicFile = new File(c.getFilesDir(), "remote-adb-public");
    if (privateFile.exists() && publicFile.exists()) {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      return new KeyPair(
          factory.generatePublic(new X509EncodedKeySpec(read(publicFile))),
          factory.generatePrivate(new PKCS8EncodedKeySpec(read(privateFile))));
    }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    try (FileOutputStream out = new FileOutputStream(privateFile)) {
      out.write(pair.getPrivate().getEncoded());
    }
    try (FileOutputStream out = new FileOutputStream(publicFile)) {
      out.write(pair.getPublic().getEncoded());
    }
    return pair;
  }

  private static byte[] read(File file) throws Exception {
    try (FileInputStream in = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[2048];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toByteArray();
    }
  }

  static String key(Context c, String direction) throws Exception {
    return key(c, direction, CancellationToken.NONE);
  }

  static String key(Context c, String direction, CancellationToken token) throws Exception {
    token.check();
    int code;
    switch (direction) {
      case "pause":
        code = 127;
        break;
      case "play":
        code = 126;
        break;
      case "home":
        code = 3;
        break;
      case "back":
        code = 4;
        break;
      case "up":
        code = 19;
        break;
      case "down":
        code = 20;
        break;
      case "left":
        code = 21;
        break;
      case "right":
        code = 22;
        break;
      case "select":
        code = 23;
        break;
      default:
        throw new Exception("Неизвестная кнопка пульта");
    }
    try {
      RemoteFocus.begin(direction, token);
      shell(c, "input keyevent " + code, 10000, token);
    } finally {
      RemoteFocus.end();
    }
    return "Команда кнопки пульта выполнена: " + direction;
  }

  static String shell(Context context, String command) throws Exception {
    return shell(context, command, 10000);
  }

  /** OPEN may have reached adbd; callers must not retry via another transport. */
  static final class DispatchException extends IOException {
    DispatchException(Exception cause) {
      super("ADB command may have been dispatched", cause);
    }
  }

  static String shell(Context context, String command, int timeoutMs) throws Exception {
    return shell(context, command, timeoutMs, CancellationToken.NONE);
  }

  static String shell(Context context, String command, int timeoutMs, CancellationToken token)
      throws Exception {
    token.check();
    KeyPair pair = keys(context);
    token.check();
    return shellOnSocket(new Socket(), command, pair, timeoutMs, token);
  }

  // Connected sockets let host tests exercise real wire failure/cancellation boundaries.
  static String shellOnSocket(
      Socket socket, String command, KeyPair pair, int timeoutMs, CancellationToken token)
      throws Exception {
    long deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs;
    boolean dispatched = false;
    try (Socket ownedSocket = socket;
        CancellationToken.Registration registration =
            token.onCancel(
                () -> {
                  try {
                    socket.close();
                  } catch (IOException ignored) {
                  }
                })) {
      token.check();
      if (!socket.isConnected())
        socket.connect(new InetSocketAddress("127.0.0.1", 5555), Math.min(1500, timeoutMs));
      socket.setSoTimeout(timeoutMs);
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();
      AdbWire.send(out, AdbWire.CNXN, 0x01000000, 4096, "host::tvgpt\0".getBytes("UTF-8"));
      boolean signed = false, offered = false;
      AdbWire.Packet packet;
      while (true) {
        token.check();
        socket.setSoTimeout(remaining(deadline));
        try {
          packet = AdbWire.read(in);
        } catch (SocketTimeoutException e) {
          throw new Exception(
              offered
                  ? "На телевизоре подтвердите разрешение отладки для TV GPT"
                  : "Локальный пульт ADB не отвечает");
        }
        if (packet.command == AdbWire.CNXN) break;
        if (packet.command != AdbWire.AUTH || packet.arg0 != 1)
          throw new IOException("ADB authentication rejected");
        if (!signed) {
          AdbWire.send(out, AdbWire.AUTH, 2, 0, AdbWire.sign(pair.getPrivate(), packet.data));
          signed = true;
        } else if (!offered) {
          String publicKey =
              Base64.encodeToString(
                      AdbWire.publicKey((RSAPublicKey) pair.getPublic()), Base64.NO_WRAP)
                  + " tvgpt@android\0";
          AdbWire.send(out, AdbWire.AUTH, 3, 0, publicKey.getBytes("UTF-8"));
          offered = true;
        } else throw new Exception("Разрешение пульта ADB не принято");
      }
      token.check();
      // Mark before writing: even a partial write can dispatch the command.
      dispatched = true;
      AdbWire.send(out, AdbWire.OPEN, 1, 0, ("shell:" + command + "\0").getBytes("UTF-8"));
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      int remote = 0;
      while (true) {
        token.check();
        socket.setSoTimeout(remaining(deadline));
        packet = AdbWire.read(in);
        if (packet.command == AdbWire.OKAY) {
          remote = packet.arg0;
          continue;
        }
        if (packet.command == AdbWire.WRTE) {
          if (result.size() + packet.data.length > 131072)
            throw new IOException("ADB output too large");
          result.write(packet.data);
          AdbWire.send(out, AdbWire.OKAY, 1, packet.arg0, new byte[0]);
        } else if (packet.command == AdbWire.CLSE) {
          if (remote == 0) throw new IOException("ADB shell was not opened");
          AdbWire.send(out, AdbWire.CLSE, 1, remote, new byte[0]);
          break;
        } else throw new IOException("Unexpected ADB stream packet");
      }
      String text = result.toString("UTF-8");
      if (text.contains("Error:")
          || text.contains("Exception")
          || text.contains("Permission denied")) throw new IOException("TV rejected remote input");
      token.check();
      return text;
    } catch (java.util.concurrent.CancellationException cancelled) {
      throw cancelled;
    } catch (Exception failure) {
      token.check();
      if (dispatched) throw new DispatchException(failure);
      throw failure;
    }
  }

  private static int remaining(long deadline) throws SocketTimeoutException {
    long remaining = deadline - android.os.SystemClock.elapsedRealtime();
    if (remaining <= 0) throw new SocketTimeoutException("Remote observation deadline");
    return (int) Math.min(Integer.MAX_VALUE, remaining);
  }
}
