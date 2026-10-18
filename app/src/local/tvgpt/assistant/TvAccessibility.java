package local.tvgpt.assistant;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.*;
import java.util.*;

public class TvAccessibility extends AccessibilityService {
  static volatile TvAccessibility current;
  private VoicePanel overlay;
  private WindowManager windowManager;
  private WindowManager.LayoutParams overlayParams;
  private Object sessionOwner;
  private Runnable sessionExit;
  private AccessibilityNodeInfo previousSearchFocus;

  @Override
  public void onServiceConnected() {
    current = this;
  }

  @Override
  public boolean onUnbind(android.content.Intent intent) {
    // A disconnected service may outlive its accessibility window token.
    if (current == this) current = null;
    hideVoiceOverlay();
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    hideVoiceOverlay();
    ReminderPopup.dismiss(this);
    if (current == this) current = null;
    super.onDestroy();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent e) {}

  @Override
  public void onInterrupt() {}

  void showVoiceOverlay(Object owner, Runnable exit) {
    if (overlay != null && overlay.failed()) hideVoiceOverlay(false);
    sessionOwner = owner;
    sessionExit = exit;
    if (overlay != null) return;
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    overlay = new VoicePanel(this, () -> hideVoiceOverlay(), true);
    overlayParams =
        new WindowManager.LayoutParams(
            (int) (getResources().getDisplayMetrics().density * 103),
            -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
    overlayParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;
    overlayParams.x = 24;
    overlayParams.y = 24;
    windowManager.addView(overlay, overlayParams);
    overlay.begin();
  }

  void hideVoiceOverlay() {
    hideVoiceOverlay(true);
  }

  void detachVoiceSessionForHome() {
    if (overlay == null || sessionOwner == null) return;
    Runnable finish = sessionExit;
    sessionOwner = null;
    sessionExit = null;
    android.util.Log.i("TVGPTSession", "Detached system session for HOME; overlay retained");
    if (finish != null) finish.run();
  }

  boolean ownsVoiceOverlay(Object owner) {
    return sessionOwner == owner && overlay != null && !overlay.failed();
  }

  void hideVoiceOverlay(Object owner) {
    if (sessionOwner == owner) hideVoiceOverlay(false);
  }

  private void hideVoiceOverlay(boolean finishSession) {
    if (overlay == null) return;
    VoicePanel old = overlay;
    overlay = null;
    old.close();
    try {
      windowManager.removeView(old);
    } catch (IllegalArgumentException ignored) {
    }
    Runnable exit = sessionExit;
    sessionExit = null;
    sessionOwner = null;
    if (finishSession && exit != null) exit.run();
  }

  void restoreRemoteFocus() {
    if (overlay != null) {
      overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
      windowManager.updateViewLayout(overlay, overlayParams);
    }
  }

  void releaseAppFocus() {
    if (overlay != null) {
      overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
      windowManager.updateViewLayout(overlay, overlayParams);
    }
  }

  void restoreOverlayFocus() {
    new android.os.Handler(getMainLooper())
        .postDelayed(
            () -> {
              if (overlay != null) {
                overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(overlay, overlayParams);
              }
            },
            300);
  }

  private AccessibilityNodeInfo appRoot() {
    AccessibilityNodeInfo selected = null;
    int bestRank = -1;
    for (AccessibilityWindowInfo w : getWindows()) {
      try {
        if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
        AccessibilityNodeInfo root = w.getRoot();
        if (root == null) continue;
        int rank = AccessibilityPolicy.windowRank(w.isFocused(), w.isActive());
        // Settings can be the foreground app; the nonfocusable orb is not an app window.
        if (rank > bestRank) {
          if (selected != null) selected.recycle();
          selected = root;
          bestRank = rank;
        } else root.recycle();
      } finally {
        w.recycle();
      }
    }
    return selected == null ? getRootInActiveWindow() : selected;
  }

  String currentAppPackage() {
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return "";
    try {
      return root.getPackageName() == null ? "" : root.getPackageName().toString();
    } finally {
      root.recycle();
    }
  }

  // Null requires the IME fallback to recheck the originally captured editor.
  String setFocusedText(String text, boolean submit) {
    return setFocusedText(text, submit, null, CancellationToken.NONE);
  }

  String setFocusedText(
      String text, boolean submit, AccessibilityNodeInfo expected, CancellationToken token) {
    token.check();
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return null;
    AccessibilityNodeInfo focused = null;
    try {
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if (!usableEditor(focused)
          || !Objects.equals(root.getPackageName(), focused.getPackageName())
          || (expected != null && !focused.equals(expected))) return null;
      token.check();
      android.os.Bundle args = new android.os.Bundle();
      args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
      if (!focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return null;
      token.check();
      boolean submitted =
          submit
              && focused.performAction(
                  AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
      return "Текст передан сфокусированному полю; отображение не проверено. Отправка поиска: "
          + (submitted ? "команда принята полем" : submit ? "не подтверждена" : "не запрошена");
    } finally {
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }

  void resetSearchProbe() {
    if (previousSearchFocus != null) previousSearchFocus.recycle();
    previousSearchFocus = null;
  }

  private boolean recognizedSearchEditor(AccessibilityNodeInfo focused, boolean searchOpened) {
    return usableEditor(focused)
        && (searchNode(focused) || (searchOpened && !focused.equals(previousSearchFocus)));
  }

  boolean hasFocusedSearchEditor(String targetPackage, boolean searchOpened) {
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return false;
    AccessibilityNodeInfo focused = null;
    try {
      if (!targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()))
        return false;
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      return recognizedSearchEditor(focused, searchOpened);
    } finally {
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }

  AccessibilityNodeInfo captureSearchEditor(String targetPackage, boolean searchOpened) {
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return null;
    AccessibilityNodeInfo focused = null;
    try {
      if (!targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()))
        return null;
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if (!recognizedSearchEditor(focused, searchOpened)
          || !Objects.equals(root.getPackageName(), focused.getPackageName())) return null;
      AccessibilityNodeInfo snapshot = focused;
      focused = null;
      return snapshot;
    } finally {
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }

  AccessibilityNodeInfo captureFocusedEditor(String targetPackage) {
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return null;
    AccessibilityNodeInfo focused = null;
    try {
      if (!targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()))
        return null;
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if (!usableEditor(focused)
          || !Objects.equals(root.getPackageName(), focused.getPackageName())) return null;
      AccessibilityNodeInfo snapshot = focused;
      focused = null;
      return snapshot;
    } finally {
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }

  boolean sameFocusedEditor(String targetPackage, AccessibilityNodeInfo expected) {
    if (expected == null) return false;
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return false;
    AccessibilityNodeInfo focused = null;
    try {
      if (!targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()))
        return false;
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      return usableEditor(focused)
          && Objects.equals(root.getPackageName(), focused.getPackageName())
          && focused.equals(expected);
    } finally {
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }

  // 0: absent; 1: search button clicked; 2: ready editor; 3: known editor focus requested.
  int prepareSearch(String targetPackage, boolean searchOpened) {
    return prepareSearch(targetPackage, searchOpened, CancellationToken.NONE);
  }

  int prepareSearch(String targetPackage, boolean searchOpened, CancellationToken token) {
    token.check();
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return 0;
    try {
      if (!targetPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()))
        return 0;
      AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      try {
        if (recognizedSearchEditor(focused, searchOpened)) return 2;
      } finally {
        if (focused != null) focused.recycle();
      }
      if (searchOpened) return 0;
      int[] remaining = {400};
      AccessibilityNodeInfo before = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      try {
        int state = openSearchNode(root, 0, remaining, token);
        if (state == 1) {
          resetSearchProbe();
          previousSearchFocus = before;
          before = null;
        }
        return state;
      } finally {
        if (before != null) before.recycle();
      }
    } finally {
      root.recycle();
    }
  }

  private boolean usableEditor(AccessibilityNodeInfo node) {
    return node != null
        && node.isFocused()
        && node.isEditable()
        && !node.isPassword()
        && node.isVisibleToUser()
        && node.isEnabled();
  }

  private boolean searchNode(AccessibilityNodeInfo node) {
    return AppSearchPolicy.matches(
        node.getViewIdResourceName(),
        node.getText(),
        node.getHintText(),
        node.getContentDescription());
  }

  private int openSearchNode(
      AccessibilityNodeInfo node, int depth, int[] remaining, CancellationToken token) {
    if (node == null || depth > 16 || --remaining[0] < 0) return 0;
    if (node.isVisibleToUser() && node.isEnabled() && !node.isPassword() && searchNode(node)) {
      if (node.isEditable()) {
        token.check();
        boolean focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        token.check();
        boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (focused || clicked) return 3;
      } else if (clickSearchButton(node, token)) {
        return 1;
      }
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      try {
        int state = openSearchNode(child, depth + 1, remaining, token);
        if (state != 0) return state;
      } finally {
        if (child != null) child.recycle();
      }
    }
    return 0;
  }

  private boolean clickSearchButton(AccessibilityNodeInfo node, CancellationToken token) {
    token.check();
    if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
    CharSequence targetPackage = node.getPackageName();
    if (targetPackage == null) return false;
    AccessibilityNodeInfo parent = node.getParent();
    try {
      for (int i = 0; parent != null && i < 3; i++) {
        if (!targetPackage.equals(parent.getPackageName())) return false;
        AccessibilityNodeInfo next = parent.getParent();
        try {
          // A matching child label must never turn into a click on the whole app root.
          if (next == null) return false;
          token.check();
          boolean clicked =
              parent.isVisibleToUser()
                  && parent.isEnabled()
                  && !parent.isEditable()
                  && !parent.isPassword()
                  && parent.isClickable()
                  && !"android:id/content".equals(parent.getViewIdResourceName())
                  && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
          parent.recycle();
          parent = next;
          next = null;
          if (clicked) return true;
        } finally {
          if (next != null) next.recycle();
        }
      }
      return false;
    } finally {
      if (parent != null) parent.recycle();
    }
  }

  org.json.JSONObject observation() throws Exception {
    return AccessibleScreen.read(appRoot());
  }

  String screen() {
    return AccessibleScreen.screen(appRoot());
  }

  boolean click(String label) {
    return click(label, CancellationToken.NONE);
  }

  boolean click(String label, CancellationToken token) {
    return AccessibleClick.perform(appRoot(), label, token);
  }

  boolean tap(int x, int y) {
    return tap(x, y, CancellationToken.NONE);
  }

  boolean tap(int x, int y, CancellationToken token) {
    token.check();
    if (ScreenCapture.imageWidth == 0
        || x < 0
        || y < 0
        || x >= ScreenCapture.imageWidth
        || y >= ScreenCapture.imageHeight) return false;
    android.graphics.Path path = new android.graphics.Path();
    path.moveTo(
        x * ScreenCapture.displayWidth / (float) ScreenCapture.imageWidth,
        y * ScreenCapture.displayHeight / (float) ScreenCapture.imageHeight);
    token.check();
    return dispatchGesture(
        new android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
            .build(),
        null,
        null);
  }

  boolean navigate(String direction) {
    return navigate(direction, CancellationToken.NONE);
  }

  boolean navigate(String direction, CancellationToken token) {
    token.check();
    if (direction.equals("home") || direction.equals("back")) {
      if (direction.equals("home")) detachVoiceSessionForHome();
      releaseAppFocus();
      token.check();
      boolean done =
          performGlobalAction(direction.equals("home") ? GLOBAL_ACTION_HOME : GLOBAL_ACTION_BACK);
      restoreOverlayFocus();
      return done;
    }
    AccessibilityNodeInfo root = appRoot();
    if (root == null) return false;
    AccessibilityNodeInfo focused = null, next = null;
    try {
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (focused == null) return false;
      token.check();
      if (direction.equals("select"))
        return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
      int d =
          direction.equals("left")
              ? View.FOCUS_LEFT
              : direction.equals("right")
                  ? View.FOCUS_RIGHT
                  : direction.equals("up") ? View.FOCUS_UP : View.FOCUS_DOWN;
      next = focused.focusSearch(d);
      token.check();
      return next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    } finally {
      if (next != null) next.recycle();
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }
}
