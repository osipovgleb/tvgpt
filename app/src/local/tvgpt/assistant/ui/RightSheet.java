package local.tvgpt.assistant.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;

/** Shared placement for editors, confirmation dialogs and date/time pickers on TV. */
public final class RightSheet {
  private RightSheet() {}

  public static void show(Dialog dialog) {
    dialog.show();
    Window window = dialog.getWindow();
    if (window == null) return;
    Context c = dialog.getContext();
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    View decor = window.getDecorView();
    decor.setBackground(Screens.rounded(c, 0xff1c1c1e, 20));
    decor.setPadding(Screens.dp(c, 12), Screens.dp(c, 12), Screens.dp(c, 12), Screens.dp(c, 12));
    WindowManager.LayoutParams p = window.getAttributes();
    p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
    p.x = Screens.dp(c, 20);
    p.width =
        Math.min(
            Screens.dp(c, 440),
            c.getResources().getDisplayMetrics().widthPixels - Screens.dp(c, 40));
    p.height = WindowManager.LayoutParams.WRAP_CONTENT;
    p.dimAmount = .4f;
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    window.setAttributes(p);
  }

  public static final class Builder extends AlertDialog.Builder {
    public Builder(Context context) {
      super(context);
    }

    @Override
    public AlertDialog show() {
      AlertDialog dialog = create();
      RightSheet.show(dialog);
      return dialog;
    }
  }
}
