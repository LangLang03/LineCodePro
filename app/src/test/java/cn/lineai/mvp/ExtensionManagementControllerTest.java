package cn.lineai.mvp;

import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.model.SkillRecord;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

public final class ExtensionManagementControllerTest {
    @Test
    public void skillHubInstallFromWorkerThreadDoesNotTouchHost() throws Exception {
        SkillRecord expected = new SkillRecord(
                "skill-id",
                "Skill",
                "Description",
                "/repo/.linecode/skills/skill",
                "/repo/.linecode/skills/skill/SKILL.md",
                SkillRecord.LOCATION_PROJECT,
                true,
                1L,
                1L);
        AtomicReference<Thread> repositoryThread = new AtomicReference<>();
        ExtensionStore store = (ExtensionStore) Proxy.newProxyInstance(
                ExtensionStore.class.getClassLoader(),
                new Class<?>[]{ExtensionStore.class},
                (proxy, method, args) -> {
                    if ("installSkillFromSkillHub".equals(method.getName())) {
                        repositoryThread.set(Thread.currentThread());
                        Assert.assertArrayEquals(
                                new Object[]{"/repo", SkillRecord.LOCATION_PROJECT, "demo-skill", "1.0.0"},
                                args);
                        return expected;
                    }
                    throw new AssertionError("Unexpected repository call: " + method.getName());
                });
        FakeHost host = new FakeHost();
        ExtensionManagementController controller = new ExtensionManagementController(
                store,
                null,
                null,
                host);
        AtomicReference<SkillRecord> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                result.set(controller.installSkillFromSkillHub(
                        SkillRecord.LOCATION_PROJECT,
                        "demo-skill",
                        "1.0.0"));
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "skillhub-install-test");
        worker.start();
        worker.join();

        Assert.assertNull(failure.get());
        Assert.assertSame(expected, result.get());
        Assert.assertSame(worker, repositoryThread.get());
        Assert.assertEquals(0, host.navigationCount);
        Assert.assertEquals(0, host.refreshCount);
        Assert.assertEquals(0, host.renderCount);
    }

    private static final class FakeHost implements ExtensionManagementController.Host {
        private int navigationCount;
        private int refreshCount;
        private int renderCount;

        @Override
        public String projectPath() {
            return "/repo";
        }

        @Override
        public void returnToScreen(String screenId) {
            navigationCount++;
        }

        @Override
        public void refreshVisibleScreen(String screenId) {
            refreshCount++;
        }

        @Override
        public void render() {
            renderCount++;
        }
    }
}
