package cn.lineai.arch;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Every rewritten class lives inside its module's package root. */
public class PackageOwnershipTest {

    @Test
    public void javaSourcesStayInsideModulePackageRoot() {
        Map<String, String> roots = Modules.packageRoots();
        StringBuilder violations = new StringBuilder();
        for (Map.Entry<String, String> entry : roots.entrySet()) {
            for (java.io.File file : Modules.javaFiles(entry.getKey())) {
                String pkg = Modules.packageOf(Modules.read(file));
                if (!pkg.equals(entry.getValue()) && !pkg.startsWith(entry.getValue() + ".")) {
                    violations.append(entry.getKey()).append(": ").append(file.getPath())
                            .append(" -> ").append(pkg).append('\n');
                }
            }
        }
        assertEquals("Package ownership violations:\n" + violations, 0, violations.length());
    }

    @Test
    public void everyModuleHasDeclaredPackageRoot() {
        Map<String, String> roots = Modules.packageRoots();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) {
                continue;
            }
            assertTrue("missing package root mapping for " + module, roots.containsKey(module));
        }
    }

    @Test
    public void samePackageNameNeverSpansTwoModules() {
        Map<String, List<String>> owners = new HashMap<>();
        for (String module : Modules.modules()) {
            if (module.equals("arch-test")) {
                continue;
            }
            for (java.io.File file : Modules.javaFiles(module)) {
                String pkg = Modules.packageOf(Modules.read(file));
                owners.computeIfAbsent(pkg, key -> new java.util.ArrayList<>()).add(module);
            }
        }
        StringBuilder duplicates = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : owners.entrySet()) {
            List<String> seen = entry.getValue();
            boolean multipleModules = seen.stream().distinct().count() > 1;
            if (multipleModules) {
                duplicates.append(entry.getKey()).append(" -> ").append(seen).append('\n');
            }
        }
        assertEquals("Split packages:\n" + duplicates, 0, duplicates.length());
    }
}
