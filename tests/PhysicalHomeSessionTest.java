package local.tvgpt.assistant;

public class PhysicalHomeSessionTest {
  public static void main(String[] args) {
    SessionService.TvSession session = new SessionService.TvSession(null);
    TvAccessibility stale = new TvAccessibility();
    stale.rejectToken = true;
    TvAccessibility.current = stale;
    boolean[] fallback = {false};
    SessionService.TvSession staleSession =
        new SessionService.TvSession(null) {
          void showSystemPanel() {
            fallback[0] = true;
          }
        };
    staleSession.onShow(null, 0);
    if (!fallback[0])
      throw new AssertionError(
          "Missing native system-window fallback for invalid accessibility token");
    TvAccessibility access = new TvAccessibility();
    TvAccessibility.current = access;
    VoicePanel panel = new VoicePanel(null, () -> {}, true);
    VoicePanel.active = panel;
    access.owner = session;
    access.overlay = panel;
    session.onCloseSystemDialogs();
    if (VoicePanel.active != panel || panel.closes != 0)
      throw new AssertionError("Physical HOME closed Live overlay");
    if (access.owner != null || session.finishes != 1)
      throw new AssertionError("Native system session not detached");
    session.onHide();
    session.onDestroy();
    if (VoicePanel.active != panel)
      throw new AssertionError("Late native lifecycle closed detached conversation");
    session.onShow(null, 0);
    if (VoicePanel.active != null)
      throw new AssertionError("Assistant key no longer closes conversation");
    TvAccessibility.current = null;
    SessionService.TvSession empty = new SessionService.TvSession(null);
    empty.onCloseSystemDialogs();
    if (empty.hides != 1)
      throw new AssertionError("Empty system dialog did not use Android default");
    System.out.println(
        "PhysicalHomeSessionTest: system HOME retains Live, late callbacks safe, assist still"
            + " toggles");
  }
}
