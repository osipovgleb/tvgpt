package local.tvgpt.assistant;

import java.util.Locale;

final class WakePhrase {
  static boolean matches(String text) {
    String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^а-яёa-z]+", " ").trim() + " ";
    return words.contains(" привет телевизор ") || words.contains(" окей телевизор ");
  }
}
