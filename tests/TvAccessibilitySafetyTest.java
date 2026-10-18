package local.tvgpt.assistant;

import android.view.accessibility.AccessibilityNodeInfo;

public final class TvAccessibilitySafetyTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    TvAccessibility service = new TvAccessibility();
    AccessibilityNodeInfo root = new AccessibilityNodeInfo();
    service.root = root;
    AccessibilityNodeInfo editor = new AccessibilityNodeInfo();
    editor.editable = true;
    root.focused = editor;
    editor.password = true;
    check(
        service.setFocusedText("secret", false) == null && editor.actions == 0,
        "typed password through accessibility");
    check(service.captureFocusedEditor("com.test") == null, "captured password");
    editor.password = false;
    AccessibilityNodeInfo expected = service.captureFocusedEditor("com.test");
    check(
        expected == editor && service.sameFocusedEditor("com.test", expected),
        "safe editor rejected");
    AccessibilityNodeInfo replacement = new AccessibilityNodeInfo();
    replacement.editable = true;
    root.focused = replacement;
    check(!service.sameFocusedEditor("com.test", expected), "same-app editor replacement accepted");
    check(
        service.setFocusedText("text", false, expected, CancellationToken.NONE) == null
            && replacement.actions == 0,
        "replacement editor mutated");
    replacement.password = true;
    check(service.captureFocusedEditor("com.test") == null, "replacement password accepted");
    replacement.password = false;
    replacement.pkg = "other.app";
    check(service.captureFocusedEditor("com.test") == null, "foreign editor accepted");
    check(
        service.setFocusedText("text", false) == null && replacement.actions == 0,
        "foreign editor mutated");
    root.focused = null;
    root.recycles = 0;
    check(!service.navigate("left") && root.recycles == 1, "no-focus root leaked");
    root.focused = editor;
    root.recycles = editor.recycles = 0;
    AccessibilityNodeInfo next = new AccessibilityNodeInfo();
    editor.next = next;
    check(service.navigate("right"), "navigation failed");
    check(
        root.recycles == 1 && editor.recycles == 1 && next.recycles == 1,
        "navigation handles leaked");
    root.recycles = editor.recycles = 0;
    editor.fail = true;
    try {
      service.navigate("select");
      throw new AssertionError("missing failure");
    } catch (IllegalStateException expectedFailure) {
    }
    check(root.recycles == 1 && editor.recycles == 1, "failed action handles leaked");
    editor.fail = false;
    CancellationToken cancelled = new CancellationToken();
    root.onFind = cancelled::cancel;
    int beforeActions = editor.actions;
    try {
      service.navigate("select", cancelled);
      throw new AssertionError("cancelled read dispatched node action");
    } catch (java.util.concurrent.CancellationException expectedCancellation) {
    }
    check(editor.actions == beforeActions, "node action dispatched after cancelled read");
    root.onFind = null;
    TvAccessibility.current = service;
    service.onUnbind(null);
    check(
        TvAccessibility.current == null,
        "Disconnected accessibility instance remained available for overlay creation");
    System.out.println("TvAccessibilitySafetyTest passed");
  }
}
