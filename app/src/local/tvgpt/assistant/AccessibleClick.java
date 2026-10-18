package local.tvgpt.assistant;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/** Bounded semantic click lookup. Consumes the supplied root and releases all node handles. */
final class AccessibleClick {
  static boolean perform(AccessibilityNodeInfo root, String label) {
    return perform(root, label, CancellationToken.NONE);
  }

  static boolean perform(AccessibilityNodeInfo root, String label, CancellationToken token) {
    if (root == null) return false;
    List<AccessibilityNodeInfo> targets = new ArrayList<>();
    List<Integer> ranks = new ArrayList<>();
    int[] remaining = {2048};
    try {
      collectClickTargets(root, root, label, 0, remaining, targets, ranks);
      int[] scores = new int[ranks.size()];
      for (int i = 0; i < scores.length; i++) scores[i] = ranks.get(i);
      int chosen = AccessibilityPolicy.uniqueBest(scores);
      android.util.Log.i(
          "TVGPTAccessibility",
          "click candidates="
              + scores.length
              + " ambiguous="
              + (chosen < 0 && scores.length > 0)
              + " truncated="
              + (remaining[0] <= 0));
      token.check();
      // A truncated tree cannot establish that a label identifies only one target.
      return remaining[0] > 0
          && chosen >= 0
          && targets.get(chosen).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    } finally {
      for (AccessibilityNodeInfo target : targets) target.recycle();
      root.recycle();
    }
  }

  private static void collectClickTargets(
      AccessibilityNodeInfo node,
      AccessibilityNodeInfo root,
      String label,
      int depth,
      int[] remaining,
      List<AccessibilityNodeInfo> targets,
      List<Integer> ranks) {
    if (node == null || --remaining[0] < 0) return;
    if (depth > 32) {
      remaining[0] = 0;
      return;
    }
    if (node.isPassword()) return;
    if (node.isVisibleToUser() && node.isEnabled()) {
      int rank = AccessibilityPolicy.labelRank(label, node.getText(), node.getContentDescription());
      AccessibilityNodeInfo target = rank == 0 ? null : clickTarget(node, root);
      if (target != null) {
        int existing = targets.indexOf(target);
        if (existing >= 0) {
          ranks.set(existing, Math.max(rank, ranks.get(existing)));
          target.recycle();
        } else {
          targets.add(target);
          ranks.add(rank);
        }
      }
    }
    for (int i = 0; i < node.getChildCount() && remaining[0] > 0; i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      try {
        collectClickTargets(child, root, label, depth + 1, remaining, targets, ranks);
      } finally {
        if (child != null) child.recycle();
      }
    }
  }

  private static AccessibilityNodeInfo clickTarget(
      AccessibilityNodeInfo node, AccessibilityNodeInfo root) {
    AccessibilityNodeInfo target = AccessibilityNodeInfo.obtain(node);
    try {
      for (int i = 0; target != null && i < 4; i++) {
        if (target.equals(root)
            || target.getWindowId() != root.getWindowId()
            || !Objects.equals(target.getPackageName(), root.getPackageName())) return null;
        if (target.isVisibleToUser()
            && target.isEnabled()
            && !target.isPassword()
            && !target.isEditable()
            && !"android:id/content".equals(target.getViewIdResourceName())
            && (target.isClickable()
                || target
                    .getActionList()
                    .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)))
          return AccessibilityNodeInfo.obtain(target);
        AccessibilityNodeInfo parent = target.getParent();
        target.recycle();
        target = parent;
      }
      return null;
    } finally {
      if (target != null) target.recycle();
    }
  }
}
