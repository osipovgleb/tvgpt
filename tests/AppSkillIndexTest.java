package local.tvgpt.assistant;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AppSkillIndexTest {
  public static void main(String[] args) throws Exception {
    for (String name :
        new String[] {"YouTube", " YouTube Android TV ", "Ютуб", "com.google.android.youtube.tv"}) {
      String asset = AppSkillIndex.asset(name);
      if (!asset.equals("skills/youtube/SKILL.md")) throw new AssertionError("Missing app route");
      String content = AppSkillIndex.read(Files.newInputStream(Path.of("app/assets", asset)));
      if (!content.startsWith("---\nname: youtube\n") || content.length() < 100)
        throw new AssertionError("Bundled skill unavailable or invalid");
    }
    for (String name :
        new String[] {
          null,
          "",
          "Spotify",
          "YouTube TV",
          "com.google.android.apps.youtube.unplugged",
          "../youtube",
          "/etc/passwd",
          "youtube/../account"
        })
      if (!AppSkillIndex.asset(name).isEmpty())
        throw new AssertionError("Unknown name became an asset path");
    String russian = "Смотреть позже";
    String decoded =
        AppSkillIndex.read(new ByteArrayInputStream(russian.getBytes(StandardCharsets.UTF_8)));
    if (!decoded.equals(russian)) throw new AssertionError("UTF-8 skill text corrupted");
    boolean bounded = false;
    try {
      AppSkillIndex.read(new ByteArrayInputStream(new byte[12001]));
    } catch (IOException expected) {
      bounded = true;
    }
    if (!bounded) throw new AssertionError("Oversized app skill accepted");
    System.out.println("App skills: bundled routes, safe names, UTF-8 and size limits passed");
  }
}
