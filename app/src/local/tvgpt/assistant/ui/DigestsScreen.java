package local.tvgpt.assistant.ui;

import android.content.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import local.tvgpt.assistant.*;
import local.tvgpt.assistant.data.*;
import local.tvgpt.assistant.digests.*;
import org.json.*;

public final class DigestsScreen extends AsyncScreen {
  private String selected = "";
  private final BroadcastReceiver updates =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (!disposed) refresh();
        }
      };

  public DigestsScreen(Context c) {
    this(c, "");
  }

  public DigestsScreen(Context c, String id) {
    super(c, "");
    selected = id == null ? "" : id;
    refresh();
  }

  public void refresh() {
    load(
        () -> {
          DigestRepository repo = new DigestRepository(getContext());
          return new JSONObject()
              .put("items", repo.list("digest", ""))
              .put("cards", selected.isEmpty() ? new JSONArray() : repo.cards(selected))
              .put("selected", selected.isEmpty() ? JSONObject.NULL : repo.read("digest", selected))
              .put("job", selected.isEmpty() ? JSONObject.NULL : repo.latestJob(selected));
        },
        this::render);
  }

  private void render(JSONObject result) {
    body.removeAllViews();
    status.setVisibility(GONE);
    JSONObject chosen = result.optJSONObject("selected");
    JSONArray digests = result.optJSONArray("items");
    if (chosen == null) {
      renderDigestList(digests);
      return;
    }
    body.addView(
        Screens.action(
            getContext(),
            SettingsGlyph.BACK,
            "Все подборки",
            () -> {
              selected = "";
              refresh();
            }));
    SettingsRows rows = new SettingsRows(getContext());
    LinearLayout group = rows.group(body, "");
    rows.row(
        group,
        SettingsGlyph.BOOK,
        chosen.optString("title"),
        chosen.optString("topics"),
        () -> DigestSheet.open(getContext(), "digest", selected, this::refresh));
    rows.row(group, SettingsGlyph.LINK, "Источники", "Сайты и RSS", () -> manage("source"));
    rows.row(
        group,
        SettingsGlyph.CLOCK,
        "Расписания",
        "Обновление по времени",
        () -> manage("schedule"));
    rows.row(
        group,
        SettingsGlyph.REFRESH,
        "Обновить сейчас",
        jobLabel(result.optJSONObject("job")),
        () -> update());
    rows.row(
        group,
        SettingsGlyph.PLAY,
        "Посмотреть на весь экран",
        "",
        () ->
            getContext()
                .startActivity(
                    new Intent(getContext(), DigestPreviewActivity.class)
                        .putExtra("digest_id", selected)));
    JSONArray cards = result.optJSONArray("cards");
    try {
      if (cards != null) cards = DigestSlides.latest(cards);
    } catch (Exception ignored) {
    }
    Set<String> stories = new HashSet<>();
    if (cards != null)
      for (int i = 0; i < cards.length() && stories.size() < 12; i++) {
        JSONObject a = cards.optJSONObject(i);
        String key = a.optString("story_key", a.optString("url"));
        if (!stories.add(key)) continue;
        DigestCard card = new DigestCard(getContext(), a);
        card.setOnClickListener(v -> article(a));
        body.addView(card);
      }
  }

  private void renderDigestList(JSONArray digests) {
    selected = "";
    body.addView(
        Screens.action(
            getContext(),
            SettingsGlyph.PLUS,
            "Создать подборку",
            () -> DigestSheet.open(getContext(), "digest", "", this::refresh)));
    if (digests != null)
      for (int i = 0; i < digests.length(); i++) {
        JSONObject d = digests.optJSONObject(i);
        body.addView(
            new LibraryCard(
                getContext(),
                SettingsGlyph.NEWS,
                d.optString("title"),
                d.optString("topics"),
                "",
                () -> {
                  selected = d.optString("id");
                  refresh();
                }));
      }
    body.addView(
        Screens.action(
            getContext(),
            SettingsGlyph.PALETTE,
            "Выбрать новостную заставку",
            this::chooseScreensaver));
  }

  private String jobLabel(JSONObject job) {
    if (job == null) return "Ещё не обновлялось";
    String state = job.optString("state");
    String value;
    switch (state) {
      case "queued":
      case "waiting":
        value = "Ожидает обновления";
        break;
      case "running":
        value = "Обновляется";
        break;
      case "ready":
        value = "Выпуск готов";
        break;
      case "partial":
        value = "Частично обновлено";
        break;
      case "unconfirmed":
        value = "Результат не подтверждён";
        break;
      case "requires-login":
        value = "Требуется вход";
        break;
      case "cancelled":
        value = "Отменено";
        break;
      default:
        value = "Не удалось обновить";
    }
    return value;
  }

  private void manage(String kind) {
    load(
        () -> new DigestRepository(getContext()).list(kind, selected),
        items -> {
          String[] labels = new String[items.length() + 1];
          labels[0] = "Добавить";
          for (int i = 0; i < items.length(); i++) {
            JSONObject a = items.optJSONObject(i);
            labels[i + 1] =
                kind.equals("source") ? a.optString("title", a.optString("url")) : scheduleLabel(a);
          }
          int[] icons = new int[labels.length];
          java.util.Arrays.fill(
              icons, kind.equals("source") ? SettingsGlyph.LINK : SettingsGlyph.CLOCK);
          icons[0] = SettingsGlyph.PLUS;
          new RightSheet.Builder(getContext())
              .setIconItems(
                  labels,
                  icons,
                  (dialog, index) ->
                      DigestSheet.open(
                          getContext(),
                          kind,
                          index == 0 ? "" : items.optJSONObject(index - 1).optString("id"),
                          selected,
                          this::refresh))
              .show();
        });
  }

  private String scheduleLabel(JSONObject s) {
    try {
      return (s.optBoolean("enabled", true) ? "" : "Пауза · ")
          + java.time.Instant.ofEpochMilli(DigestSchedulePolicy.next(s, System.currentTimeMillis()))
              .atZone(java.time.ZoneId.of(s.optString("zone", "UTC")))
              .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm z"));
    } catch (Exception e) {
      return "Изменить расписание";
    }
  }

  private void update() {
    load(
        () ->
            new DigestCommands(getContext())
                .execute(
                    "digest_refresh",
                    new JSONObject()
                        .put("digest_id", selected)
                        .put("operation_id", UUID.randomUUID().toString())),
        r -> refresh());
  }

  private void article(JSONObject a) {
    LinearLayout box = Screens.column(getContext(), "");
    box.addView(Screens.text(getContext(), a.optString("summary"), 17));
    JSONArray sources = a.optJSONArray("sources");
    if (sources != null)
      for (int i = 0; i < sources.length(); i++) {
        JSONObject s = sources.optJSONObject(i);
        box.addView(
            Screens.action(
                getContext(),
                SettingsGlyph.LINK,
                s.optString("title", s.optString("url")),
                () -> {
                  try {
                    getContext()
                        .startActivity(
                            new Intent(
                                Intent.ACTION_VIEW, android.net.Uri.parse(s.optString("url"))));
                  } catch (Exception e) {
                    Toast.makeText(getContext(), "Браузер недоступен", Toast.LENGTH_SHORT).show();
                  }
                }));
      }
    new RightSheet.Builder(getContext())
        .setTitle(a.optString("title"))
        .setView(Screens.scroll(getContext(), box))
        .setNegativeButton("Закрыть", null)
        .show();
  }

  private void chooseScreensaver() {
    try {
      Intent intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
      if (intent.resolveActivity(getContext().getPackageManager()) == null)
        intent.setComponent(
            new ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.device.display.daydream.DaydreamActivity"));
      getContext().startActivity(intent);
    } catch (Exception e) {
      new RightSheet.Builder(getContext())
          .setMessage(
              "Выбор заставки недоступен в настройках этого телевизора. Полноэкранный просмотр"
                  + " доступен в подборке.")
          .setPositiveButton("OK", null)
          .show();
    }
  }

  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getContext()
        .registerReceiver(updates, new IntentFilter("local.tvgpt.assistant.DIGEST_CHANGED"));
    DigestScheduler.reconcile(getContext());
  }

  protected void onDetachedFromWindow() {
    getContext().unregisterReceiver(updates);
    super.onDetachedFromWindow();
  }
}
