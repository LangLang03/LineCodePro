package cn.lineai.model;

public final class ChatMode {
    public static final String CHAT = "chat";
    public static final String PLAN = "plan";
    public static final String AGENT = "agent";
    public static final String DEFAULT = AGENT;

    private ChatMode() {
    }

    public static String normalize(String mode) {
        if (CHAT.equals(mode)) {
            return CHAT;
        }
        if (PLAN.equals(mode)) {
            return PLAN;
        }
        return AGENT;
    }

    public static String promptContext(String mode) {
        String normalized = normalize(mode);
        if (CHAT.equals(normalized)) {
            return "## 当前会话模式\n"
                    + "当前模式：Chat。\n"
                    + "- Chat 是只读交流模式，只用于回答问题、解释代码、读取上下文、搜索资料和列目录。\n"
                    + "- 允许使用明确只读工具：file_read、glob、list_dir、web_search、web_fetch。\n"
                    + "- 禁止写入、编辑、删除文件；禁止执行 Shell；禁止启动服务、运行构建、运行测试、安装依赖、修改权限或分派 Agent。\n"
                    + "- 如果用户要求实现、修复、迁移、执行命令或验证结果，只说明需要切换到 Agent；如果用户只是要方案，可以建议切换到 Plan。";
        }
        if (PLAN.equals(normalized)) {
            return "## 当前会话模式\n"
                    + "当前模式：Plan。\n"
                    + "- Plan 是只读规划模式。目标是理解需求、读取必要上下文、形成计划、列出风险、确认验收方式；不要执行计划。\n"
                    + "- 允许使用明确只读工具收集信息：file_read、glob、list_dir、web_search、web_fetch。读取时只读最小必要文件，不要扩大范围。\n"
                    + "- 禁止调用任何会改变状态的工具：file_write、file_edit、file_delete、http_server、agent、agent_pipeline、自定义 Agent、写入型 MCP 或未知副作用工具。\n"
                    + "- 本地工作区目标下禁止执行 Shell；本地项目请用 file_read、glob、list_dir 读取上下文。\n"
                    + "- SSH Shell 目标下，本地 file_read、glob、list_dir 不可用时，允许用 shell_execute 查看远端项目内容，但只能执行无副作用的项目读取命令。\n"
                    + "- SSH Plan 允许的 shell 范围：pwd、ls/find 查看目录、cat/sed/head/tail 查看文件、grep/rg 搜索文本。命令必须限定在当前项目目录，优先限制深度、行数和结果数量。\n"
                    + "- SSH Plan 禁止检测和改变环境：不要运行 git status、构建、测试、安装、包管理器、启动服务、写入重定向、tee、touch、mkdir、rm、mv、cp、chmod、chown、kill、ssh/scp/rsync、curl/wget 下载、数据库命令或任何可能改变远端状态的命令。\n"
                    + "- 禁止修改本地工作区、SSH 远端、Skills 目录、配置、数据库、依赖、权限、进程或网络服务；也不要创建临时文件。\n"
                    + "- 如果完成任务需要写文件、运行测试、构建、安装、启动服务、调用 Agent 或确认真实环境状态，必须停止在计划阶段，明确告诉用户切换到 Agent 后再执行。\n"
                    + "- 输出应是可执行计划：先给结论，然后列步骤、涉及文件、需要的工具、验证命令、风险和需要用户确认的问题。";
        }
        return "## 当前会话模式\n"
                + "当前模式：Agent。\n"
                + "- Agent 是执行模式。用户要求实现、修复、迁移、验证或执行命令时，可以主动读取上下文、调用工具、修改文件并验证。\n"
                + "- 执行前仍要保持范围最小，优先读取相关文件，说明明显风险；写入、删除、Shell 和 SSH 操作必须遵守当前工具权限与确认机制。\n"
                + "- SSH Shell 下要明确命令目的和工作目录，避免无关探测；不要修改任务无关文件、环境、依赖或服务。\n"
                + "- 完成后总结改动、验证结果和剩余风险；如果被权限、缺少配置或环境问题阻塞，说明具体阻塞点和下一步。";
    }
}
