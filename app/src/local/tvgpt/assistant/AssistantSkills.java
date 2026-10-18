package local.tvgpt.assistant;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class AssistantSkills {
  private static volatile String cached;

  private static final java.util.Map<String, String> appCache = new java.util.HashMap<>();

  static synchronized String app(Context context, String name) throws Exception {
    String id = AppSkillIndex.id(name);
    if (id.isEmpty())
      return "Встроенный навык пока отсутствует. Доступны youtube и imagegen; для поиска продолжай"
          + " по общему app-search.";
    String content = appCache.get(id);
    if (content == null) {
      content = AppSkillIndex.read(context.getAssets().open(AppSkillIndex.asset(id)));
      appCache.put(id, content);
    }
    return content;
  }

  static synchronized String search(Context context) {
    if (cached != null) return cached;
    try (InputStream input = context.getAssets().open("skills/app-search/SKILL.md");
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024];
      int count;
      while ((count = input.read(bytes)) != -1) {
        if (output.size() + count > 12000) throw new java.io.IOException("Skill too large");
        output.write(bytes, 0, count);
      }
      cached = new String(output.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception failure) {
      cached =
          "При поиске сначала открой поиск в приложении, затем используй type_text вместо набора"
              + " букв пультом. Проверь результат.";
    }
    return cached;
  }
}
