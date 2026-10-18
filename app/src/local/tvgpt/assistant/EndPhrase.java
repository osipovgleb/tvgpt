package local.tvgpt.assistant;

import java.util.*;

final class EndPhrase {
  private static final Set<String> END =
      new HashSet<>(
          Arrays.asList(
              "завершить текущий голосовой разговор",
              "заверши текущий голосовой разговор",
              "закрой сессию live",
              "заверши live сессию",
              "заверши сессию live",
              "закрой сессию",
              "заверши чат",
              "закрой чат",
              "закончи сессию",
              "завершить сессию",
              "закрой разговор",
              "заверши голосовую сессию",
              "заверши голосовой чат",
              "завершить голосовой чат",
              "закончи голосовой чат",
              "выключи голосовой чат",
              "заверши разговор",
              "закончи разговор",
              "заверши сеанс",
              "на этом все",
              "на этом все спасибо",
              "все на этом все",
              "все спасибо",
              "все спасибо до свидания",
              "спасибо до свидания",
              "до свидания",
              "отключись",
              "заверши сессию",
              "завершить разговор",
              "останови голосовой режим",
              "выключи помощника",
              "закрой помощника",
              "закрой голосовой чат",
              "давай на этом закончим",
              "давай закончим",
              "все пока",
              "спасибо все",
              "все на этом все спасибо",
              "хватит",
              "спасибо",
              "все"));

  static boolean matches(String text) {
    if (text == null) return false;
    String words = normalized(text);
    return END.contains(words)
        || words.matches(
            "(?:ну |ладно |хорошо )?(?:все|на этом все)(?: наверное)?(?: спасибо(?: большое)?)?");
  }

  static boolean explicit(String text) {
    String words = normalized(text);
    return matches(text) && !words.equals("спасибо") && !words.equals("все");
  }

  static String normalized(String text) {
    if (text == null) return "";
    String words =
        text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^а-яa-z]+", " ").trim();
    words = words.replaceAll("\\bпожалуйста\\b", " ").replaceAll(" +", " ").trim();
    if (words.startsWith("можешь ")) words = words.substring(7);
    return words;
  }
}
