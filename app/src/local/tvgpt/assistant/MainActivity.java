package local.tvgpt.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import local.tvgpt.assistant.ui.*;

/** Native app navigation; voice remains a separate system assistant session. */
public class MainActivity extends Activity {
  private AppNavigation navigation;
  private AppShell shell;
  private TechnicalSettingsView access;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    navigation = new AppNavigation(getIntent().getBooleanExtra("setup", false));
    if (state != null) {
      try {
        navigation.open(AppNavigation.Route.valueOf(state.getString("route", "APPEARANCE")));
      } catch (IllegalArgumentException ignored) {
      }
    }
    sectionIntent(getIntent());
    shell = new AppShell(this, this::open);
    setContentView(shell);
    show();
  }

  private void open(AppNavigation.Route route) {
    navigation.open(route);
    show();
  }

  private void show() {
    if (access != null) {
      access.dispose();
      access = null;
    }
    View page;
    switch (navigation.current()) {
      case ACCESS:
        access = new TechnicalSettingsView(this, () -> openAssistant(true));
        page = access;
        break;
      case CONVERSATIONS:
        page = new ConversationsScreen(this);
        break;
      case KNOWLEDGE:
        page = new KnowledgeScreen(this);
        break;
      case REMINDERS:
        page = new RemindersScreen(this);
        break;
      case DIGESTS:
        page = new DigestsScreen(this, getIntent().getStringExtra("digest_id"));
        break;
      case APPEARANCE:
        page = new AppearanceScreen(this);
        break;
      default:
        throw new IllegalStateException("Unknown section");
    }
    shell.show(navigation.current(), page);
  }

  private void talk() {
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 8);
    else openAssistant();
  }

  private void openAssistant() {
    openAssistant(false);
  }

  private void openAssistant(boolean check) {
    Bundle args = new Bundle();
    args.putString("source", "launch");
    args.putBoolean("voice_check", check);
    if (AssistantService.current != null) AssistantService.current.showSession(args, 0);
    else startActivity(new Intent(this, ConversationActivity.class).putExtra("voice_check", check));
  }

  @Override
  public void onBackPressed() {
    if (!shell.focusMenu()) super.onBackPressed();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.getBooleanExtra("setup", false)) open(AppNavigation.Route.ACCESS);
    else if (sectionIntent(intent)) show();
  }

  private boolean sectionIntent(Intent intent) {
    String value = intent.getStringExtra("section");
    if (value == null) return false;
    intent.removeExtra("section");
    if (value.equals("SETTINGS") || value.equals("GENERAL")) value = "ACCESS";
    if (value.equals("AUDIO")) value = "APPEARANCE";
    try {
      navigation.open(AppNavigation.Route.valueOf(value));
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (access != null) access.permissionsResult(request, grants);
    if (request == 8 && grants.length > 0 && grants[0] == 0) openAssistant();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("route", navigation.current().name());
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onResume() {
    super.onResume();
    local.tvgpt.assistant.reminders.ReminderDispatcher.onInteractive(this);
    if (access != null) access.refresh();
  }

  @Override
  protected void onStop() {
    VoicePreview.stopActive(null);
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (access != null) access.dispose();
    super.onDestroy();
  }
}
