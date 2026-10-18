package android.view.accessibility;

public class AccessibilityNodeInfo {
  public Runnable onFind;
  public String pkg = "com.test";
  public AccessibilityNodeInfo focused, next;
  public boolean editable, password, enabled = true, visible = true, hasFocus = true, fail;
  public int recycles, actions;

  public CharSequence getPackageName() {
    return pkg;
  }

  public AccessibilityNodeInfo findFocus(int which) {
    if (onFind != null) onFind.run();
    return focused;
  }

  public AccessibilityNodeInfo focusSearch(int direction) {
    return next;
  }

  public boolean isEditable() {
    return editable;
  }

  public boolean isPassword() {
    return password;
  }

  public boolean isVisibleToUser() {
    return visible;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isFocused() {
    return hasFocus;
  }

  public boolean performAction(int action) {
    actions++;
    if (fail) throw new IllegalStateException("failed node");
    return true;
  }

  public boolean performAction(int action, android.os.Bundle args) {
    return performAction(action);
  }

  public void recycle() {
    recycles++;
  }
}
