package fixture.alpha;

public final class AlphaContract {
    public static class FakeView {}

    public static final class Wrapper {
        public Turbo owner() { return null; }
    }

    public static final class Manager {
        private void finishPrimary(Turbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishSecondary(Turbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, Turbo turbo, Wrapper wrapper, FakeView panel) {}
    }

    public static final class AssistantType {
        private int current;
        private int previous;
        public void setCode(int value) { previous = current; current = value; }
        public int code() { return current; }
        public int displayCode() { return current == 1 ? 2 : current == 3 ? 3 : current == 4 ? 4 : 1; }
    }

    public static class DockView extends FakeView {}
    public static class AllAppsView extends FakeView {}
    public static class BoxView extends FakeView {}
    public static class GameMaterial extends FakeView {}
    public static final class GameBox extends FakeView {
        public GameMaterial getMainView() { return null; }
    }
    public static final class VideoAdapter {}

    public static final class Turbo extends FakeView {
        private Wrapper wrapper;
        private Manager manager;
        private AssistantType type;
        private GameBox game;
        private VideoAdapter video;
        private AllAppsView apps;

        public void configurePanel(
                Wrapper wrapper, boolean left, String pkg, int mode, AssistantType type,
                boolean force, boolean vertical, boolean extra) {}
        public DockView getDockLayout() { return null; }
        public AllAppsView getAppsLayout() { return apps; }
        public BoxView getBoxView() { return null; }
        public GameBox getGameTurboLayout() { return game; }
        public VideoAdapter getVideoBoxViewAdapter() { return video; }
    }

    private AlphaContract() {}
}
