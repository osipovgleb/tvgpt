package local.tvgpt.assistant;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import local.tvgpt.assistant.data.*;

public final class DataMutationCancellationTest {
  public static void main(String[] args) throws Exception {
    Context context = new Context();
    KnowledgeRepository knowledge = new KnowledgeRepository(context);
    CountDownLatch read = new CountDownLatch(1), release = new CountDownLatch(1);
    SQLiteDatabase.afterSql =
        sql -> {
          if (!sql.startsWith("SELECT COUNT(*) FROM knowledge")) return;
          read.countDown();
          boolean interrupted = false;
          while (true)
            try {
              release.await();
              break;
            } catch (InterruptedException e) {
              interrupted = true;
            }
          if (interrupted) Thread.currentThread().interrupt();
        };
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread writer =
        new Thread(
            () -> {
              try {
                knowledge.save(0, 0, "cancelled", "must not persist", "test");
              } catch (Throwable e) {
                failure.set(e);
              }
            });
    writer.start();
    if (!read.await(5, TimeUnit.SECONDS))
      throw new AssertionError("repository did not reach real SQLite read");
    writer.interrupt();
    release.countDown();
    writer.join(5000);
    SQLiteDatabase.afterSql = null;
    if (writer.isAlive()) throw new AssertionError("repository did not unblock");
    if (!(failure.get() instanceof CancellationException) || knowledge.list().length() != 0)
      throw new AssertionError(
          "cancelled task persisted a note after blocked repository read", failure.get());
    CancellationToken noteRead = new CancellationToken();
    blocked(
        "SELECT COUNT(*) FROM knowledge",
        noteRead,
        () -> knowledge.save(0, 0, "cancelled", "blocked read", "test", noteRead));
    if (knowledge.list().length() != 0) throw new AssertionError("cancelled note read persisted");
    CancellationToken noteWrite = new CancellationToken();
    blocked(
        "INSERT INTO knowledge",
        noteWrite,
        () -> knowledge.save(0, 0, "cancelled", "blocked insert", "test", noteWrite));
    if (knowledge.list().length() != 0)
      throw new AssertionError("cancelled note insert was not rolled back");
    long note = knowledge.save(0, 0, "retained", "must remain", "test");
    CancellationToken noteDelete = new CancellationToken();
    blocked("DELETE FROM knowledge", noteDelete, () -> knowledge.delete(note, 1, noteDelete));
    if (knowledge.list().length() != 1)
      throw new AssertionError("cancelled note deletion committed");
    ReminderRepository reminders = new ReminderRepository(context);
    long future = System.currentTimeMillis() + 60000;
    CancellationToken create = new CancellationToken();
    blocked(
        "BEGIN IMMEDIATE",
        create,
        () ->
            reminders.create("cancelled", future, "UTC", "REMINDER", "NONE", 0, 0, "", "", create));
    if (reminders.list().length() != 0)
      throw new AssertionError("cancelled reminder admission committed");
    long reminder = reminders.create("retained", future, "UTC", "REMINDER", "NONE", 0, 0, "", "");
    CancellationToken cancel = new CancellationToken();
    blocked(
        "UPDATE reminders SET enabled=0", cancel, () -> reminders.cancelOutcome(reminder, cancel));
    if (!reminders.list().getJSONObject(0).getString("status").equals("scheduled"))
      throw new AssertionError("cancelled reminder cancellation committed");
    CancellationToken edit = new CancellationToken();
    blocked(
        "SELECT kind FROM reminders",
        edit,
        () -> reminders.edit(reminder, 1, "changed", future, "UTC", "NONE", 0, edit));
    if (!reminders.list().getJSONObject(0).getString("body").equals("retained"))
      throw new AssertionError("cancelled reminder edit committed");
    CancellationToken delete = new CancellationToken();
    blocked("SELECT status FROM reminders", delete, () -> reminders.delete(reminder, delete));
    if (reminders.list().length() != 1)
      throw new AssertionError("cancelled reminder delete committed");
    System.out.println("Data mutation cancellation: blocked read/admission/write rollback passed");
  }

  private static void blocked(String prefix, CancellationToken token, Runnable action)
      throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    SQLiteDatabase.afterSql =
        sql -> {
          if (!sql.startsWith(prefix)) return;
          entered.countDown();
          try {
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timeout");
          } catch (InterruptedException e) {
            throw new AssertionError(e);
          }
        };
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                action.run();
              } catch (Throwable e) {
                error.set(e);
              }
            });
    thread.start();
    if (!entered.await(5, TimeUnit.SECONDS)) {
      release.countDown();
      throw new AssertionError("not reached: " + prefix, error.get());
    }
    token.cancel();
    release.countDown();
    thread.join(5000);
    SQLiteDatabase.afterSql = null;
    if (thread.isAlive() || !(error.get() instanceof CancellationException))
      throw new AssertionError("cancellation not observed after " + prefix, error.get());
  }
}
