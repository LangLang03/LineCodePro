package cn.lineai.feature.extensions;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.Skill;
import cn.lineai.data.api.SkillStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteSkillInstallTest {

    private SkillStore store(StringBuilder seen, boolean fail) {
        return new SkillStore() {
            public List<Skill> list() { return java.util.Collections.emptyList(); }
            public List<Skill> enabled() { return java.util.Collections.emptyList(); }
            public Skill save(Skill input) { return input; }
            public Skill importSkill(InputStream input, String displayName) throws IOException {
                if (fail) throw new IOException("bad zip");
                seen.append(displayName);
                return Skill.builder().name(displayName).build();
            }
            public void setEnabled(String id, boolean enabled) { }
            public void delete(String id) { }
        };
    }

    @Test public void downloadsThenUsesSharedImportPipeline() {
        StringBuilder seen = new StringBuilder();
        final StringBuilder outcome = new StringBuilder();
        RemoteSkillInstall.run(Scheduler.immediate(), store(seen, false),
                () -> new byte[] {1}, "remote.zip", new RemoteSkillInstall.Callback() {
                    public void onSuccess() { outcome.append("ok"); }
                    public void onError(String message) { outcome.append(message); }
                });
        assertEquals("remote.zip", seen.toString());
        assertEquals("ok", outcome.toString());
    }

    @Test public void reportsDownloadOrImportFailures() {
        StringBuilder seen = new StringBuilder();
        final StringBuilder error = new StringBuilder();
        RemoteSkillInstall.run(Scheduler.immediate(), store(seen, true),
                () -> new byte[] {1}, "remote.zip", new RemoteSkillInstall.Callback() {
                    public void onSuccess() { error.append("ok"); }
                    public void onError(String message) { error.append(message); }
                });
        assertTrue(error.toString().contains("bad zip"));
    }
}
