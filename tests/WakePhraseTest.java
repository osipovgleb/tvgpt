package local.tvgpt.assistant;

public class WakePhraseTest {
  public static void main(String[] args) {
    String[] yes = {
      "привет телевизор", "Привет, телевизор!", "ну привет телевизор", "окей телевизор"
    };
    String[] no = {
      "привет", "телевизор", "привет телевизору", "непривет телевизор", "включи телевизор", ""
    };
    for (String s : yes) if (!WakePhrase.matches(s)) throw new AssertionError(s);
    for (String s : no) if (WakePhrase.matches(s)) throw new AssertionError(s);
    System.out.println("WakePhraseTest: 10 checks passed");
  }
}
