package local.tvgpt.assistant;

import android.content.Context;
import android.content.Intent;

/** Content-free notification; receivers read their own current database state. */
public final class ArchiveEvents {
  public static final String CHANGED = "local.tvgpt.assistant.ARCHIVE_CHANGED";

  private ArchiveEvents() {}

  public static void changed(Context context) {
    context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
  }
}
