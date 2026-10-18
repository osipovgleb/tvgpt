package local.tvgpt.assistant.ui;

import android.content.Context;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;
import local.tvgpt.assistant.*;
import local.tvgpt.assistant.settings.*;

/** The same rounded settings group as Access, with horizontal value selection. */
public final class AppearanceScreen extends LinearLayout {
  private final AssistantPreferences prefs;
  private final VoiceSamples samples;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final List<String> voices = new ArrayList<>(VoiceCatalogue.CANDIDATES);
  private TextView voiceStatus;
  private int voiceIndex, generation;
  private boolean disposed;
  private Runnable pending;
  private Runnable renderVoice;
  private String modelCatalog = "{}";
  private final List<Runnable> modelRenders = new ArrayList<>();

  public AppearanceScreen(Context c) {
    super(c);
    prefs = new AssistantPreferences(c);
    samples = new VoiceSamples(c);
    voiceIndex = Math.max(0, voices.indexOf(prefs.voice()));
    setOrientation(VERTICAL);
    setPadding(Screens.dp(c, 24), Screens.dp(c, 22), Screens.dp(c, 24), Screens.dp(c, 24));
    VoiceOrb orb = new VoiceOrb(c);
    orb.state(false, false);
    orb.theme(prefs.theme());
    LayoutParams op = new LayoutParams(Screens.dp(c, 87), Screens.dp(c, 87));
    op.gravity = Gravity.CENTER_HORIZONTAL;
    op.bottomMargin = 0;
    addView(orb, op);
    LinearLayout groups = new LinearLayout(c);
    groups.setOrientation(VERTICAL);
    LinearLayout group = new SettingsRows(c).group(groups, "Внешний вид");
    addView(Screens.scroll(c, groups), new LayoutParams(-1, 0, 1));
    option(
        group,
        SettingsGlyph.PALETTE,
        "Цвет",
        () -> OrbPalette.LABELS.get(OrbPalette.index(prefs.theme())),
        d -> {
          prefs.theme(
              OrbPalette.IDS.get(
                  Math.floorMod(OrbPalette.index(prefs.theme()) + d, OrbPalette.IDS.size())));
          orb.theme(prefs.theme());
        },
        false);
    group = new SettingsRows(c).group(groups, "Голос");
    option(
        group,
        SettingsGlyph.VOICE,
        "Голос",
        () -> title(voices.get(voiceIndex)),
        d -> {
          voiceIndex = Math.floorMod(voiceIndex + d, voices.size());
          queuePreview();
        },
        true);
    option(
        group,
        SettingsGlyph.SPEAKER,
        "Манера речи",
        () -> prefs.preset().label,
        d -> {
          SpeechPreset[] values = SpeechPreset.values();
          prefs.preset(values[Math.floorMod(prefs.preset().ordinal() + d, values.length)]);
        },
        false);
    modelRows(c, group);
    ModelCatalogue.load(
        c,
        json -> {
          if (!disposed) {
            modelCatalog = json;
            for (Runnable render : modelRenders) render.run();
          }
        });
  }

  private void modelRows(Context c, LinearLayout group) {
    option(
        group,
        SettingsGlyph.CHAT,
        "Модель обсуждения",
        () -> ModelSettings.discussion(c),
        d -> cycleModel("discussion", d),
        false);
    option(
        group,
        SettingsGlyph.CHAT,
        "Рассуждение при поручениях",
        () -> ModelOptions.reasoningLabel(ModelSettings.reasoning(c, "discussion")),
        d -> cycleReasoning("discussion", d),
        false);
    option(
        group,
        SettingsGlyph.BOOK,
        "Описания разговоров",
        () -> ModelSettings.summary(c),
        d -> cycleModel("summary", d),
        false);
    option(
        group,
        SettingsGlyph.CHAT,
        "Скорость поручений и описаний",
        () -> ModelSettings.fastLabel(c),
        d -> ModelSettings.fast(c, !ModelSettings.fast(c)),
        false);
    option(
        group,
        SettingsGlyph.VOICE,
        "Модель голоса",
        () -> ModelSettings.live(c),
        d -> cycleModel("live", d),
        false);
  }

  private void cycleReasoning(String kind, int direction) {
    String model =
        kind.equals("summary")
            ? ModelSettings.summary(getContext())
            : ModelSettings.discussion(getContext());
    List<String> options = ModelOptions.reasoning(modelCatalog, model);
    ModelSettings.reasoning(
        getContext(),
        kind,
        options.get(
            Math.floorMod(
                options.indexOf(ModelSettings.reasoning(getContext(), kind)) + direction,
                options.size())));
  }

  private void cycleModel(String kind, int direction) {
    String current =
        kind.equals("live")
            ? ModelSettings.live(getContext())
            : kind.equals("summary")
                ? ModelSettings.summary(getContext())
                : ModelSettings.discussion(getContext());
    List<String> options = ModelOptions.parse(modelCatalog, kind.equals("live"), current);
    ModelSettings.set(
        getContext(),
        kind,
        options.get(Math.floorMod(options.indexOf(current) + direction, options.size())));
    for (Runnable render : modelRenders) render.run();
  }

  private static String title(String id) {
    return Character.toUpperCase(id.charAt(0)) + id.substring(1);
  }

  private boolean ducking() {
    return getContext().getSharedPreferences("assistant", 0).getBoolean("duck", true);
  }

  private void option(
      LinearLayout group,
      int icon,
      String title,
      Supplier<String> value,
      IntConsumer change,
      boolean voice) {
    Context c = getContext();
    LinearLayout row = new SettingsRows(c).row(group, icon, title, value.get(), () -> {});
    TextView detail = (TextView) row.getTag();
    row.removeViewAt(row.getChildCount() - 1);
    TextView arrows = Screens.text(c, "‹  ›", 16);
    row.addView(arrows, new LayoutParams(Screens.dp(c, 32), -2));
    Runnable render =
        () -> {
          detail.setText(value.get());
          row.setContentDescription(title + ": " + value.get());
        };
    if (!voice) modelRenders.add(render);
    if (voice) {
      voiceStatus = detail;
      renderVoice = render;
      row.setOnFocusChangeListener(
          (v, focus) -> {
            if (focus) queuePreview();
            else cancelPreview();
          });
    }
    row.setOnClickListener(
        v -> {
          if (voice) queuePreview();
          else {
            change.accept(1);
            render.run();
          }
        });
    row.setOnKeyListener(
        (v, key, event) -> {
          if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
          if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            change.accept(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            render.run();
          }
          return true;
        });
  }

  private void queuePreview() {
    cancelPreview();
    int attempt = generation;
    String voice = voices.get(voiceIndex);
    pending =
        () -> {
          if (!active(attempt)) return;
          samples.play(
              voice,
              prefs.preset(),
              new VoiceSamples.Listener() {
                public void status(String text) {
                  if (active(attempt)) showStatus(text);
                }

                public void ready(String id) {
                  if (active(attempt)) {
                    prefs.verifiedVoice(id);
                    showStatus("");
                  }
                }

                public void unavailable(String id) {
                  if (!active(attempt)) return;
                  voices.remove(id);
                  if (voices.isEmpty()) voices.add("cove");
                  voiceIndex = Math.max(0, voices.indexOf(prefs.voice()));
                  renderVoice.run();
                }
              });
        };
    main.postDelayed(pending, 400);
  }

  private void showStatus(String text) {
    if (text.isEmpty()) renderVoice.run();
    else voiceStatus.setText(text);
  }

  private boolean active(int attempt) {
    return !disposed && isShown() && getWindowVisibility() == VISIBLE && attempt == generation;
  }

  private void cancelPreview() {
    generation++;
    if (pending != null) main.removeCallbacks(pending);
    pending = null;
    samples.stop();
    if (renderVoice != null) renderVoice.run();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility != VISIBLE && samples != null) cancelPreview();
  }

  @Override
  public void onWindowFocusChanged(boolean focus) {
    super.onWindowFocusChanged(focus);
    if (!focus && samples != null) cancelPreview();
  }

  @Override
  protected void onDetachedFromWindow() {
    disposed = true;
    cancelPreview();
    super.onDetachedFromWindow();
  }
}
