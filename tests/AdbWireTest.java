package local.tvgpt.assistant;

import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;

public class AdbWireTest {
  public static void main(String[] args) throws Exception {
    byte[] payload = "hello\0".getBytes("UTF-8");
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    AdbWire.send(stream, AdbWire.CNXN, 0x01000000, 4096, payload);
    AdbWire.Packet packet = AdbWire.read(new ByteArrayInputStream(stream.toByteArray()));
    if (packet.command != AdbWire.CNXN || !java.util.Arrays.equals(packet.data, payload))
      throw new AssertionError("packet roundtrip");
    byte[] invalid = stream.toByteArray();
    invalid[20] ^= 1;
    try {
      AdbWire.read(new ByteArrayInputStream(invalid));
      throw new AssertionError("bad magic");
    } catch (IOException expected) {
    }
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair keys = gen.generateKeyPair();
    byte[] pub = AdbWire.publicKey((RSAPublicKey) keys.getPublic());
    if (pub.length != 524 || pub[0] != 64) throw new AssertionError("adb public key encoding");
    byte[] token = new byte[20];
    new SecureRandom().nextBytes(token);
    byte[] signature = AdbWire.sign(keys.getPrivate(), token);
    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keys.getPublic());
    byte[] digest = cipher.doFinal(signature);
    if (digest.length != 35
        || !java.util.Arrays.equals(java.util.Arrays.copyOfRange(digest, 15, 35), token))
      throw new AssertionError("AUTH SHA1 signature");
    System.out.println("AdbWireTest: packet validation, RSA public key and auth signature passed");
  }
}
