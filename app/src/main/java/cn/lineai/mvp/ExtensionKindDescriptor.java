package cn.lineai.mvp;
import cn.lineai.ui.theme.IconButtonView;

import android.content.Context;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.model.ExtensionOverviewState;
import java.util.List;

/**
 * 扩展类型描述符策略接口，替代 kind 字符串 if-else 分发。
 * 每种扩展类型（agent / mcp / skills / linecode）提供各自的数据与行为实现。
 */
public interface ExtensionKindDescriptor {

    int ADD_ACTION_NONE = 0;
    int ADD_ACTION_AGENT = 1;
    int ADD_ACTION_MCP = 2;
    int ADD_ACTION_SKILL = 3;

    /** 返回此描述符对应的 kind 字符串标识 */
    String kind();

    /** 设置指定扩展的启用状态 */
    void setEnabled(ExtensionStore repository, String id, boolean enabled);

    /** 删除指定扩展 */
    void delete(ExtensionStore repository, String id);

    /** 返回此类型的页面标题 */
    String title(Context context);

    /** 返回此类型的图标常量（IconButtonView.XXX） */
    int iconType();

    /** 返回安装操作行的标题文字 */
    String inlineTitle(Context context);

    /** 返回安装操作行的描述文字 */
    String inlineDesc(Context context);

    /** 此类型在确认删除时是否展示"修改"操作（agent / mcp 展示，skills / linecode 不展示） */
    boolean hasModifyAction();

    /** 返回此类型的添加行为常量（ADD_ACTION_*） */
    int addActionType();

    /** 返回此类型已安装条目的列表，便于统一渲染 */
    List<ExtensionItem> getInstalledItems(ExtensionOverviewState state);

    /** 当已安装列表为空时返回提示文字 */
    String emptyMessage(Context context);

    /** 返回安装区块的标题（skills 用"安装 Skills"，其余用"安装"） */
    String sectionTitle(Context context);
}
