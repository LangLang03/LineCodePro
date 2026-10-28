package cn.lineai.tool.api;

/** Supplies the current tool set; rebuilt when extensions change. */
public interface ToolCatalog {

    ToolRegistry registry();
}
