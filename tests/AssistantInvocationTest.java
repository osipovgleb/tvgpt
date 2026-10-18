package local.tvgpt.assistant;

public class AssistantInvocationTest {
  public static void main(String[] args) {
    AssistantInvocation gate = new AssistantInvocation();
    if (gate.ignoreRestart("", 1000)) throw new AssertionError("First key ignored");
    gate.hidden(2000);
    if (!gate.ignoreRestart("", 2150))
      throw new AssertionError("TCL hide/show pair reopened instead of ending");
    if (gate.ignoreRestart("", 3000)) throw new AssertionError("Next separate key ignored");
    gate.hidden(4000);
    if (gate.ignoreRestart("wake", 4100)) throw new AssertionError("Voice wake must still open");
    if (gate.ignoreRestart("launch", 4100)) throw new AssertionError("Launcher must still open");
    System.out.println("AssistantInvocationTest: normal calls and TCL duplicate hide/show passed");
  }
}
