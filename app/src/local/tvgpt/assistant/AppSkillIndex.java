package local.tvgpt.assistant;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Fixed bundled skill routes; model-supplied names never become filesystem paths. */
final class AppSkillIndex {
  static String id(String name) {
    if (name == null) return "";
    String normalized = name.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("youtube")
        || normalized.equals("youtube android tv")
        || normalized.equals("ютуб")
        || normalized.equals("com.google.android.youtube.tv")) return "youtube";
    return "";
  }

  static String asset(String name) {
    return id(name).equals("youtube") ? "skills/youtube/SKILL.md" : "";
  }

  static String read(InputStream input) throws IOException {
    try (InputStream in = input;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024];
      int count;
      while ((count = in.read(bytes)) != -1) {
        if (out.size() + count > 12000) throw new IOException("Skill too large");
        out.write(bytes, 0, count);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
