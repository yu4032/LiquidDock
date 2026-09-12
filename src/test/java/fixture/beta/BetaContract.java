package fixture.beta;

public final class BetaContract {
    public static class RenderNode {}

    public static final class RailShell {
        public GlassHost host() { return null; }
    }

    public static final class WindowAuthority {
        private void settlePrimary(GlassHost host, RailShell wrapper, RenderNode panel) {}
        private void settleSecondary(GlassHost host, RailShell wrapper, RenderNode panel) {}
        private void settleWithMove(boolean moveSidebar, GlassHost host,
                                    RailShell wrapper, RenderNode panel) {}
    }

    public static final class ModeToken {
        private int active;
        private int last;
        public void setActive(int value) { last = active; active = value; }
        public int active() { return active; }
        public int presentation() { return active == 1 ? 2 : active == 3 ? 3 : active == 4 ? 4 : 1; }
    }

    public static class DockSurface extends RenderNode {}
    public static class AppSurface extends RenderNode {}
    public static class BoxSurface extends RenderNode {}
    public static class GameSurface extends RenderNode {}
    public static final class GameContainer extends RenderNode {
        public GameSurface getMainView() { return null; }
    }
    public static final class MediaBridge {}

    public static final class GlassHost extends RenderNode {
        private RailShell wrapper;
        private WindowAuthority manager;
        private ModeToken type;
        private GameContainer game;
        private MediaBridge video;
        private AppSurface apps;

        public void bindPanel(
                RailShell wrapper, boolean left, String pkg, int mode, ModeToken type,
                boolean force, boolean vertical, boolean extra) {}
        public DockSurface getDockLayout() { return null; }
        public AppSurface getAppsLayout() { return apps; }
        public BoxSurface getBoxView() { return null; }
        public GameContainer getGameTurboLayout() { return game; }
        public MediaBridge getVideoBoxViewAdapter() { return video; }
    }

    private BetaContract() {}
}
