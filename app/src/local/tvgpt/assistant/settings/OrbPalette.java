package local.tvgpt.assistant.settings;

public final class OrbPalette {
  public static final java.util.List<String> IDS =
      java.util.Collections.unmodifiableList(
          java.util.Arrays.asList(
              "sky",
              "blue",
              "turquoise",
              "mint",
              "green",
              "lime",
              "yellow",
              "amber",
              "orange",
              "pink",
              "violet",
              "silver"));
  public static final java.util.List<String> LABELS =
      java.util.Collections.unmodifiableList(
          java.util.Arrays.asList(
              "Голубой",
              "Синий",
              "Бирюзовый",
              "Мятный",
              "Зелёный",
              "Лаймовый",
              "Жёлтый",
              "Янтарный",
              "Оранжевый",
              "Розовый",
              "Фиолетовый",
              "Серебряный"));
  private static final int[] COLOURS = {
    0xff80a7ff,
    0xff486dff,
    0xff3ad7db,
    0xff7ae3b4,
    0xff45bc72,
    0xffb0df45,
    0xffffdf55,
    0xffffbd40,
    0xffff8b45,
    0xffed89c2,
    0xffa48bee,
    0xffbcc7d5
  };

  public static int index(String id) {
    for (int i = 0; i < IDS.size(); i++) if (IDS.get(i).equals(id)) return i;
    return 0;
  }

  public static String valid(String id) {
    return IDS.get(index(id));
  }

  public static int colour(String id) {
    return COLOURS[index(id)];
  }

  public static float[] matrix(String id) {
    int colour = colour(id);
    float r = ((colour >> 16) & 255) / 255f,
        g = ((colour >> 8) & 255) / 255f,
        b = (colour & 255) / 255f;
    return new float[] {1 - r, 0, r, 0, 0, 1 - g, 0, g, 0, 0, 1 - b, 0, b, 0, 0, 0, 0, 0, 1, 0};
  }

  private OrbPalette() {}
}
