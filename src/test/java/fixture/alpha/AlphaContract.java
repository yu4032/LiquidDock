package fixture.alpha;

public final class AlphaContract {
    public static class FakeView {}
    public static final class Wrapper {
        public Turbo C() { return null; }
    }
    public static final class Manager {
        private void d2(Wrapper wrapper, boolean animate) {}
        private void f2(Wrapper wrapper, boolean moveSidebar) {}
    }
    public static final class AssistantType {
        private int current;
        private int previous;
        public void a(int value) { previous = current; current = value; }
        public int c() { return current; }
        public int d() { return current == 1 ? 2 : current == 3 ? 3 : current == 4 ? 4 : 1; }
    }
    public static class DockView extends FakeView {}
    public static class AllAppsView extends FakeView {}
    public static class BoxView extends FakeView {}
    public static class GameMaterial extends FakeView { public void o() {} }
    public static final class GameBox extends FakeView {
        public GameMaterial getMainView() { return null; }
    }
    public static final class VideoAdapter { public void t() {} }
    public static final class Turbo extends FakeView {
        private Wrapper wrapper;
        private Manager manager;
        private AssistantType type;
        private GameBox game;
        private VideoAdapter video;
        private AllAppsView apps;
        private boolean q;
        private boolean s;

        public void V(Wrapper w, boolean left, String pkg, int mode, AssistantType t,
                      boolean force, boolean vertical, boolean extra) {}
        public void c0() {}
        public void d0() {}
        public void U() {}
        public Wrapper getSidebarWrapper() { return wrapper; }
        public DockView getDockLayout() { return null; }
        public AllAppsView getAppsLayout() { return apps; }
        public BoxView getBoxView() { return null; }
        public GameBox getGameTurboLayout() { return game; }
        public VideoAdapter getVideoBoxViewAdapter() { return video; }
    }
    private AlphaContract() {}
}
