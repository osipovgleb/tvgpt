package local.tvgpt.assistant;

import android.content.Context;
import android.os.Bundle;
import java.util.UUID;
import local.tvgpt.assistant.data.*;
import org.json.*;

/** Device SQLite checks use isolated IDs and remove their own records only. */
final class FeatureChecks {
  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void database(Context c, Bundle out) throws Exception {
    String id = "test-" + UUID.randomUUID();
    ConversationRepository conversations = new ConversationRepository(c);
    conversations.ensure(id, 10, 20);
    require(
        conversations.appendTurn(id, "call", "one", 30, "user", "одинаковая фраза"), "first turn");
    require(
        !conversations.appendTurn(id, "call", "one", 31, "user", "одинаковая фраза"),
        "duplicate event");
    require(
        conversations.appendTurn(id, "call", "two", 32, "user", "одинаковая фраза"),
        "distinct real turn");
    String assistantOnly = id + "-assistant";
    conversations.appendTurn(assistantOnly, "call", "reply", 32, "assistant", "Сорок два.");
    require(
        conversations
            .search("Сорок два.", 0, 0, "other", 1)
            .getJSONObject(0)
            .optString("excerpt")
            .equals("Сорок два."),
        "assistant-only archive excerpt");
    conversations.delete(assistantOnly);
    String needle = "recall-" + id + " 50%_";
    conversations.appendTurn(id, "call", "recall", 33, "user", needle);
    String decoy = id + "-decoy";
    conversations.appendTurn(decoy, "call", "recall", 33, "user", needle.replace("%_", "XX"));
    JSONArray found = conversations.search(needle, 0, 0, "other-current-call", 5);
    require(
        found.length() == 1 && found.getJSONObject(0).getString("id").equals(id),
        "literal archive search");
    require(
        conversations.search(needle, 0, 0, id, 5).length() == 0, "current call leaked into recall");
    require(conversations.search(needle, 34, 0, "other", 5).length() == 0, "archive since bound");
    require(conversations.search(needle, 0, 5, "other", 5).length() == 0, "archive until bound");
    conversations.delete(decoy);
    long revision = conversations.get(id).getLong("revision");
    require(!conversations.saveSummaryIfCurrent(id, revision - 1, "old", "old"), "stale summary");
    require(
        conversations.saveSummaryIfCurrent(id, revision, "title", "summary"), "current summary");
    conversations.delete(id);
    require(
        conversations.search(needle, 0, 0, "other", 5).length() == 0, "deleted archive recalled");
    require(
        !conversations.appendTurn(id, "call", "three", 33, "user", "late"),
        "deleted conversation revived");
    require(
        !conversations.saveSummaryIfCurrent(id, revision, "late", "late"),
        "deleted summary revived");
    String legacy = id + "-legacy";
    JSONArray turns =
        new JSONArray().put(new JSONObject().put("role", "user").put("text", "legacy"));
    conversations.importLegacy(legacy, 10, 20, turns);
    conversations.importLegacy(legacy, 10, 20, turns);
    require(conversations.turns(legacy, 10, 0).length() == 1, "duplicate migration");
    require(conversations.turns(legacy, 10, 0).getJSONObject(0).isNull("at"), "invented timestamp");
    conversations.delete(legacy);
    KnowledgeRepository knowledge = new KnowledgeRepository(c);
    long note = knowledge.save(0, 0, "Тест " + id, "Текст", "test");
    knowledge.save(note, 1, "Тест", "Новый текст", "test");
    boolean stale = false;
    try {
      knowledge.save(note, 1, "Тест", "Устаревший текст", "test");
    } catch (IllegalStateException expected) {
      stale = true;
    }
    require(stale, "knowledge edit race");
    require(!knowledge.delete(note, 1), "stale delete");
    require(knowledge.delete(note, 2), "delete note");
    ReminderRepository reminders = new ReminderRepository(c);
    long reminder =
        reminders.create(
            "Тест " + id,
            System.currentTimeMillis() + 3600000,
            "Europe/Moscow",
            "REMINDER",
            "NONE",
            0,
            0,
            "",
            " ");
    require(!reminders.claim(reminder), "early reminder");
    require(reminders.cancel(reminder), "cancel reminder");
    require(!reminders.claim(reminder), "cancelled reminder claimed");
    boolean cancelledEdit = false;
    try {
      reminders.edit(
          reminder,
          1,
          "Устаревшая правка",
          System.currentTimeMillis() + 7200000,
          "Europe/Moscow",
          "NONE",
          0);
    } catch (IllegalStateException expected) {
      cancelledEdit = true;
    }
    require(cancelledEdit, "stale editor revived cancelled reminder");
    reminders.delete(reminder);
    long action =
        reminders.create(
            "Тест действия",
            System.currentTimeMillis() + 3600000,
            "Europe/Moscow",
            "ACTION",
            "NONE",
            0,
            0,
            "",
            "{\"tool\":\"open_app\",\"arguments\":{\"name\":\"YouTube\"}}");
    reminders.edit(
        action,
        1,
        "Другое время",
        System.currentTimeMillis() + 7200000,
        "Europe/Moscow",
        "NONE",
        0);
    JSONArray edited = reminders.list();
    JSONObject actionItem = null;
    for (int i = 0; i < edited.length(); i++)
      if (edited.getJSONObject(i).optLong("id") == action) actionItem = edited.getJSONObject(i);
    require(
        actionItem != null
            && actionItem.optString("kind").equals("ACTION")
            && !actionItem.optString("action").isEmpty(),
        "editor removed scheduled action");
    reminders.delete(action);
    long delivery =
        reminders.create(
            "Тест доставки",
            System.currentTimeMillis() + 3600000,
            "Europe/Moscow",
            "REMINDER",
            "NONE",
            0,
            0,
            "",
            "");
    AppDatabase.get(c)
        .getWritableDatabase()
        .execSQL("UPDATE reminders SET due=1 WHERE id=?", new Object[] {delivery});
    reminders.deferDue(System.currentTimeMillis());
    JSONObject due = null;
    JSONArray candidates = reminders.pending(System.currentTimeMillis());
    for (int i = 0; i < candidates.length(); i++)
      if (candidates.getJSONObject(i).optLong("id") == delivery) due = candidates.getJSONObject(i);
    require(due != null && due.optString("status").equals("pending"), "not persisted pending");
    require(reminders.claim(delivery) && !reminders.claim(delivery), "duplicate delivery claim");
    reminders.finish(due, true);
    JSONObject finished = null;
    JSONArray all = reminders.list();
    for (int i = 0; i < all.length(); i++)
      if (all.getJSONObject(i).optLong("id") == delivery) finished = all.getJSONObject(i);
    require(
        finished != null
            && finished.optInt("enabled") == 0
            && finished.optString("last_status").equals("completed"),
        "delivery outcome missing");
    AppDatabase.get(c)
        .getWritableDatabase()
        .execSQL(
            "UPDATE reminders SET status='delivering',enabled=1 WHERE id=?",
            new Object[] {delivery});
    reminders.finish(due, "unconfirmed");
    JSONObject uncertain = null;
    JSONArray afterUnconfirmed = reminders.list();
    for (int i = 0; i < afterUnconfirmed.length(); i++)
      if (afterUnconfirmed.getJSONObject(i).optLong("id") == delivery)
        uncertain = afterUnconfirmed.getJSONObject(i);
    require(
        uncertain != null
            && uncertain.optString("status").equals("unconfirmed")
            && uncertain.optString("last_status").equals("unconfirmed")
            && uncertain.optInt("enabled") == 0,
        "unconfirmed observation became failed or was scheduled to repeat");
    reminders.delete(delivery);
    out.putBoolean("deferred_claim_and_completion", true);
    out.putBoolean("sqlite_dedupe_revision_migration", true);
    out.putBoolean("knowledge_conflict_and_delete", true);
    out.putBoolean("reminder_cancel_and_not_early", true);
  }
}
