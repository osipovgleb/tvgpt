package android.accessibilityservice;

import android.view.accessibility.*;
import java.util.*;

public class AccessibilityService extends android.content.Context {
  public AccessibilityNodeInfo root;

  public List<AccessibilityWindowInfo> getWindows() {
    return Collections.emptyList();
  }

  public AccessibilityNodeInfo getRootInActiveWindow() {
    return root;
  }

  public void onServiceConnected() {}

  public void onDestroy() {}

  public boolean onUnbind(android.content.Intent intent) {
    return false;
  }

  public void onAccessibilityEvent(AccessibilityEvent event) {}

  public void onInterrupt() {}

  public boolean performGlobalAction(int action) {
    return true;
  }
}
