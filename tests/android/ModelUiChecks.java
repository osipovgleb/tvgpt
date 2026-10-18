package local.tvgpt.assistant;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

final class ModelUiChecks {
  static void run(Instrumentation test, Bundle out, String restoreEffort) throws Exception {
    Context c = test.getTargetContext();
    Activity activity =
        test.startActivitySync(
            new Intent(c, MainActivity.class)
                .putExtra("section", "AUDIO")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    String original = ModelSettings.reasoning(c, "discussion");
    boolean fast = ModelSettings.fast(c);
    try {
      test.runOnMainSync(
          () -> {
            View label = find(activity.getWindow().getDecorView(), "Рассуждение при поручениях");
            if (label == null) throw new AssertionError("Reasoning setting absent");
            ((View) label.getParent().getParent()).performClick();
          });
      String selected = ModelSettings.reasoning(c, "discussion");
      if (selected.isEmpty() || selected.equals(original))
        throw new Exception("Reasoning control did not change saved selection");
      out.putString("selected_effort", selected);
      String answer =
          new Agent(c).run("Сколько будет два плюс два? Ответь одним словом, без инструментов.");
      if (answer.isEmpty()) throw new Exception("Reasoning request failed");
      out.putBoolean("reasoning_request_completed", true);
      test.runOnMainSync(
          () -> {
            View label = find(activity.getWindow().getDecorView(), "Скорость поручений и описаний");
            if (label == null) throw new AssertionError("Fast setting absent");
            ((View) label.getParent().getParent()).performClick();
          });
      if (ModelSettings.fast(c) == fast)
        throw new Exception("Fast control did not change preference");
      out.putBoolean("fast_control_persists", true);
    } finally {
      String restore = "auto".equals(restoreEffort) ? "" : original;
      ModelSettings.reasoning(c, "discussion", restore);
      ModelSettings.fast(c, fast);
      // Instrumentation can kill the process before apply() reaches disk.
      c.getSharedPreferences("models", 0)
          .edit()
          .putString("reasoning_discussion_" + ModelSettings.discussion(c), restore)
          .putBoolean("fast", fast)
          .commit();
    }
  }

  private static View find(View view, String label) {
    if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }
}
