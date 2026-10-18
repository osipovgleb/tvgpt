package local.tvgpt.assistant.ui;

import android.app.*;
import android.content.Context;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import local.tvgpt.assistant.data.*;
import org.json.*;

public final class ConversationsScreen extends AsyncScreen {
  private boolean observing;
  private final android.content.BroadcastReceiver updates =
      new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent intent) {
          if (selected == null) refresh();
          else showTurns(turnOffset);
        }
      };

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    disposed = false;
    android.content.IntentFilter filter =
        new android.content.IntentFilter(local.tvgpt.assistant.ArchiveEvents.CHANGED);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      getContext().registerReceiver(updates, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
    else getContext().registerReceiver(updates, filter);
    observing = true;
  }

  @Override
  protected void onDetachedFromWindow() {
    if (observing) {
      getContext().unregisterReceiver(updates);
      observing = false;
    }
    super.onDetachedFromWindow();
  }

  private int offset;
  private int turnOffset;
  private String selected;

  public ConversationsScreen(Context c) {
    super(c, "Разговоры");
    refresh();
  }

  private String date(long millis) {
    return millis > 0
        ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(new Date(millis))
        : "время не сохранено";
  }

  private void refresh() {
    selected = null;
    load(
        () -> new ConversationRepository(getContext()).recent(20, offset),
        items -> {
          body.removeAllViews();
          if (items.length() == 0)
            body.addView(Screens.text(getContext(), "Сохранённых разговоров пока нет.", 16));
          for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            String label = item.optString("title");
            if (label.isEmpty()) label = "Разговор";
            final String id = item.optString("id");
            body.addView(
                Screens.button(
                    getContext(),
                    label
                        + " · "
                        + date(item.optLong("started"))
                        + " — "
                        + date(item.optLong("ended")),
                    () -> {
                      selected = id;
                      showTurns(0);
                    }));
            body.addView(
                Screens.text(
                    getContext(),
                    item.optString("summary").isEmpty()
                        ? "Отрывок: " + item.optString("excerpt", "Описание ещё не подготовлено")
                        : "Описание: " + item.optString("summary"),
                    14));
            if (item.optString("summary_state").equals("failed"))
              body.addView(
                  Screens.button(
                      getContext(),
                      "Повторить создание описания",
                      () -> {
                        local.tvgpt.assistant.ConversationSummarizer.request(getContext(), id);
                        status.setText("Описание запрошено. Обновите список через минуту.");
                      }));
          }
          if (offset > 0)
            body.addView(
                Screens.button(
                    getContext(),
                    "Предыдущие",
                    () -> {
                      offset = Math.max(0, offset - 20);
                      refresh();
                    }));
          if (items.length() == 20)
            body.addView(
                Screens.button(
                    getContext(),
                    "Следующие",
                    () -> {
                      offset += 20;
                      refresh();
                    }));
        });
  }

  private void showTurns(int page) {
    turnOffset = page;
    String id = selected;
    load(
        () -> new ConversationRepository(getContext()).turns(id, 30, page),
        items -> {
          body.removeAllViews();
          body.addView(Screens.button(getContext(), "К списку разговоров", this::refresh));
          body.addView(
              Screens.button(
                  getContext(),
                  "Удалить разговор",
                  () ->
                      new RightSheet.Builder(getContext())
                          .setMessage("Удалить сохранённую историю этого разговора?")
                          .setPositiveButton(
                              "Удалить",
                              (a, w) ->
                                  load(
                                      () -> {
                                        new ConversationRepository(getContext()).delete(id);
                                        return true;
                                      },
                                      ok -> refresh()))
                          .setNegativeButton("Отмена", null)
                          .show()));
          for (int i = 0; i < items.length(); i++) {
            JSONObject t = items.optJSONObject(i);
            TextView message =
                Screens.text(
                    getContext(),
                    (t.optString("role").equals("user") ? "Вы" : "GPT")
                        + " · "
                        + date(t.optLong("at"))
                        + "\n"
                        + t.optString("text"),
                    16);
            if (android.text.util.Linkify.addLinks(message, android.text.util.Linkify.WEB_URLS))
              message.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            body.addView(message);
          }
          if (page > 0)
            body.addView(
                Screens.button(getContext(), "Ранее", () -> showTurns(Math.max(0, page - 30))));
          if (items.length() == 30)
            body.addView(Screens.button(getContext(), "Далее", () -> showTurns(page + 30)));
        });
  }
}
