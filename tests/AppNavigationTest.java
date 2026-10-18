package local.tvgpt.assistant;

public final class AppNavigationTest {
  public static void main(String[] args) {
    AppNavigation nav = new AppNavigation(false);
    if (nav.current() != AppNavigation.Route.APPEARANCE)
      throw new AssertionError("app must open settings without Home");
    nav.open(AppNavigation.Route.ACCESS);
    if (nav.back() != null) throw new AssertionError("all sidebar destinations are roots");
    if (new AppNavigation(true).current() != AppNavigation.Route.ACCESS)
      throw new AssertionError("setup");
    nav.open(AppNavigation.Route.KNOWLEDGE);
    if (nav.back() != null) throw new AssertionError("root back must exit");
    for (AppNavigation.Route r : AppNavigation.Route.values())
      if (r.name().equals("HOME") || r.name().equals("SETTINGS") || r.name().equals("AUDIO"))
        throw new AssertionError("Home route remains");
    System.out.println("AppNavigation: direct settings, setup entry and root back passed");
  }
}
