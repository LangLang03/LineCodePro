package cn.lineai.mvp;

import org.junit.Assert;
import org.junit.Test;

public final class ScreenNavigationControllerTest {
    @Test
    public void backFallsThroughParentMappingThenChat() {
        ScreenNavigationController controller = new ScreenNavigationController();
        RecordingHost host = new RecordingHost();

        controller.showScreen("settings", host);
        controller.showScreen("llm", host);
        controller.backFrom("llm", host);
        controller.backFrom("settings", host);

        Assert.assertEquals("settings", host.lastScreenId);
        Assert.assertTrue(host.chatShown);
    }

    @Test
    public void directBackUsesParentScreenWhenStackIsEmpty() {
        ScreenNavigationController controller = new ScreenNavigationController();
        RecordingHost host = new RecordingHost();

        controller.backFrom("modelEdit:m1", host);

        Assert.assertEquals("models", host.lastScreenId);
        Assert.assertFalse(host.chatShown);
    }

    @Test
    public void promptTemplatesBackReturnsToAiBehavior() {
        ScreenNavigationController controller = new ScreenNavigationController();
        RecordingHost host = new RecordingHost();

        controller.backFrom("promptTemplates", host);

        Assert.assertEquals("llm", host.lastScreenId);
        Assert.assertFalse(host.chatShown);
    }

    @Test
    public void inputSettingsBackReturnsToSettings() {
        ScreenNavigationController controller = new ScreenNavigationController();
        RecordingHost host = new RecordingHost();

        controller.backFrom("input", host);

        Assert.assertEquals("settings", host.lastScreenId);
        Assert.assertFalse(host.chatShown);
    }

    @Test
    public void imageUnderstandingModelBackReturnsToToolSettings() {
        ScreenNavigationController controller = new ScreenNavigationController();
        RecordingHost host = new RecordingHost();

        controller.backFrom("imageUnderstandingModel", host);

        Assert.assertEquals("toolSettings", host.lastScreenId);
        Assert.assertFalse(host.chatShown);
    }

    private static final class RecordingHost implements ScreenNavigationController.Host {
        private String lastScreenId = "";
        private boolean chatShown;

        @Override
        public void hideOverlays() {
        }

        @Override
        public void showScreen(String screenId) {
            lastScreenId = screenId;
        }

        @Override
        public void showChatScreen() {
            chatShown = true;
        }
    }
}
