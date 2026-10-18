package local.tvgpt.assistant;

import android.view.accessibility.AccessibilityNodeInfo;

/** Bounded, password-safe fresh window observation. Consumes the supplied root. */
final class AccessibleScreen {
  static org.json.JSONObject read(AccessibilityNodeInfo root) throws Exception {
    org.json.JSONObject result = new org.json.JSONObject();
    if (root == null)
      return result.put("node_count", 0).put("labeled_node_count", 0).put("tree_status", "no_root");
    AccessibilityNodeInfo focused = null;
    try {
      result.put("package", root.getPackageName() == null ? "" : root.getPackageName().toString());
      StringBuilder text = new StringBuilder();
      int[] counts = {0, 0, 0, 0};
      describe(root, text, 0, counts);
      result.put("text", text.toString());
      result
          .put("node_count", counts[0])
          .put("visible_node_count", counts[1])
          .put("labeled_node_count", counts[2])
          .put("tree_truncated", counts[3] != 0)
          .put("tree_status", counts[2] == 0 ? "no_visible_labels" : "labeled");
      android.util.Log.i(
          "TVGPTAccessibility",
          "tree package="
              + result.optString("package")
              + " nodes="
              + counts[0]
              + " visible="
              + counts[1]
              + " labeled="
              + counts[2]
              + " truncated="
              + (counts[3] != 0));
      focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (focused != null && !focused.isPassword()) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        focused.getBoundsInScreen(bounds);
        result.put(
            "focus",
            bounds.toShortString()
                + " "
                + focused.getText()
                + " "
                + focused.getContentDescription());
      }
      return result;
    } finally {
      if (focused != null) focused.recycle();
      root.recycle();
    }
  }

  static String screen(AccessibilityNodeInfo root) {
    try {
      org.json.JSONObject observed = read(root);
      if (!observed.optString("text").trim().isEmpty()) return observed.toString();
      return observed
          .put("requires_visual", true)
          .put(
              "instruction",
              "В текущем наблюдении нет видимых подписей; это не доказывает отсутствие дерева у"
                  + " приложения. Для определения содержимого нужен screenshot. Простую команду"
                  + " стрелки можно отправить без изображения; выбор видео и результат поиска"
                  + " требуют просмотра.")
          .toString();
    } catch (Exception e) {
      return "Состояние экрана недоступно";
    }
  }

  private static void describe(AccessibilityNodeInfo n, StringBuilder s, int depth, int[] counts) {
    if (n == null) return;
    if (depth > 32 || s.length() >= 14000 || counts[0] >= 2048) {
      counts[3] = 1;
      return;
    }
    counts[0]++;
    if (n.isPassword()) return;
    if (n.isVisibleToUser()) {
      counts[1]++;
      if ((n.getText() != null && n.getText().length() > 0)
          || (n.getContentDescription() != null && n.getContentDescription().length() > 0))
        counts[2]++;
      if (n.getText() != null && n.getText().length() > 0) s.append(n.getText()).append("\n");
      if (n.getContentDescription() != null && n.getContentDescription().length() > 0)
        s.append(n.getContentDescription()).append("\n");
    }
    if (s.length() > 14000) {
      s.setLength(14000);
      counts[3] = 1;
    }
    for (int i = 0; i < n.getChildCount() && counts[3] == 0; i++) {
      AccessibilityNodeInfo c = n.getChild(i);
      try {
        describe(c, s, depth + 1, counts);
      } finally {
        if (c != null) c.recycle();
      }
    }
  }
}
