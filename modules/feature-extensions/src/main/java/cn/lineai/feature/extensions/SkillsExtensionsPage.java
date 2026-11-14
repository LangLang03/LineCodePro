package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.Skill;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** Local SKILL.md extension detail: add, install, browse and manage Skills. */
public final class SkillsExtensionsPage extends ExtensionPage implements Refreshable {

    /** Host callbacks for skill management and store navigation. */
    public interface Listener {
        /**
         * Loads the installed skills.
         *
         * @return current skills; may be null or empty
         */
        List<Skill> load();

        /**
         * Enables or disables one installed skill.
         *
         * @param id skill identifier
         * @param enabled true to enable the skill
         */
        void setEnabled(String id, boolean enabled);

        /**
         * Deletes one installed skill.
         *
         * @param id skill identifier
         */
        void delete(String id);

        /** Opens the editor for creating a new skill. */
        void add();

        /**
         * Opens the editor for an existing skill.
         *
         * @param skill skill to edit
         */
        void edit(Skill skill);

        /** Opens the ZIP import flow; import funnels through the shared install path. */
        void importSkill();

        /** Opens the SkillHub store page. */
        void openHub();

        /** Opens the GitHub skill import page. */
        void openGithub();

        /** Opens the SkillHub login page. */
        void openLogin();

        /** Opens the SkillHub publish page. */
        void openPublish();
    }

    private final Listener listener;
    private final LinearLayout installedHost;

    /**
     * Builds the skills extension page.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param listener host callbacks for skill management and store navigation
     */
    public SkillsExtensionsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, tokens, back, R.string.screen_extensions_section_skills);
        this.listener = listener;
        this.installedHost = new LinearLayout(context);
        installedHost.setOrientation(LinearLayout.VERTICAL);
        header(R.string.screen_extensions_section_skills,
                ExtensionKit.addAction(context, theme,
                        R.string.screen_extension_detail_sheet_title_skills, this::openActions));
        build();
    }

    /** Adds the install, store, and installed sections. */
    private void build() {
        SettingsSection create = section(R.string.screen_extension_detail_section_install_skills);
        create.row(ExtensionKit.action(getContext(), theme, Icons.ARCHIVE,
                R.string.screen_extension_detail_inline_title_skills,
                R.string.screen_extension_detail_inline_desc_skills, 83, this::openActions), false, 0);

        SettingsSection store = section(R.string.extension_online_store);
        // Logging in, publishing and GitHub import belong to the store/action sheets, not to the
        // installed extensions page (legacy ExtensionDetailScreenView only lists the store row).
        store.row(ExtensionKit.action(getContext(), theme, Icons.ARCHIVE,
                R.string.extension_skillhub_store, R.string.extension_skillhub_store_desc, 68,
                listener::openHub), false, 0);

        content.addView(installedHost, new LinearLayout.LayoutParams(-1, -2));
        renderInstalled();
    }

    /** Re-renders the installed list when the page returns to the foreground. */
    @Override public void onShown() {
        renderInstalled();
    }

    /** Forces a re-render of the installed list after external changes. */
    public void reload() {
        renderInstalled();
    }

    /** Rebuilds the installed skills section with switch rows and empty-state handling. */
    private void renderInstalled() {
        installedHost.removeAllViews();
        SettingsSection installed = ExtensionKit.section(getContext(), theme,
                R.string.screen_extension_detail_section_installed);
        installedHost.addView(installed, new LinearLayout.LayoutParams(-1, -2));
        List<Skill> skills = listener.load();
        if (skills == null || skills.isEmpty()) {
            installed.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_extension_detail_empty_skills), false, 0);
            return;
        }
        for (Skill skill : skills) {
            ExtensionRows.SwitchRow row = new ExtensionRows.SwitchRow(getContext(), theme,
                    Icons.BOOK_OPEN, skill.name, description(skill));
            row.checked(skill.enabled);
            row.onToggle(enabled -> listener.setEnabled(skill.id, enabled));
            row.onLongClick(() -> openItemMenu(skill));
            installed.row(row, false, 0);
        }
    }

    /** Resolves the row description, falling back to the skill source label. */
    private String description(Skill skill) {
        if (skill.description != null && !skill.description.isEmpty()) return skill.description;
        return getContext().getString(Skill.SOURCE_PROJECT.equals(skill.source)
                ? R.string.screen_extension_detail_position_project
                : R.string.screen_extension_detail_position_global);
    }

    /** Shows the long-press menu with edit and confirmed delete actions for one skill. */
    private void openItemMenu(Skill skill) {
        List<ExtensionKit.SheetItem> items = new ArrayList<>();
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_modify,
                R.string.screen_extension_detail_modify_desc, false, () -> listener.edit(skill)));
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_delete,
                R.string.screen_extension_detail_delete_desc, true,
                () -> ExtensionKit.confirm(getContext(), theme,
                        R.string.screen_skill_delete_title,
                        getContext().getString(R.string.screen_skill_delete_message, skill.name),
                        () -> {
                            listener.delete(skill.id);
                            renderInstalled();
                        })));
        ExtensionKit.sheet(getContext(), theme, skill.name, items);
    }

    /** Shows the add-actions sheet: ZIP import, GitHub import, and manual creation. */
    private void openActions() {
        List<ExtensionKit.SheetItem> items = new ArrayList<>();
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_install_zip,
                R.string.screen_extension_detail_install_zip_desc, false, listener::importSkill));
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_install_github,
                R.string.screen_extension_detail_install_github_desc, false, listener::openGithub));
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_create_skill,
                R.string.screen_extension_detail_create_skill_desc, false, listener::add));
        ExtensionKit.sheet(getContext(), theme,
                getContext().getString(R.string.screen_extension_detail_sheet_title_skills), items);
    }
}
