package local.tvgpt.assistant;

import local.tvgpt.assistant.data.DataPolicy;

public class DataPolicyTest {
  public static void main(String[] args) {
    if (!DataPolicy.turnKey("call", "event", 0).equals(DataPolicy.turnKey("call", "event", 99)))
      throw new AssertionError("event dedupe");
    if (DataPolicy.turnKey("call", "a", 0).equals(DataPolicy.turnKey("call", "b", 0)))
      throw new AssertionError("distinct turns");
    DataPolicy.note("Заметка", "Текст");
    try {
      DataPolicy.note("x", new String(new char[4001]));
      throw new AssertionError("limit");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println("Data policy tests passed");
  }
}
