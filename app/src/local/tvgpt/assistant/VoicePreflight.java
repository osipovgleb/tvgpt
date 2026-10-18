package local.tvgpt.assistant;

import java.util.concurrent.Callable;

/** No native call or visible orb is retained when local account preflight fails. */
final class VoicePreflight {
  static boolean ready(Callable<Boolean> account, Runnable dismiss) {
    try {
      if (account.call()) return true;
    } catch (Exception ignored) {
      // An unreadable encrypted account has the same quiet failure path as logout.
    }
    dismiss.run();
    return false;
  }
}
