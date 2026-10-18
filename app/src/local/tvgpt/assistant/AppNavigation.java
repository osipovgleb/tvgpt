package local.tvgpt.assistant;

public final class AppNavigation {
  public enum Route {
    CONVERSATIONS,
    KNOWLEDGE,
    REMINDERS,
    DIGESTS,
    APPEARANCE,
    ACCESS
  }

  private Route current;

  public AppNavigation(boolean setup) {
    current = setup ? Route.ACCESS : Route.APPEARANCE;
  }

  public Route current() {
    return current;
  }

  public Route open(Route route) {
    if (route == null) throw new IllegalArgumentException("route");
    return current = route;
  }

  public Route back() {
    return null;
  }
}
