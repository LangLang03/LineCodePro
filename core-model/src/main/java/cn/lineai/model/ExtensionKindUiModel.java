package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UI layer representation of an extension kind descriptor, decoupled from
 * ExtensionKindRegistry / ExtensionKindDescriptor in the mvp package.
 */
public final class ExtensionKindUiModel {
    public static final int ADD_ACTION_NONE = 0;
    public static final int ADD_ACTION_AGENT = 1;
    public static final int ADD_ACTION_MCP = 2;
    public static final int ADD_ACTION_SKILL = 3;

    private final String kind;
    private final String title;
    private final int iconType;
    private final String sectionTitle;
    private final String inlineTitle;
    private final String inlineDesc;
    private final int addActionType;
    private final boolean hasModifyAction;
    private final String emptyMessage;
    private final List<ExtensionItemUiModel> installedItems;

    public ExtensionKindUiModel(
            String kind,
            String title,
            int iconType,
            String sectionTitle,
            String inlineTitle,
            String inlineDesc,
            int addActionType,
            boolean hasModifyAction,
            String emptyMessage,
            List<ExtensionItemUiModel> installedItems
    ) {
        this.kind = kind;
        this.title = title;
        this.iconType = iconType;
        this.sectionTitle = sectionTitle;
        this.inlineTitle = inlineTitle;
        this.inlineDesc = inlineDesc;
        this.addActionType = addActionType;
        this.hasModifyAction = hasModifyAction;
        this.emptyMessage = emptyMessage;
        this.installedItems = installedItems == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(installedItems));
    }

    public String getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public int getIconType() {
        return iconType;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getInlineTitle() {
        return inlineTitle;
    }

    public String getInlineDesc() {
        return inlineDesc;
    }

    public int getAddActionType() {
        return addActionType;
    }

    public boolean hasModifyAction() {
        return hasModifyAction;
    }

    public String getEmptyMessage() {
        return emptyMessage;
    }

    public List<ExtensionItemUiModel> getInstalledItems() {
        return installedItems;
    }
}
