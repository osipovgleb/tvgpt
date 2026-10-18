package local.tvgpt.assistant;

import android.view.accessibility.AccessibilityNodeInfo;

public final class TvNavigationResourceTest {
  public static void main(String[] args) {
    TvAccessibility service = new TvAccessibility();
    AccessibilityNodeInfo root = new AccessibilityNodeInfo();
    service.root = root;
    if (service.navigate("left")) throw new AssertionError("navigated without focus");
    if (root.recycles != 1) throw new AssertionError("no-focus navigation leaked root");
    AccessibilityNodeInfo focused = new AccessibilityNodeInfo(), next = new AccessibilityNodeInfo();
    root.focused = focused;
    focused.next = next;
    root.recycles = 0;
    if (!service.navigate("right")) throw new AssertionError("navigation failed");
    if (root.recycles != 1 || focused.recycles != 1 || next.recycles != 1)
      throw new AssertionError("navigation leaked root/focused/next handles");
    System.out.println("TvNavigationResourceTest passed");
  }
}
