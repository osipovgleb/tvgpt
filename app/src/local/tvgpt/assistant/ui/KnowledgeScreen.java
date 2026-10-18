package local.tvgpt.assistant.ui;

import android.app.*;
import android.content.*;
import android.widget.*;
import local.tvgpt.assistant.SettingsGlyph;
import local.tvgpt.assistant.data.*;
import org.json.*;

public final class KnowledgeScreen extends AsyncScreen {
  public KnowledgeScreen(Context c) {
    super(c, "Общие знания");
    observeData(local.tvgpt.assistant.AppDataEvents.KNOWLEDGE, this::refresh);
    refresh();
  }

  private void refresh() {
    load(
        () -> new KnowledgeRepository(getContext()).list(),
        notes -> {
          body.removeAllViews();
          body.addView(
              Screens.action(
                  getContext(), SettingsGlyph.PLUS, "Добавить знание", () -> edit(null)));
          if (notes.length() == 0)
            body.addView(
                Screens.text(
                    getContext(),
                    "Заметок пока нет. Можно попросить помощника запомнить нужное.",
                    16));
          for (int i = 0; i < notes.length(); i++) {
            JSONObject n = notes.optJSONObject(i);
            body.addView(
                new LibraryCard(
                        getContext(),
                        SettingsGlyph.BOOK,
                        n.optString("title"),
                        n.optString("text"),
                        "",
                        () -> edit(n))
                    .withKey("knowledge:" + n.optLong("id")));
          }
        });
  }

  private void edit(JSONObject note) {
    LinearLayout box = Screens.column(getContext(), "");
    EditText title = new EditText(getContext());
    title.setHint("Название");
    EditText text = new EditText(getContext());
    text.setHint("Что запомнить");
    text.setMinLines(3);
    if (note != null) {
      title.setText(note.optString("title"));
      text.setText(note.optString("text"));
    }
    box.addView(title);
    box.addView(text);
    AlertDialog d =
        new RightSheet.Builder(getContext())
            .setTitle(note == null ? "Новое знание" : "Изменить знание")
            .setView(box)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton(
                note == null ? "" : "Удалить",
                (v, w) -> {
                  if (note != null)
                    new RightSheet.Builder(getContext())
                        .setMessage("Удалить знание?")
                        .setPositiveButton(
                            "Удалить",
                            (a, z) ->
                                load(
                                    () -> {
                                      boolean ok =
                                          new KnowledgeRepository(getContext())
                                              .delete(note.optLong("id"), note.optLong("revision"));
                                      if (!ok)
                                        throw new IllegalStateException("Заметка уже изменена");
                                      return true;
                                    },
                                    ok -> refresh()))
                        .setNegativeButton("Отмена", null)
                        .show();
                })
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    button -> {
                      String t = title.getText().toString(), s = text.getText().toString();
                      try {
                        DataPolicy.note(t, s);
                      } catch (Exception e) {
                        text.setError(e.getMessage());
                        return;
                      }
                      IO.submit(
                          () -> {
                            try {
                              new KnowledgeRepository(getContext())
                                  .save(
                                      note == null ? 0 : note.optLong("id"),
                                      note == null ? 0 : note.optLong("revision"),
                                      t,
                                      s,
                                      "manual");
                              main.post(
                                  () -> {
                                    if (!disposed) {
                                      d.dismiss();
                                      refresh();
                                    }
                                  });
                            } catch (Exception e) {
                              main.post(
                                  () -> {
                                    if (!disposed) text.setError(e.getMessage());
                                  });
                            }
                          });
                    }));
    RightSheet.show(d);
  }
}
