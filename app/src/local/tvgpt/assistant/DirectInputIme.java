package local.tvgpt.assistant;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Local Unicode transport. Android alone can bind this service; no broadcast text endpoint. */
public final class DirectInputIme extends InputMethodService {
  static volatile DirectInputIme current;
  static volatile DirectInputRequest pending;
  private boolean inputStarted;

  @Override
  public void onCreate() {
    super.onCreate();
    current = this;
  }

  @Override
  public void onDestroy() {
    if (current == this) current = null;
    super.onDestroy();
  }

  @Override
  public void onStartInput(EditorInfo info, boolean restarting) {
    super.onStartInput(info, restarting);
    inputStarted = true;
    deliverPending();
  }

  @Override
  public void onFinishInput() {
    inputStarted = false;
    super.onFinishInput();
  }

  @Override
  public boolean onEvaluateInputViewShown() {
    return false;
  }

  void deliverPending() {
    DirectInputRequest request = pending;
    EditorInfo editor = getCurrentInputEditorInfo();
    InputConnection connection = getCurrentInputConnection();
    if (!inputStarted || request == null || editor == null || connection == null) return;
    int inputClass = editor.inputType & android.text.InputType.TYPE_MASK_CLASS;
    int variation = editor.inputType & android.text.InputType.TYPE_MASK_VARIATION;
    boolean password =
        (inputClass == android.text.InputType.TYPE_CLASS_TEXT
                && (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            || (inputClass == android.text.InputType.TYPE_CLASS_NUMBER
                && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    if (password) {
      request.deliver(
          editor.packageName,
          SystemClock.elapsedRealtime(),
          (text, submit) -> "Поле пароля не используется для поиска; ввод отменён");
      return;
    }
    if (editor.inputType == android.text.InputType.TYPE_NULL) {
      request.deliver(
          editor.packageName,
          SystemClock.elapsedRealtime(),
          (text, submit) ->
              "Прямой ввод недоступен: приложение предоставляет TYPE_NULL вместо текстового"
                  + " редактора. Используй search_app для поиска в приложении");
      return;
    }
    request.deliver(
        editor.packageName,
        SystemClock.elapsedRealtime(),
        (text, submit) -> {
          if (!connection.commitText(text, 1)) return "Редактор отклонил прямой ввод";
          boolean submitted = false;
          if (submit) {
            int action = editor.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED)
              action = EditorInfo.IME_ACTION_SEARCH;
            submitted = connection.performEditorAction(action);
          }
          return "Текст передан редактору напрямую; отображение не проверено. Отправка поиска: "
              + (submitted
                  ? "команда принята редактором"
                  : submit ? "не подтверждена" : "не запрошена");
        });
  }
}
