package local.tvgpt.assistant;

import java.io.IOException;

/** False enqueue is a delivery failure, never an instruction to execute an action again. */
final class LiveDelivery {
  interface Sender {
    boolean send(String chunk) throws Exception;
  }

  static void send(String text, CancellationToken token, Sender sender) throws Exception {
    for (String chunk : LiveProtocol.chunks(text)) {
      token.check();
      if (!sender.send(chunk)) throw new IOException("Live result enqueue rejected");
    }
  }
}
