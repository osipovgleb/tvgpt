package local.tvgpt.assistant;

import android.content.Context;
import java.io.File;

final class SessionWorkspace {
  private static SessionStore store;

  static synchronized SessionStore.State open(Context c) {
    if (store == null)
      store =
          new SessionStore(
              new File(c.getFilesDir(), "codex/tv-assistant"),
              state -> {
                local.tvgpt.assistant.data.ConversationRepository repo =
                    new local.tvgpt.assistant.data.ConversationRepository(c);
                repo.importLegacy(state.id, state.created, state.lastUsed, state.transcript);
              });
    try {
      SessionStore.State state = store.open(System.currentTimeMillis());
      new local.tvgpt.assistant.data.ConversationRepository(c)
          .importLegacy(state.id, state.created, state.lastUsed, state.transcript);
      return state;
    } catch (Exception e) {
      throw new IllegalStateException("Не удалось открыть рабочую сессию", e);
    }
  }

  static synchronized void save(SessionStore.State state) {
    try {
      if (store != null && state.owner == store) store.save(state);
    } catch (Exception e) {
      android.util.Log.w("TVGPTSession", "Workspace save failed: " + e.getClass().getSimpleName());
    }
  }

  static synchronized void clear(Context c) {
    store = null;
    File folder = new File(c.getFilesDir(), "codex/tv-assistant");
    File[] files = folder.listFiles();
    if (files != null) for (File f : files) f.delete();
  }
}
