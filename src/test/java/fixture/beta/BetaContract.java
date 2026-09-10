package fixture.beta;

public final class BetaContract {
    public static class RenderNode {}
    public static final class RailShell {
        public GlassHost C() { return null; }
    }
    public static final class WindowAuthority {
        private void d2(RailShell wrapper, boolean animate) {}
        private void f2(RailShell wrapper, boolean moveSidebar) {}
    }
    public static final class ModeToken {
        private int active;
        private int last;
        public void a(int value) { last = active; active = value; }
        public int c() { return active; }
        public int d() { return active == 1 ? 2 : active == 3 ? 3 : active == 4 ? 4 : 1; }
    }
    public static class DockSurface extends RenderNode {}
    public static class AppSurface extends RenderNode {}
    public static class BoxSurface extends RenderNode {}
    public static class GameSurface extends RenderNode { public void o() {} }
    public static final class GameContainer extends RenderNode {
        public GameSurface getMainView() { return null; }
    }
    public static final class MediaBridge { public void t() {} }
    public static final class GlassHost extends RenderNode {
        private RailShell wrapper;
        private WindowAuthority manager;
        private ModeToken type;
        private GameContainer game;
        private MediaBridge video;
        private AppSurface apps;
        private boolean q;
        private boolean s;

        public void V(RailShell w, boolean left, String pkg, int mode, ModeToken t,
                      boolean force, boolean vertical, boolean extra) {}
        public void c0() {}
        public void d0() {}
        public void U() {}
        public RailShell getSidebarWrapper() { return wrapper; }
        public DockSurface getDockLayout() { return null; }
        public AppSurface getAppsLayout() { return apps; }
        public BoxSurface getBoxView() { return null; }
        public GameContainer getGameTurboLayout() { return game; }
        public MediaBridge getVideoBoxViewAdapter() { return video; }
    }
    private BetaContract() {}
}
