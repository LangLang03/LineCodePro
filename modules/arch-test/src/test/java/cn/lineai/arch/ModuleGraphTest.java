package cn.lineai.arch;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Dependency whitelist and layer purity for the rewritten modules. */
public class ModuleGraphTest {

    private static final Pattern DEPENDENCY =
            Pattern.compile("project\\(\":([a-z0-9-]+)\"\\)");

    private List<String> dependenciesOf(String module) {
        File build = new File(Modules.ROOT, "modules/" + module + "/build.gradle.kts");
        Matcher matcher = DEPENDENCY.matcher(Modules.read(build));
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    @Test
    public void featuresNeverDependOnEachOther() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (!module.startsWith("feature-")) {
                continue;
            }
            for (String dependency : dependenciesOf(module)) {
                if (dependency.startsWith("feature-") || dependency.equals("app")) {
                    violations.append(module).append(" -> ").append(dependency).append('\n');
                }
            }
        }
        assertEquals("feature -> feature/app edges:\n" + violations, 0, violations.length());
    }

    @Test
    public void coreModulesNeverDependOnUiOrFeatures() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (!module.startsWith("core-") && !module.equals("tool-api")) {
                continue;
            }
            for (String dependency : dependenciesOf(module)) {
                if (dependency.startsWith("ui-") || dependency.startsWith("feature-")
                        || dependency.equals("app") || dependency.equals("platform-android")) {
                    violations.append(module).append(" -> ").append(dependency).append('\n');
                }
            }
        }
        assertEquals("core -> upper layer edges:\n" + violations, 0, violations.length());
    }

    @Test
    public void engineAndCoreStayAndroidFree() {
        // engine-local is intentionally Android/native-adjacent; tool-* families are UI
        // adjacent by design. Everything else in core/engine must stay pure.
        String[] pure = {"core-model", "core-kernel", "core-mvi", "core-service",
                "engine-protocol", "engine-prompt", "engine-skillhub", "engine-ai",
                "tool-api", "engine-agent"};
        StringBuilder violations = new StringBuilder();
        for (String module : pure) {
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.read(file);
                if (source.contains("import android.")) {
                    violations.append(module).append(": ").append(file.getName()).append('\n');
                }
            }
        }
        assertEquals("Android imports in pure modules:\n" + violations, 0, violations.length());
    }

    /**
     * The legacy modules are frozen debt: only the composition root shell (:app) and the
     * shell-platform module may touch them. Any other rewritten dependency re-glues the
     * legacy tree into the new architecture.
     */
    @Test
    public void rewrittenModulesNeverDependOnLegacyModules() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test") || module.equals("platform-shell")) {
                continue;
            }
            for (String dependency : dependenciesOf(module)) {
                if (dependency.equals("ipc") || dependency.equals("terminal-provider")) {
                    violations.append(module).append(" -> ").append(dependency).append('\n');
                }
            }
        }
        assertEquals("rewritten -> legacy edges:\n" + violations, 0, violations.length());
    }

    @Test
    public void dataApiPackageIsAndroidFree() {
        StringBuilder violations = new StringBuilder();
        for (File file : Modules.javaFiles("data")) {
            String pkg = Modules.packageOf(Modules.read(file));
            if (pkg.startsWith("cn.lineai.data.api") && Modules.read(file).contains("import android.")) {
                violations.append(file.getName()).append('\n');
            }
        }
        assertEquals("Android imports in data/api:\n" + violations, 0, violations.length());
    }

    @Test
    public void rewrittenModulesDoNotImportLegacyCode() {
        StringBuilder violations = new StringBuilder();
        String[] forbidden = {"cn.lineai." + "mvp", "cn.lineai.ui.component",
                "cn.lineai.data.repository", "cn.lineai.ui.theme"};
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) {
                continue;
            }
            for (File file : Modules.javaFiles(module)) {
                String source = Modules.read(file);
                for (String legacy : forbidden) {
                    if (source.contains("import " + legacy)) {
                        violations.append(module).append(" -> ").append(legacy).append('\n');
                    }
                }
            }
        }
        assertEquals("Legacy imports in rewritten modules:\n" + violations, 0, violations.length());
    }

    @Test
    public void noJsonAtUiBoundary() {
        StringBuilder violations = new StringBuilder();
        for (String module : Modules.modules()) {
            if (!module.startsWith("feature-") && !module.startsWith("ui-")) {
                continue;
            }
            for (File file : Modules.javaFiles(module)) {
                if (Modules.read(file).contains("import org.json")) {
                    violations.append(module).append(": ").append(file.getName()).append('\n');
                }
            }
        }
        assertTrue("org.json found above the engine boundary:\n" + violations,
                violations.length() == 0);
    }
}
