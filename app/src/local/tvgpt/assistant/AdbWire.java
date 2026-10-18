package local.tvgpt.assistant;

import java.io.*;
import java.math.BigInteger;
import java.nio.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.Cipher;

final class AdbWire {
  static final int CNXN = 0x4e584e43,
      AUTH = 0x48545541,
      OPEN = 0x4e45504f,
      OKAY = 0x59414b4f,
      WRTE = 0x45545257,
      CLSE = 0x45534c43;

  static class Packet {
    int command, arg0, arg1;
    byte[] data;
  }

  static void send(OutputStream stream, int command, int arg0, int arg1, byte[] data)
      throws IOException {
    int checksum = 0;
    for (byte b : data) checksum += b & 255;
    ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    header
        .putInt(command)
        .putInt(arg0)
        .putInt(arg1)
        .putInt(data.length)
        .putInt(checksum)
        .putInt(command ^ 0xffffffff);
    stream.write(header.array());
    stream.write(data);
    stream.flush();
  }

  static Packet read(InputStream stream) throws IOException {
    DataInputStream input = new DataInputStream(stream);
    byte[] h = new byte[24];
    input.readFully(h);
    ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
    Packet p = new Packet();
    p.command = b.getInt();
    p.arg0 = b.getInt();
    p.arg1 = b.getInt();
    int size = b.getInt(), sum = b.getInt(), magic = b.getInt();
    if (magic != (p.command ^ 0xffffffff) || size < 0 || size > 1024 * 1024)
      throw new IOException("Invalid ADB packet");
    p.data = new byte[size];
    input.readFully(p.data);
    int calculated = 0;
    for (byte value : p.data) calculated += value & 255;
    if (sum != calculated && sum != 0) throw new IOException("Invalid ADB checksum");
    return p;
  }

  static byte[] sign(PrivateKey key, byte[] token) throws Exception {
    if (token.length != 20) throw new IOException("Invalid ADB authentication challenge");
    byte[] prefix = {
      0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };
    byte[] digest = new byte[35];
    System.arraycopy(prefix, 0, digest, 0, prefix.length);
    System.arraycopy(token, 0, digest, prefix.length, 20);
    Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    rsa.init(Cipher.ENCRYPT_MODE, key);
    return rsa.doFinal(digest);
  }

  static byte[] publicKey(RSAPublicKey key) {
    BigInteger n = key.getModulus(), base = BigInteger.ONE.shiftLeft(32);
    ByteBuffer result = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
    result.putInt(64).putInt(n.mod(base).modInverse(base).negate().mod(base).intValue());
    result.put(little(n, 256));
    result.put(little(BigInteger.ONE.shiftLeft(4096).mod(n), 256));
    result.putInt(key.getPublicExponent().intValue());
    return result.array();
  }

  private static byte[] little(BigInteger value, int size) {
    byte[] big = value.toByteArray(), out = new byte[size];
    for (int i = 0; i < size && i < big.length; i++) out[i] = big[big.length - 1 - i];
    return out;
  }
}
