package cn.lineai.shell;

import android.app.Activity;
import cn.lineai.core.model.ScannedTerminalProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * Facade over the legacy {@code :ipc} scanner for screen layers: discovers installed
 * terminal provider services, keeping feature modules free of {@code :ipc} types.
 */
public final class TerminalProviderDiscovery {

    private TerminalProviderDiscovery() {
    }

    /** Scans the device for terminal provider services exposed by companion apps. */
    public static List<ScannedTerminalProvider> scan(Activity activity) {
        List<ScannedTerminalProvider> result = new ArrayList<>();
        for (cn.lineai.ipc.ScannedProvider provider : new cn.lineai.ipc.IpcProviderScanner()
                .scan(activity, cn.lineai.ipc.IpcProviderType.TERMINAL)) {
            result.add(new ScannedTerminalProvider(provider.getLabel(), provider.getPackageName(),
                    provider.getServiceClass(), provider.getProviderType()));
        }
        return result;
    }
}
