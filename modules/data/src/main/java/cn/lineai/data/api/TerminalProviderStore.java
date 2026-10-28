package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.TerminalProviderConfig;

/** Installed external terminal providers. */
public interface TerminalProviderStore {

    List<TerminalProviderConfig> list();

    TerminalProviderConfig add(TerminalProviderConfig input);

    void setEnabled(String id, boolean enabled);

    void delete(String id);
}
