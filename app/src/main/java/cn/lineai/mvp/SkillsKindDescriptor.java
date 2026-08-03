package cn.lineai.mvp;
import cn.lineai.ui.theme.IconButtonView;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.model.ExtensionOverviewState;
import cn.lineai.model.SkillRecord;
import java.util.ArrayList;
import java.util.List;

final class SkillsKindDescriptor implements ExtensionKindDescriptor {

    @Override
    public String kind() {
        return "skills";
    }

    @Override
    public void setEnabled(ExtensionStore repository, String id, boolean enabled) {
        repository.setSkillEnabled(id, enabled);
    }

    @Override
    public void delete(ExtensionStore repository, String id) {
        repository.deleteSkill(id);
    }

    @Override
    public String title(Context context) {
        return context.getString(R.string.screen_extensions_section_skills);
    }

    @Override
    public int iconType() {
        return IconButtonView.ARCHIVE;
    }

    @Override
    public String inlineTitle(Context context) {
        return context.getString(R.string.screen_extension_detail_inline_title_skills);
    }

    @Override
    public String inlineDesc(Context context) {
        return context.getString(R.string.screen_extension_detail_inline_desc_skills);
    }

    @Override
    public boolean hasModifyAction() {
        return false;
    }

    @Override
    public int addActionType() {
        return ADD_ACTION_SKILL;
    }

    @Override
    public List<ExtensionItem> getInstalledItems(ExtensionOverviewState state) {
        List<SkillRecord> skills = state.getSkills();
        List<ExtensionItem> items = new ArrayList<>(skills.size());
        for (SkillRecord skill : skills) {
            String desc = skill.getLocationLabel() + " \u00b7 " + skill.getSkillMdPath();
            items.add(new ExtensionItem(skill.getId(), skill.getName(), desc, skill.isEnabled()));
        }
        return items;
    }

    @Override
    public String emptyMessage(Context context) {
        return context.getString(R.string.screen_extension_detail_empty_skills);
    }

    @Override
    public String sectionTitle(Context context) {
        return context.getString(R.string.screen_extension_detail_section_install_skills);
    }
}
