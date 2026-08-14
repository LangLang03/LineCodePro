# 更新日志

## v1.2.6

### 聊天滚动体验优化（嵌套滚动冲突修复）

- **共享 `BoundedScrollView`（`:ui-theme`）** - 新增方向感知的受限滚动容器：`ACTION_DOWN` 在内容可见且有界时保守抢占手势（不依赖布局完成，消除流式更新中测量时序误判），`ACTION_MOVE` 仅当内容可滚且拖动方向仍有剩余滚动时保持手势（到达边界或内容不可滚时释放，外层列表平滑接管），`UP/CANCEL` 一律释放；`shouldHandleDrag` / `canScrollContent` / `shouldDisallowOnDown` 为纯静态决策方法，可脱离 Android 环境单测
- **思考区滚动修复** - `ThinkingBlockView` 删除私有 `MaxHeightScrollView`，改用共享 `BoundedScrollView`；原实现对 `canScrollContent()` 的依赖在内容每帧重测时可能误判 false，导致下拉手势被外层聊天列表抢走；现改为「保守抢占 + 按方向动态交还」，流式输出中在思考区拖动不再整列表乱飞
- **`ToolCallGenericView` 裸 ScrollView 修复** - 泛型/MCP 工具卡的长内容原本使用无任何拦截逻辑的原生 `ScrollView`，内滚必被外层 ListView 抢占；改用共享 `BoundedScrollView`（220dp），内滚优先、边界交还
- **消除无条件 disallow 滚动死区** - `ToolCallShellView` / `ToolCallAgentView` / `ToolCallAgentPipelineView` 各自的私有 `BoundedScrollView` 在 `onTouchEvent` / `onInterceptTouchEvent` 中无条件 `requestDisallowInterceptTouchEvent(true)` 且从不释放，内容短（不可滚）时外层列表永远无法滚动，表现为"滑动无反应"；全部替换为共享类，并删除 `ToolCallShellView` 冗余的 `setOnTouchListener` 双重拦截
- **`ToolCallReadView` 长内容有界滚动** - 读取文件的长结果（>300 字符或 >8 行）不再直接撑高聊天列表行，包进 220dp `BoundedScrollView` 内部滚动，内容尾部可达，列表滚动性能提升

### 工具卡片流式渲染性能优化

- **`ToolCallCardView.updateContent` 增量钩子** - 接口新增 `default updateContent(call, result)`（默认回退 `bind`，完全向后兼容），为卡片提供「仅内容变化时增量更新」的契约
- **`ToolCallBlockView` 结构/内容签名拆分** - 原 `signature()` 包含完整 `result.content`，流式输出每帧内容变化都会 `removeAllViews` + 重建整棵卡片视图树（几十个 View）；现拆分为 `structureSignature`（卡片类型 / 状态 / 参数）与 `contentSignature`（正文）：结构变 → 重建，仅内容变 → 调用已存 `childView.updateContent(...)` 增量更新，不再销毁重建
- **`ToolCallReadView` / `ToolCallGenericView` 增量更新** - 缓存消息行 / 输出 section 的 TextView，流式时仅 `setText`；`computeTerminalStatus` 由内容空/非空推导（UNKNOWN↔SUCCESS），状态切换或换行溢出切换滚动容器时回退完整绑定，保证图标/颜色过渡不变
- **`ToolCallAgentView` 增量更新** - 新增纯静态 `layoutSignature`（progressStatus / reviewState / isError / agentId / toolCount / thinking / output 存在性 / name / type / nested 数量）判定结构：签名不变时仅更新 thinking 与输出 Markdown，签名变化（如 running→done）回退完整绑定保留进度条→图标、pills 过渡
- **`ToolCallAgentPipelineView` 保守增量** - summary 计数与状态未变时仅更新汇总 Markdown；有 agent 行的场景保守全量重建（行为不变，性能优先场景为 agents 空时的汇总输出）
- **`BaseToolCallView.addMessageRow` 返回值** - 由 `void` 改为返回创建的 `TextView`，供卡片捕获并增量更新文本

### 测试

- 新增 `BoundedScrollViewTest`（`:ui-theme`，13 断言组）覆盖 `shouldHandleDrag` 全方向/边界组合、`canScrollContent` 边界、`shouldDisallowOnDown` 八种组合、DOWN→MOVE→反向→UP 完整手势序列决策
- 新增 `ToolCallBlockViewSignatureTest`（13 例）覆盖结构/内容签名拆分：仅内容变化签名相等、isError/reviewState/id/name/arguments/projectPath 变化签名不等
- 新增 `ToolCallAgentViewLayoutSignatureTest`（13 例）覆盖 agent 进度 JSON 的 status / tool_call_count / agent_id / output 存在性对布局签名的影响
- 新增 `MemoryRankerRagInjectionTest` 证明 RAG 排序会选中记忆候选（`MemoryRanker` 相关性注入）

### 功能与改进

- **MCP 协议升级（2025-03-26）** - 自定义 HTTP 工具（`CustomMcpHttpTool`）支持新版 MCP 协议：请求前初始化会话并携带 `Mcp-Session-Id` 会话管理、`Mcp-Protocol-Version` 协议版本头
- **工具调用去重** - 原生与文本工具调用按签名去重，同一工具调用不会被执行两次
- **工具调用上限提示** - 达到主流程总工具调用次数上限时给出明确的终止提示（多语言支持）
- **进度监听器透传** - `ToolContext` 新增进度监听器 accessor 并透传给工具执行器，供工具上报流式进度
- **学习上下文改进** - 学习模式关闭时改用手动记忆构建学习上下文（不再误用自动抽取）
- **响应头暴露** - `SimpleHttpClient` 捕获并暴露响应头，供调用方读取（如 MCP 会话头）
- **Kimi 模型兼容层** - 新增 Kimi/Moonshot 推理策略兼容层（温度下限 1），`OpenAiCompatibleProtocol` 按模型适配
- **`memory_update` 可见性** - `memory_update` 工具在学习模式开启后可见（`ToolSettingsRepository` 按模式过滤）
- **死代码清理** - 删除从未被调用的 `MemoryExtractionService`（587 行）及其专属 prompt 模板（memory/skill-extraction）与相关字符串，`CLAUDE.md`/`README` 引用同步更新

### 全量代码审计修复

对全项目（core / data / feature-model / feature-tool / feature-ssh / feature-share / markdown / terminal-provider / app 共 11 个范围）进行了系统性代码审计并逐项核实，修复**高危 2 项、中危 30 项**：

**文件读取（`file_read`）**
- 修复 KB 分页读取**只返回第一行**：行边界对齐的 `indexOf`/`lastIndexOf` 搜索方向写反，非末页读取被截断到第一个换行
- `end_kb` 不再硬性限制 50KB，可按文件大小分页读取整文件（单次跨度上限 1MB 防内存暴涨），参数说明同步更新
- 总行数 / 起始行号统计改为 64KB 分块缓冲读取，替换逐字节 `seek+read`（大文件不再卡死工具线程）
- 消除内容末尾换行的幻影空行号、总行数多计 1 行的问题

**Shell 执行（Terminal Provider）**
- 修复执行命令输出**只返回最后一行**：输出回调逐块覆盖缓冲改为累积（SSH 路径同步修正）
- 命令超时后击杀**整个进程树**（扫描 /proc ppid 链，含子进程），并抑制超时后的过期回调；正常完成只销毁 shell 本体，不误杀 `nohup ... &` 后台任务
- `readFile` 单次读入内存上限 32MB（超大文件走 `readFileChunk` 分块），防 OOM
- Terminal Provider 定位为**开放插件**：手动开启 + 工具层切换执行模式后才被使用，不设签名/包名等调用方校验，兼容 GPLv3 第三方渠道重签名分发

**安全加固**
- `UrlPolicy` 私网判定由主机名**前缀匹配**改为字面 IPv4 校验（`10.evil.com`、`192.168.attacker.example` 等公网域名不再被放行明文 HTTP；`10.0.2.2` 模拟器别名不受影响）
- `SimpleHttpClient` 响应体增加 32MB 上限；3 参 `download` 不再传 `Integer.MAX_VALUE`；字节计数 `int` 溢出改 `long`
- 图片生成工具下载上限（12MB）/ 响应上限（24MB）此前定义了但从未生效，现已真正执行
- Markdown `data:image` base64 解码增加 5MB 上限 + `inSampleSize` 降采样（防恶意/超大内嵌图 OOM）
- 错误日志 `summary` 字段补上脱敏（此前 details/堆栈已脱敏）；`.linecode` 导入的 `fileName` 消毒 + 规范路径校验（防 `../` 穿越读取任意文件）
- 中文图片占位标签 `[图片]` 不再被截成 `[图`（内联渲染与回退文本两处）

**上下文压缩**
- 修复 Codex/Responses 压缩路径不重置 `TokenUsageTracker`，压缩后基线残留旧 usage 导致反复过度压缩

**并发与稳定性**
- `SshConnectionPool` 并发首次借用竞态：`put` 覆盖丢条目、锁永不释放、`release` 对非持有锁 `unlock` 抛 `IllegalMonitorStateException` → `putIfAbsent` + 锁守卫
- `ToolRegistry` 增加读写锁（后台 `reloadExtensions` 与主线程读取竞争，`ConcurrentModificationException` 风险）
- 会话持久化移出主线程热路径：单线程后台执行器 + latest-wins 合并，切换会话前等待落库（防 ANR 与过期覆盖）
- 工具调用预算只统计**实际执行**的工具（暂停确认/拒绝/跳过不再空耗额度）；Agent 内部工具调用并入全局上限并按轮重置
- Skill 创建/安装（含 GitHub 下载）、聊天导出（PDF/图片）移出主线程；MCP 请求头改为主线程快照（消除工作线程读 EditText 竞态）

**UI 与细节**
- `MainChatView` 屏幕缓存上限 12 条并淘汰最旧（动态 screen id 每次导航泄漏 View/Context 的内存问题）
- 存储页刷新按钮误绑定"返回"、统计查询移出主线程；压缩模型"查询列表"按钮实时读取凭据（新增模型不再永远禁用）
- Agent 编辑页分区标题 `%2$s` 误传按钮文案 → 新增 `screen_agent_tools_selected`（三语言）
- PDF 导出宽度换行 off-by-one 丢字符；无障碍点击只点击第一个匹配节点；SKILL.md 读取有界化
- 新增/加强回归测试：`ToolBuiltinsTest` 23 例（KB 分页多行、中间页连续行号、end_kb>50）

### 版本

- 版本号升级到 `1.2.6`（正式版）
- `versionCode` 升级到 `31`

---

## v1.2.5

### 工具调用 UI 模块化重构

- **新增 `:tool-ui` 模块** - 从 `:app` 拆分出独立模块承载全部 ToolCall 卡片视图（`ToolCallBlockView`、`BaseToolCallView`）、工厂注册表（`ToolCallViewFactoryRegistry`）、`ToolInfoResolver` 契约接口；`:app` 改为依赖 `:tool-ui`，解决 app 模块与工具视图的紧耦合
- **`BaseTool.getToolCallViewClass()`** - 19 个内置工具声明对应视图类（如 `FileReadTool` → `ToolCallReadView`），移除 `MainDependencies` 按分类集中注册 UI 的旧方式；未声明的工具走 `ToolDisplayCategory` 分类回退
- **`ToolCallViewFactoryRegistry` 两级解析** - 精确匹配工具声明的视图类 → 分类兜底 → `GENERIC` 兜底；支持按 `Class` 和 `ToolDisplayCategory` 双维度注册
- **工具调用 UI 预览页** - 输出设置新增「工具调用 UI」入口（`ToolCallPreviewScreenView`），遍历注册表全部 `ToolCallViewFactory`，用 `ToolCallPreviewSamples` 模拟数据渲染每种卡片，便于不跑真实工具时检查 UI
- **包结构迁移** - `ToolCall` / `ToolResult` 迁至 `cn.lineai.model.tool`；`IconButtonView` 等共享组件与 84 个 drawable 迁至 `:ui-theme`；`cn.lineai.tool` 包整体迁至 `:feature-tool`；`ExtensionStore` / `ExtensionOverviewState` 迁至 `:data`

### 上下文压缩增强

- **Token 用量追踪** - 新增 `TokenUsageTracker`（`:feature-model`），记录最近一次模型响应中服务器观测到的 `input_tokens` / `output_tokens`；触发检查优先使用观测值（含系统提示 + 工具定义），协议未返回 usage 时回退到 `ContextManager` 本地估算
- **软压缩开关** - `AiBehaviorSettings` 新增 `softCompactionEnabled` 字段（默认开启）；关闭后只保留 80% 硬触发，压缩行为对齐 codex：仅在上下文占用达到硬阈值时全量压缩并保留最近用户消息
- **硬压缩 codex 对齐** - 硬触发（80%）在模型完成回合 + 工具执行完毕后、继续模型循环前执行（`GenerationFlowController.continueModelAfterTools` → `shouldAutoCompactMidLoop` → `startToolLoopContextCompaction`）；压缩后保留最近用户消息原文（`COMPACT_USER_MESSAGE_MAX_TOKENS = 20000`），摘要作为用户消息追加，布局：`[base] + [retainedRecentUserMessages] + [summary] + [preservedTail]`
- **压缩重试** - `ContextCompactionService` 新增 `MAX_COMPACT_RETRIES` 重试 + 退避机制；压缩调用使用 codex 风格 handoff-summary 模板（`context-compaction-template.txt` + `context-compaction-summary-prefix.txt`），压缩后的 usage 记入 `TokenUsageTracker` 作为后续基线
- **会话切换重置** - `TokenUsageTracker` 在对话切换时由 `ContextCompactionController.onConversationChanged` 重置

### 教程重写

- **章节化详细指南** - `simple.md`（29 章）与 `pro.md` 全面重写：覆盖主页/侧边栏导览、权限确认卡语义（同意=已写入生效、拒绝=撤销还原）、模型管理/工具与执行/扩展/主题/安全/数据等每个子设置页的字段级说明；移除本地 GGUF 与实验性键盘避让等未接入功能
- **教程页章节目录** - 新增横向 chips 导航，解析 `##` 章节生成按钮，点击平滑跳转
- **设置页入口** - 设置页顶部新增「查看教程」入口，返回回到设置页

### 代码质量优化

- **命名规范统一** - 多个 setter 重命名为更语义化的前缀：`setPathFromRemoteRoot` → `applyPathFromRemoteRoot`；`AgentProgressSession` 使用 `mark` / `update` 前缀替代 `set`
- **不可变对象** - `StorageStatsUiModel` 改为不可变类，构造函数初始化 + getter；`ChatUiState` 新增防御性拷贝与不可变列表包装
- **`ToolCallUtils` 移至 `:tool-ui`** - 工具调用工具类从 `:app` 迁入 `:tool-ui`，消除 app 层对工具视图细节的直接依赖

### 测试

- 新增 `ChatUiStateTest`（111 行）覆盖防御性拷贝与不可变列表
- 新增 `ToolCallViewFactoryRegistryTest`（50 行）覆盖视图类精确匹配与分类回退
- 新增 `ToolCallPreviewSamplesTest`（37 行）覆盖每种分类的模拟数据生成
- 新增 `ContextCompactionServiceTest`（91 行）覆盖 token 用量追踪、硬压缩保留最近用户消息、重试机制
- 扩展 `ToolBuiltinsTest`（18 行）验证所有内置工具声明 `getToolCallViewClass()`

### 版本

- 版本号升级到 `1.2.5-rc.2`

---

## v1.2.4

### 历史对话丢失修复

- **消息 ID 跨会话冲突根因修复** - 原先 `ChatSessionStore.nextMessageId()` 全局生成 `m1` / `m2`…，而 `messages.id` 为 PRIMARY KEY 且保存走 `INSERT OR REPLACE`，新建会话会覆盖旧会话同 ID 消息行，导致侧边栏只剩最近一次对话；`conversation_index` 无外键清理，检索仍能搜到「幽灵」旧记录。现改为 `{conversationId}_m{n}` 按会话隔离
- **落库兼容旧裸 ID** - `ConversationRepository.scopedMessageId` 对历史 `m1` 形式写入时加会话前缀（`{convId}:m1`），避免再保存时撞车；`ChatSessionStore.sequenceFromMessageId` 同时解析 `mN` / `{id}_mN` / `{id}:mN`
- **删除/清空同步清索引** - `deleteConversation` / `clearAll` / `StorageStatsRepository.clearChatHistory` 同步删除 `conversation_index`（及 FTS），避免删会话后检索残留
- **进后台主动落盘** - `MainCoordinator.onEnterBackground` 调用 `persistCurrentConversation()`，降低 `onPause` 后被系统杀进程丢未落库内容的概率

### 文件编辑工具与工具结果对齐

- **`file_edit` 默认唯一匹配** - 多处匹配且未设 `replace_all=true` 时失败不写盘，并提示提供更唯一的 `old_string` 或开启全量替换；默认只替换唯一匹配的第一处
- **用户撤销写操作后模型不再读成功文案** - `ToolMessageController.updateToolReview` 在 `rejected` 时将 tool 消息标 `isError=true` 并改写 content 为明确「用户已拒绝并还原」说明；嵌套 agent 结果同步更新 `is_error` / `content`
- **OpenAI 工具错误前缀** - `OpenAiMessageSerializer` 对 `isToolError` 结果加 `Tool <name> failed:\n` 前缀（与 Codex 路径一致），避免协议无结构化 error 字段时模型误判成功
- **`ToolArgsCleaner` 保留多行空白** - 字符串内未转义的 `\n` / `\r` / `\t` 转为 JSON 转义，避免增量 `old_string` 被拍扁导致匹配失败

### 扩展与 Skill

- **从 GitHub 安装 Skill** - 新增 `GitHubSkillInstaller`：支持 `github.com/org/repo`、blob/tree 路径、`raw.githubusercontent.com` 的 `SKILL.md`；仓库用 codeload zip 下载并查找 `SKILL.md`。扩展 → Skills → 添加菜单新增「从 GitHub 安装」
- **Skills 多选批量删除** - 扩展详情页长按 Skill 进入多选，顶部显示已选数量，可批量删除；`ExtensionStore.deleteSkills` / `ExtensionManagementController.deleteExtensions` 贯通
- **工作区 FileProvider** - 新增 `WorkspaceFileProvider`（`content://{appId}.workspace/...`）暴露内部 `files/.linecode`；Skills 页增加「分享工作区目录」入口（`WorkspaceShareHelper`）

### 思考深度与模型预设

- **思考深度 Auto** - `AiBehaviorSettings.REASONING_AUTO`；LLM 设置与 `/model` 级别列表增加 Auto；协议层 `auto` 视为开启思考，具体 effort 映射为 `medium`（MiniMax 仍走 adaptive）
- **新增模型提供商预设** - Groq、Together AI、SiliconFlow、MiniMax、Ollama、LM Studio（OpenAI 兼容），`ModelProviderPresets` + 三语文案同步

### 测试

- 新增 `ConversationMessageIdScopeTest`、`GitHubSkillInstallerTest`、`AiBehaviorSettingsAutoTest`
- 扩展 `ChatSessionStoreTest`、`ToolMessageControllerTest`、`ToolBuiltinsTest`（file_edit 唯一/全量）、`ToolArgsCleanerTest`、`OpenAiCompatibleProtocolTest`、`SlashCommandCatalogTest`

### 版本

- 版本号升级到 `1.2.4`
- `versionCode` 升级到 `27`

---

## v1.2.3


### 国际化与多语言

- **全量 i18n 替换与术语统一** - 将项目内全部硬编码中文提示、日志、枚举标签、错误文案统一替换为英文，覆盖 `feature-model`、`core-model`、`feature-tool`、`data`、`core-security`、`app` 等模块：包含 `ChatMode.promptContext` 中 Chat / Plan / Control / Agent 四种会话模式说明、`SystemPromptProvider` 调用的全部 `feature-model/src/main/assets/prompts/*.txt` 模板（`system-prompt-template.txt`、`agent-role-coding-local.txt` / `agent-role-coding-remote.txt` / `agent-role-explore-local.txt` / `agent-role-explore-remote.txt`、`memory-extraction-template.txt`、`learning-context-template.txt`、`work-directory-template.txt`、`tone-chat-template.txt`、`tone-coding-template.txt`、`todo-usage-template.txt`、`todo-state-template.txt`、`model-identity-template.txt`、`image-understanding-tool-system.txt`、`skill-extraction-template.txt`）、`ModelProtocolType` 协议标签（OpenAI / Codex / Anthropic / Local）、`SheetOption.getDeleteActionLabel`、协议层错误描述、分享格式前缀、工具异常提示、工具提示词渲染等
- **`:app` 新增 50+ 条多语言字符串** - 在 `app/src/main/res/values/strings.xml` 与 `values-zh/strings.xml` 同步补充 `model_provider_preset_*_label/desc/hint` 系列（custom / deepseek / glm / mimo / mimo-token-plan / kimi / qwen / openai / claude / gemini / openrouter / codex 共 12 套）、`memory_extraction_json_only`、`agent_execution_completed`、`agent_pipeline_completed`、`user_rejected_tool`、`remote_file_tree_loading`、`skill_source_not_found`、`skill_ssh_directory_hint`、`skill_zip_entry_out_of_bounds`、`extension_draft_missing_fields`、`extension_draft_invalid_json` 等
- **`:app` 新增俄文资源** - `app/src/main/res/values-ru/strings.xml` 一次性补齐 1251 行，覆盖模型预设、内存抽取、Agent 执行、技能草稿等所有英文新增文案
- **`:feature-tool` 新建模块级资源** - 全新 `feature-tool/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-ru/strings.xml`（228 / 228 / 55 行）：按工具分组为 `FileReadTool` / `FileEditTool` / `FileWriteTool` / `FileDeleteTool` / `GlobTool` / `ListDirectoryTool` / `AgentTool` / `AgentPipelineTool` / `TodoUpdateTool` / `ImageGenerationTool` / `ImageUnderstandingTool` / `WebSearchTool` / `WebFetchTool` / `WebSearchService` / `CustomMcpHttpTool` / `CustomAgentExtensionTool` / `ImageResponseParser` / `ShellExecuteTool` / `FileToolPathPolicy` / `ToolArgs` / `ExceptionUtils` 共 21 组错误与状态文案，以及 7 个工具动作标签（`tool_call_action_read` / `search` / `fetch` / `match` / `list_dir` / `image_generation` / `image_understanding`）
- **Phone 工具俄文补齐** - `feature-tool/src/main/res/values-ru/strings.xml` 补充 55 行：phone 工具动作名（`tool_call_phone_action_screenshot` / `click` / `swipe` / `long_press` / `view_hierarchy` / `click_view` / `global_action` / `default`）、phone 工具摘要、全局动作本地化名（back / home / exit_app / recents / notifications / quick_settings / power_dialog / lock_screen / unknown）、`phone_tool_accessibility_*` 提示与 `phone_tool_screenshot/click/swipe/long_press/view_hierarchy/click_view/global_action_*` 等描述/成功/失败文案
- **模型预设抽取为资源键** - `ModelProviderPreset` 移除 `label` / `desc` / `hint` 内置字符串字段，改用新增的 `cn.lineai.ui.util.ModelProviderPresetStrings` 注册表（`register(id, labelResId, descResId, hintResId)` + `getLabel/Desc/Hint(Context, id)`），由 `:app` 在 strings.xml 提供多语言文案，`ModelProviderPresets` 同步移除文本构造参数
- **`ToolContext` 字符串解析** - `ToolContext` 新增 `StringResolver` 字段与 `getString` 优先走 `stringResolver`，避免在无 Android Context 场景下文案无法解析；内置工具全部 `getDescription` / `getActionName` / `promptSupplement` / 错误返回改用英文；`FileReadTool` / `ImageGenerationTool` / `ImageUnderstandingTool` 错误返回全部经 `context.getString(R.string.tool_*)` 解析
- **Bing RSS 搜索 market 动态化** - `BingRssSearchProvider` 的 `mkt` 参数从硬编码 `zh-CN` 改为 `Locale.getDefault().toLanguageTag()`，缺失回退 `en-US`，跟随系统语言
- **`FakeResourceContext` 测试桩** - 新增 `feature-tool/src/test/java/cn/lineai/tool/FakeResourceContext` 解析 `feature-tool/src/main/res/values/strings.xml` 并通过 `R.string.*` 反射查表，单元测试中可替代 `Context.getString`（被 Robolectric/Android 框架默认 stub 抛 "not mocked"），`ToolBuiltinsTest` 全面接入

### 模型协议与配置

- **`ModelProtocolType` 携带协议能力** - 枚举增加 `dedicatedCompression` 字段与 `supportsDedicatedCompression()` 方法；`OPENAI_COMPATIBLE` / `CODEX_RESPONSES` 标记为 `true`，`ANTHROPIC_MESSAGES` / `LOCAL_GGUF` 标记为 `false`，`ModelConfig` 删除 `@Deprecated public static boolean supportsDedicatedCompression(ModelProtocolType)`，统一通过枚举属性判断
- **`ModelConfig` 构造器收敛** - 移除 3 个 `@Deprecated` 旧构造器（含 `compressionModelEnabled` / `compressionModelAuto` / `compressionModelId` / `contextSize` 全字段版），保留 `Builder` 模式；`compressionModelEnabled` 在构造时直接按 `protocolType.supportsDedicatedCompression()` 二次校正
- **模型目录拉取接口化** - 新增 `cn.lineai.ai.protocol.CatalogFetcher` 接口与 `OpenAiCatalogFetcher` / `AnthropicCatalogFetcher` / `CodexCatalogFetcher` 三个实现；`ModelCatalogClient` 重构为注册表 + 通用 `fetch(...)` 调度，删除 78 行硬编码分支
- **Codex 协议拆分** - 从 `CodexResponsesProtocol` 抽离 `CodexRequestBuilder`（149 行，请求体拼装）和 `CodexOutputMerger`（191 行，SSE 输出合并 + `function_call` / `custom_tool_call` / `reasoning` / `output_text` 归并），`CodexResponsesProtocol` 大幅缩减；`OpenAiCompatibleProtocol` 同步拆出 `OpenAiMessageSerializer`（74 行，消息序列化）
- **`ModelAddOptionsScreenView` / `ModelAddScreenView` 拆分** - 模型添加页 716 → 缩减，新增 `ModelCompressionSectionView`（333 行，压缩模型配置区：query 按钮、loading 态、picker 选择、custom id 切换）、`ModelFormHelper`（88 行，label / input / toggle / switch tint / value / detachFromParent 复用工具）、`ModelPickerDialog`（106 行，底部弹窗，列出目录模型 + 自定义 id 选项）

### 推理策略与模型搜索

- **统一 thinking 参数格式** - 各 `ReasoningRequestStrategy` 统一按 `context.isEnabled()` 与 `context.getEffort()` 写入请求体；`DeepseekReasoningStrategy` 移除对 `AiBehaviorSettings.REASONING_MAX` 的硬编码判断，直接 `body.put("reasoning_effort", context.getEffort())`；`DefaultReasoningStrategy` 在 `AiBehaviorSettings.REASONING_MAX.equals(effort)` 时映射为 `"xhigh"`，否则原样写 `effort`；`MinimaxReasoningStrategy` 额外写入 `body.put("thinking", new JSONObject().put("type", enabled ? "adaptive" : "disabled"))`
- **ComposerView 模型搜索** - `/model` 命令候选列表新增大小写不敏感子串匹配：`name` / `modelId` / `id` / `providerLabel` 任意字段包含查询词即保留；模型下拉候选项从只显示 id 改为 `modelDisplayName`（name 优先，回退 apiId，再回退 providerLabel）+ `modelDisplayDetail`（`providerLabel · apiId`），无 apiId 时只显示 providerLabel

### 工具系统重构

- **`ToolResult` 工厂化** - 统一 `ToolResult.success(output)` / `error(error)` / `withReview(...)` / `of(...)` 静态方法替代 `new ToolResult(...)`；`ToolExecutor` 内部错误返回改用 `ToolResult.error` / `ToolResult.of`
- **工具默认权限配置** - 新增 `cn.lineai.data.repository.ToolSettingsStore` 默认实现细节（`toolSettingsRepository = new ToolSettingsRepository(resourceProvider, settingsRepository, webSearchConfigRepository, phoneControlRepository, categoryResolver)`），`ToolPermissionService` 新增（86 行）将权限判断从 `ToolExecutor` 中拆出
- **DiffRecorder 抽离** - 新增 `cn.lineai.tool.DiffRecorder`（57 行），把 `shouldRecordDiff(tool)` 与 `executeWithDiff(tool, input, context)` 从 `ToolExecutor` 抽出；`ToolExecutor` 持有 `DiffRecorder` 引用，需要时委托；`ToolExecutor` 构造签名同步调整为 7 参数版 + 8 参数（多 `learningContextStore` 注入），`MainDependencies` 接入新签名
- **`PhoneControlToolSupport` 拆分与接口化** - 原 `app/.../tool/builtin/PhoneControlToolSupport` 工具类（37 行壳）拆为 `:feature-tool` 模块的 `PhoneControlToolSupport`（85 行，绑定无障碍服务 `LineCodeAccessibilityService.getReadyInstance(ctx)`），新增 `cn.lineai.tool.PhoneControlService` 接口（28 行）解耦手机工具与无障碍服务实现；`AccessibilityStateProvider` 接口（9 行）由 `LineCodeAccessibilityService.isServiceEnabled` 实现
- **内置工具操作名/图标** - 25+ 个内置工具统一新增 `getActionName(Context)` 返回 `R.string.tool_call_action_*` 多语言资源与 `getActionIcon()` 返回 `ICON_*` 常量；`FileReadTool` 错误提示从硬编码中文改为 `context.getString(R.string.tool_file_read_*)` 共 10 个资源
- **`ToolRegistry` 扩展** - 接受 `ExtensionStore` 注入并对外暴露 `setExtensionStore`；`BuiltInToolProviders` 注册新内置工具时同步带 `NAME` 常量
- **`ToolContext` 字段扩展** - 构造器新增 `LearningContextStore` 与 `StringResolver` 字段及对应 Builder 方法；`BaseTool.isConcurrencySafe` / `BaseTool.execute` 路径与 `ToolSettingsRepository` / `LearningContextStore` 全部接入新字段

### Agent 系统增强

- **`agent_output` 工具** - 新增 `cn.lineai.tool.builtin.AgentOutputTool`（185 行，`NAME = ToolNames.AGENT_OUTPUT`），通过 `ToolContext.getAgentResultStore()` 按 `agent_id` 拉取会话内已存的 Agent 结果；参数 `include: output` 返回完整正文（受 `ToolResult.MAX_TOOL_RESULT_CHARS` 截断），`include: meta` 返回 `agent_id / status / type / description / preview / error / async / tool_call_count` 字段；Agent 仍 running 时返回结构化 `running` 状态 JSON；`isAllowedInReadonlyMode = true`，`isConcurrencySafe = true`，`getDisplayCategory = READ`，`getActionIcon = ICON_BOT`
- **异步 Agent 执行** - `AgentTool` 参数新增 `async: boolean`（仅 explore Agent 允许；sub-coding 传 `async=true` 时立即返回错误）；`AgentExecutionController.runAgentTool` 检测 `async` 时 `agentResultRegistry.allocateId()` + `runningRecord` 落库 → 写入 `ToolResult.reviewState = "running"` + `host.runInBackground(...)` 异步跑 `runAgentLoop`，完成后用 `finishAgentWithCompact` 落定结果并把 `compact` JSON 替换原 tool result
- **Agent 结果注册表** - 新增 `cn.lineai.mvp.agent.AgentResultRegistry`（170 行，会话级 `LinkedHashMap<String, AgentResultRecord>` + `AtomicInteger id` 分配器，提供 `allocateId / put / getRecord / get / setFullOutput / toCompactJson`），`AgentResultRecord`（220 行，含 `agentId / toolCallId / status / type / description / preview / fullOutput / thinking / progressJson / toolCallCount / error / async / generationId / updatedAtMs` + `running(...)` 工厂 + `withPreview/withStatus/withFullOutput` 不可变更新）；`ToolContext.AgentResultStore` 抽象由 `agentResultRegistry` 实现并由 `GenerationFlowController` 通过 `ToolContext.Builder.agentResultStore(...)` 注入
- **Agent 工具返回压缩** - `finishAgentWithCompact` 把原本"任务标题 + 类型 + 工具调用次数 + 输出"的长字符串结果替换为 `linecode_agent_ref` 精简 JSON（`agent_id / status / type / description / preview / async / tool_call_count`），完整正文留在 `agentResultRegistry`，由后续 `agent_output` 拉取
- **PipelineProgressSession 线程安全** - `PipelineProgressSession` 所有 setter / `publish` / `payload` 增加 `synchronized (lock)` 块；新增 `Object lock` 字段；`setStatus` / `setFinalSummary` / `beginAgent` / `updateAgent` / `finishAgent` / `terminate` / `publish` / `payload` 等方法全部加锁，`buildPayload` 拆为 `buildPayloadLocked`，`countStatus` / `countFailed` 拆为 `*Locked`
- **工具预算原子化** - `runAgentTool` / `runAgentPipelineTool` / `runAgentLoop` 中 `int[] toolCallBudget` 替换为 `AtomicInteger toolCallBudget`，跨 sub-agent 调用时 `toolCallBudget.incrementAndGet()` 共享主流程 `toolCallLimit`
- **AgentTool 显示与图标** - `AgentTool` 新增 `getActionIcon = ICON_BOT`、`getActionName = "Agent"`；`BaseTool` 新增 `ICON_BOT` 常量；`ToolCallAgentView` 卡片元信息新增 `agent_id` 标签，title 文本由 `AgentToolResultDisplay.description` 提供避免显示空内容

### 记忆与上下文压缩

- **`MemoryUpdateTool` 持久化用户偏好** - 新增 `cn.lineai.tool.builtin.MemoryUpdateTool`（133 行，`NAME = "memory_update"`），参数 `content`（≤ `MAX_CONTENT_CHARS = 320`，超出截断 + `。`）+ `scope: user / project / environment`；通过 `ToolContext.getLearningContextStore().saveMemory("", scope, homePath, content)` 写入；拒绝包含 `api key` / `password` / `secret` / `cookie` / `token` / `私钥` / `密码` / `密钥` / `sk-` 等敏感关键词的内容（`tool_memory_sensitive_rejected`）；返回 `tool_memory_updated`；`isConcurrencySafe = true`，`getDisplayCategory = READ`，`getActionIcon = ICON_BOOK_OPEN`
- **记忆批量删除** - `LearningContextStore` 接口新增 `deleteMemories(List<String> ids)`；`MemorySettingsScreenView` 从 `ScreenScaffoldView` 重构为 `LinearLayout`，`Listener` 新增 `onMemoriesDeleted(List<String> ids)`；长按 / 点击多选按钮进入 `multiSelectedIds` 状态，顶部切换为 `ScreenHeaderView("Selected: N")` + 关闭 + `TRASH_2` 按钮；确认弹窗 `screen_memory_delete_selected_message` 批量删除；`setBackgroundColor(LineTheme.ACCENT_MUTED)` 高亮已选
- **记忆提取优化** - `MemoryExtractionService` 重构：构造函数改为 `MemoryExtractionService(ResourceProvider, LearningContextStore, ExtensionStore, PromptTemplateRepository)`，移除 `Context` 硬依赖；`MAX_MEMORIES` 由 5 改为 3；新增 `MIN_KEEP_CONFIDENCE = 0.78` / `RULE_CONFIDENCE = 0.88` / `MODEL_DEFAULT_CONFIDENCE = 0.82` 常量；新增 `hasDurableSignal(userInput, transcript)` 触发条件，关键词包括"记住/长期/始终/永远/禁止/必须/偏好/习惯/全局/这个项目/本项目"等 30+ 个中英文长效信号；未命中时跳过模型调用与规则抽取，直接走技能提取；`scope` 解析新增 `normalizeScopeOrEmpty` 区分"未知"与"user"，未知 scope 不再默认 user；`modelContent` 提取改用 `R.string.memory_extraction_json_only`
- **记忆提取调度迁移** - `GenerationFlowController.scheduleMemoryExtractionIfNeeded` 整段删除（含 `memoryExtractionService` 字段、构造器注入与 `linecode-memory-extract` 后台任务），记忆提取由 `MainCoordinator` 单独调度，控制器字段收敛

### MVP / 工具调用视图

- **修复后台切到前台时强制中断生成** - 新增 `cn.lineai.mvp.ActivityGenerationLifecyclePolicy` 策略类：`shouldStopGenerationOnStop(isFinishing)` 仅当 `Activity.isFinishing()` 为 true 时返回 true；`shouldStopGenerationOnStart()` 恒为 false；`MainActivity.onStop` / `onStart` 改为通过策略判断后调用 `presenter.resetGenerationState()`，避免 Home / 多任务切回杀掉正在进行的 LLM 流、auto-reject 待审工具、关闭 keep-alive；`onDestroy` 仍无条件 `presenter.destroy()`；`MainCoordinator.resetGenerationState` Javadoc 明确只在用户主动停止 / Activity finishing / destroy 三个场景调用
- **`AgentToolResultDisplay` 工具类** - 新增 `cn.lineai.ui.component.toolcall.AgentToolResultDisplay`（113 行），统一解析 `linecode_agent_progress` / `linecode_agent_pipeline_progress` / `linecode_agent_ref` 三种结构化 JSON；提供 `progressPayload / displayOutput / progressStatus / nestedToolCalls / toolCallCount / description / type / thinking` 8 个静态方法；`displayOutput` 在 JSON 包裹时优先返回 `output` 字段再回退 `model_content`，避免 Markdown 块直接渲染原始 JSON；非 JSON 文本中检测到 `"输出:\n"` 前缀则取其后正文
- **`ToolCallAgentView` 改用 `AgentToolResultDisplay`** - 删除 60+ 行手写 `progressPayload` 私有解析逻辑，全部改用 `AgentToolResultDisplay` 工具方法；`running` 状态判定新增 `outerReviewState == "running"`；`toolCount` 优先从 progress JSON 拿，其次解析 `工具调用:` 文本标签
- **`ToolCallGenericView` 同样接入** - 当结果内容是 `linecode_agent_*_progress` 结构时改走 `AgentToolResultDisplay.displayOutput` 取人类可读正文，避免 generic view 把 raw JSON 整段塞进 Markdown；空内容时回退 `tool_call_agent_failed` / `tool_call_agent_done` 提示
- **`ToolDisplayResolver` 内置 agent 分类** - `fallbackDisplayCategory` 命中 `"agent"` 返回 `ToolDisplayCategory.AGENT`；命中 `"agent_pipeline"` 返回 `ToolDisplayCategory.AGENT_PIPELINE`；`agentx_` 前缀继续返回 `AGENT`；`mcpx_` 仍为 `GENERIC`

### 数据层接口抽象（DIP）

- **新增 8 个解耦接口** - `cn.lineai.ai.PromptTemplateProvider`（11 行）、`cn.lineai.ai.SkillPromptProvider`（9 行）、`cn.lineai.resource.ResourceProvider`（13 行，`openAsset/getString`）、`cn.lineai.resource.SystemConfigProvider`（13 行，`isDarkModeEnabled/getSdkInt/getFilesDirPath`）、`cn.lineai.service.AccessibilityStateProvider`（9 行，`isAccessibilityEnabled`）、`cn.lineai.tool.ToolCategoryResolver`（9 行）、`cn.lineai.tool.ToolPromptRenderer`（9 行）、`cn.lineai.tool.PhoneControlService`（28 行）；仓库层不再依赖具体实现，可由测试桩或 DI 容器替换
- **仓库层解构 Android Context** - `KeepAliveRepository` / `UserAgreementRepository` / `SshConfigRepository` / `AiBehaviorSettingsRepository` / `ChatModeRepository` / `InputSettingsRepository` / `OutputSettingsRepository` / `ThemeSettingsRepository` / `ExtensionRepository` / `IpcProviderRepository` / `ProjectRepository` / `PromptTemplateRepository` / `WebSearchConfigRepository` / `SettingsRepository` / `ConversationRepository` / `DiffRepository` / `ModelRepository` 全部改为构造注入 `LineCodeDatabase` / `ResourceProvider` / `SystemConfigProvider` / `WorkspacePaths`；`MainDependencies` 一次性 `LineCodeDatabase.getInstance(appContext)` + 构造 `ContextResourceProvider` / `ContextSystemConfigProvider` 共享
- **`LearningContextStore` 拆分** - 接口新增 `deleteMemories(List<String> ids)`；业务编排 `buildLearningContext` / `getOverview` 抽到 `cn.lineai.service.LearningContextService`（51 行）；`MemoryPromptBuilder` 由 `cn.lineai.ai.prompt` 包移至 `cn.lineai.ai.prompt` 并接受 `WorkspacePaths` / `PromptTemplateRepository` 注入
- **`DiffStore` 接口扩展** - 新增 `markReverted(String diffId)` 方法；`DiffRepository` 实现迁移到 `cn.lineai.data.service.FileRestorer`（36 行，文件恢复），`DiffRepository.revertDiff` 不再直接写文件
- **`UrlPolicy` 实例化扩展** - 从 `final class` + 全部 `static` 方法改为可继承实例类（`public class UrlPolicy`），持有 `DEFAULT` 单例；`cleartextHosts` / `privateNetworkPredicates` 通过 `addCleartextHost(String)` / `addPrivateNetworkPredicate(Predicate<String>)` 注册；`registerDefaultCleartextHosts` 默认注册 localhost / 127.0.0.1 / 10.0.2.2 / ::1；`registerDefaultPrivateNetworkPredicates` 默认注册 192.168.* / 10.* / 172.16-31.*；`checkNormalizeHttpOrHttpsUrl` / `checkNormalizeHttpOrLocalCleartextUrl` / `checkRequireHttpOrLocalCleartextUrl` / `checkIsAllowedCleartextHttpUrl` 由静态方法改为实例方法（保留兼容调用）

### 模型自动重试

- **重试机制** - `GenerationFlowController` 引入 `MAX_RETRIES = 3` / `RETRY_DELAY_MS = 5000L`；新增私有 `retryableModelStream(generationId, model, cancellationToken, messages, usedToolCallCount, attempt, userInput)` 方法；`ModelCompletionException` 触发时进入 `handleModelError`：取消或已用满 3 次则 `failGeneration(generationId, id, host.formatModelFailed(error))`；否则从 `messages` 列表移除失败的 assistant 消息，插入 `ChatMessage.retryNotice(id, host.formatRetryNotice(attempt+1, MAX_RETRIES, error))` 通知气泡，主线程 `postDelayed(RETRY_DELAY_MS)` 后再次调用 `retryableModelStream(..., attempt+1, ...)`；后台任务名按 `attempt == 0` 区分 `linecode-model-stream` / `linecode-model-stream-retry`
- **Host 回调扩展** - `GenerationFlowController.Host` 新增 `String formatRetryNotice(int attempt, int maxRetries, String error)` / `String formatModelFailed(String error)`；`GenerationFlowHost` 实现走 `coordinator.context().getString(R.string.model_retry_attempt / model_retry_failed, ..., StringUtils.decodeUnicodeEscapes(error))`，错误文本先经 Unicode 转义解码
- **ChatMessage 新增重试通知类型** - `ChatMessage.retryNotice(id, content)` 工厂方法；`modelSwitchNotice` 文案从中文 "模型已从 ... 更改为 ..." 改为 "Model changed from ... to ..."；`ChatMessageListView` 通知视图创建方法 `createModelSwitchNotice` 改名为通用 `createNoticeView`，ViewType 常量 `VIEW_TYPE_MODEL_SWITCH` 改名为 `VIEW_TYPE_NOTICE`，同时支持模型切换与重试通知
- **三语资源** - `app/src/main/res/values/strings.xml` / `values-zh/strings.xml` / `values-ru/strings.xml` 同步新增 `model_retry_attempt` / `model_retry_failed` 两条

### 工具调用系统

- **导入导出数据库依赖注入** - `LineCodeArchiveService` / `LineCodeImportService` 构造注入 `LineCodeDatabase` 而非依赖 `Context`；`ArchiveSecretRedactor`（124 行）增补可注册敏感字段与命名关键字
- **StorageStatsRepository** - 36 行调整，统计口径从 `MessageTextChunkStore.totalLength` 聚合
- **`MemoryRanker` 适配** - BM25 排序器 22 行调整，配合 `LearningContextService` 业务编排分离
- **`SkillRepository` 业务编排扩展** - 123 行扩展，把 Skill 文件 IO 委托给 `SkillFileManager`，自身只保留数据库 CRUD
- **`ChatModeRepository.applyMode`** - 权限申请与模式应用解耦，新增 `applyPermissionForMode` 单独处理权限；`SettingsManagementController` 透传回调

### 测试

- 新增 `ActivityGenerationLifecyclePolicyTest`（22 行）覆盖 home / 任务切换 / 退出 / 回前台 4 种生命周期的生成终止策略
- 新增 `ToolDisplayResolverFallbackTest`（26 行）覆盖 `agent` / `agent_pipeline` / `agentx_*` 的 fallback 分类
- 新增 `AgentToolResultDisplayTest`（57 行）覆盖结构化 JSON 解析、`output` 字段优先级、未知 JSON 安全降级
- 扩展 `AgentOutputToolTest`（115 行）覆盖 agent_id 缺失、store 不可用、未知 id、运行中、含错误、空正文、超大输出截断、include=meta 等场景
- 扩展 `AgentResultRegistryTest`（149 行）覆盖 id 分配、状态变更、压缩 JSON 输出
- 扩展 `PipelineProgressSessionTest`（92 行）覆盖多线程并发下状态变更与最终 payload 构建
- 新增 `PipelineDependencyResolverTest`（72 行）覆盖 Pipeline 依赖图与并行层划分
- 扩展 `ToolBuiltinsTest`（65 行）覆盖英文文案下的全部内置工具描述、参数、动作名；接入 `FakeResourceContext` 解决单元测试无 Context 问题
- 扩展 `GenerationFlowControllerTest`（+11 行）覆盖 `MemoryExtractionService` 字段移除后的构造器签名变化
- 扩展 `ToolSettingsRepositoryTest`（+56 行）覆盖 `ToolCategoryResolver` / `WebSearchConfigRepository` / `PhoneControlRepository` 注入后的权限与分类解析

### 版本

- 版本号升级到 `1.2.3`
- `versionCode` 升级到 `26`

---

## v1.2.2

### 项目模块化拆分

- **10 个新 Gradle 模块** - 从单模块 `:app` 拆分为多模块架构，新增 `:core-model`（纯数据类 / DTO / 枚举）、`:core-api`（接口抽象）、`:core-security`（`UrlPolicy`、`SimpleHttpClient`）、`:ui-theme`（`LineTheme` 及共享 UI 基础设施）、`:markdown`（Markdown 渲染，commonmark + GFM tables）、`:data`（SQLite schema、仓库、导入导出归档、错误日志）、`:feature-tool`（`BaseTool`、`ToolRegistry`、全部内置工具）、`:feature-model`（`ModelProtocol`、`ModelProtocolFactory`、四种协议实现、`ContextManager`、`ContextCompactionService`、提示词模板）、`:feature-ssh`（`SshService`、`SshConnectionPool`、`SshCommandExecutor`、`TermuxHelper`）、`:feature-share`（导出 / 分享 / PDF）。`settings.gradle.kts` 新增 `includeBuild("build-logic")` composite build 共享约定插件。
- **代码迁移** - `:app` 模块中 `cn.lineai.model.*` → `:core-model`；`cn.lineai.tool.ToolInfo` / `ToolNames` / `ToolCategory` / `ToolDisplayCategory` / `ModelServiceProvider` → `:core-api`；`cn.lineai.security.*` → `:core-security`；`cn.lineai.ui.theme.*` → `:ui-theme`；`cn.lineai.ui.markdown.*` → `:markdown`；`cn.lineai.data.*` + `cn.lineai.log.*` + `cn.lineai.workspace.*` → `:data`；`cn.lineai.tool.*` → `:feature-tool`；`cn.lineai.ai.*` + `cn.lineai.context.*` → `:feature-model`；`cn.lineai.ssh.*` → `:feature-ssh`；`cn.lineai.share.*` → `:feature-share`。`app/src/main/assets/prompts/*.txt` 提示词模板 → `:feature-model`。
- **新增基类与工具** - `BaseRepository`（公共 Cursor 辅助方法）、`FileTreeBaseRepository`（文件树仓库公共基类）、`ExceptionUtils`（工具异常格式化）、`AbstractToolCallBuilder`（协议 tool_call 构建公共逻辑）、`DefaultModelServiceProvider`（默认 `ModelServiceProvider` 实现）、`ReasoningDeltaExtractor`（推理增量提取）。
- **循环依赖打破** - `:feature-tool` 不得引用 `cn.lineai.ai`；`:feature-model` 只引用 `:core-api` 中的 `ToolInfo` / `ToolNames` / `ToolCategory`；`ai ↔ tool` 循环依赖已消除。

### 上下文压缩动态化

- **50% 软触发** - `ContextCompactionService` 引入 `SOFT_COMPACT_TRIGGER_RATIO = 0.5`，上下文窗口占用超过 50% 时触发软压缩：最早 70% 可压缩消息被总结，最近 30%（`SOFT_COMPACT_TAIL_KEEP_RATIO`）原文保留。布局：`[head(excludeFromContext)] + [summary] + [tail(original)] + [preservedTail] + [progressDone]`。
- **80% 硬触发** - `shouldCompact` 在 80% 占用时执行全量压缩兜底。`ChatInteractionController` 在每次请求前检查软触发（未硬触发时）。
- **分段存储防 OOM** - `ContextCompactionService` 用 `List<String>` 逐段拼接转录文本，每段 ≤ `TRANSCRIPT_SEGMENT_MAX_CHARS = 256KB`，避免单个 `StringBuilder` OOM。移除固定大小截断（`MAX_MESSAGE_CONTENT_CHARS` / `MAX_TRANSCRIPT_CHARS`）。
- **大堆内存** - `AndroidManifest.xml` 设置 `android:largeHeap="true"` 支持大转录。
- **压缩控制器** - `ContextCompactionController` 新增 `startSoftContextCompaction` / `finishSoftContextCompaction` 驱动流程；`OutOfMemoryError` 在服务和控制器中均被捕获作为最后兜底。

### 模型上下文大小

- **`ContextSizeParser`** - 新增 `cn.lineai.model.ContextSizeParser`（`:core-model`），解析用户输入如 `128K`、`1m`、`128000` 为整数 token 数。
- **`ModelContextParser`** - 新增 `cn.lineai.model.ModelContextParser`（`:core-model`），兼容旧 `{id}[{size}]` 后缀格式，提供 `parse(ModelConfig)` / `apiModelId(ModelConfig)` 方法。协议统一调用 `apiModelId(ModelConfig)` 发送请求，剥离 `[size]` 后缀。
- **`ModelConfig.contextSize`** - `contextSize` 作为一等字段由 `ContextSizeParser` 解析；UI 使用纯文本字段输入；`model_configs` SQLite 表新增 `context_size` 列。

### 工具结果内容截断

- **`ToolResult.truncateContent()`** - 新增静态方法，50KB 上限截断：输出 ≤ 50KB 原样返回，> 50KB 保留前 25KB + 截断提示 + 后 25KB（中间截断，保留首尾）。
- **`FileReadTool` KB 参数** - 文件读取参数从行制（`start_line` / `end_line` / `offset` / `limit`）改为 KB 制（`start_kb` / `end_kb`），50KB 上限；超过 1MB 的文件拒绝读取并返回提示。
- **`ShellExecuteTool` 输出截断** - Shell 输出超过 50KB 时自动截断（首 25KB + 截断消息 + 尾 25KB）。
- **压缩期截断** - `ContextCompactionService` 构建转录时对消息内容、工具调用参数、推理内容执行截断，防止超大内容进入上下文。
- **安全校验** - `ToolMessageController` 在写入工具结果前检查内容大小。

### 图片选择与路径保护

- **图片选择按钮** - `ComposerView` 在附件 `+` 按钮右侧新增图片按钮（`IconButtonView.IMAGE`），点击打开系统图片选择器（`ACTION_OPEN_DOCUMENT`，`image/*`）。选中图片经压缩（长边 ≤ 1568px，JPEG q=85，>3.5MB 再降级）后 base64 编码，通过 `ChatInteractionController.sendMessageWithImage` → `ImageInputPayload.rawInputJson` 注入消息。协议自动检测 `ImageInputPayload.KIND` 并按供应商格式输出（OpenAI `image_url`、Anthropic `image.source.base64`、Codex `input_image`）。
- **绕过路径保护** - 安全设置页新增"绕过路径保护"开关（`OutputSettings.bypassPathProtection`，默认关闭）。开启后 `FileToolPathPolicy.resolve` 跳过工作区边界校验直接返回规范路径；`ToolContext.bypassPathProtection` 由 `ToolExecutor.injectDependencies` 传播；`AgentExecutionController.validateAgentWriteScope` 在绕过开启时跳过 `write_scope` 检查。开启时显示警告对话框需用户确认。所有文件类工具（`FileReadTool` / `FileWriteTool` / `FileEditTool` / `FileDeleteTool` / `GlobTool` / `ListDirectoryTool` / `ImageUnderstandingTool`）自动遵守此标志。

### Bing RSS 免费网页搜索

- **`BingRssSearchProvider`** - 新增 `cn.lineai.tool.builtin.search.BingRssSearchProvider`，利用 Bing RSS 搜索接口提供免费网页搜索能力（无需 API key）。解析 RSS/Atom XML 提取标题、摘要、URL。
- **搜索配置优化** - `WebSearchConfig` 新增 `bingRss` 提供商标识字段；`ToolSettingsScreenView` 搜索配置区域支持 Bing RSS 选项；`WebSearchService` 适配新 provider；`ToolSettingsRepository` 搜索配置逻辑重构。
- **测试** - 新增 `BingRssSearchProviderTest`（49 行）覆盖 RSS 解析。

### 工具调用参数清洗

- **`ToolArgsCleaner`** - 新增 `cn.lineai.tool.ToolArgsCleaner`（移至 `:feature-tool`），对模型返回的工具参数字符串做清洗：剥离 Markdown 代码围栏、NFKC 归一化并移除零宽字符、删除注释与控制字符、移除对象/数组尾部多余逗号、将简单单引号字符串转为双引号。合法 JSON 原样返回，空/空白返回 `{}`。
- **`ToolExecutor` 接入** - `ToolExecutor.execute` 在 `new JSONObject(...)` 之前先调用 `ToolArgsCleaner.clean(toolCall.getArguments())`，降低模型输出带围栏、注释、单引号或多余逗号时参数解析失败的概率。
- **测试** - 新增 `ToolArgsCleanerTest` 覆盖空输入、Markdown 围栏、注释、尾部逗号、单引号、控制字符等场景。

### 工具调用视图优化

- **Agent 类型展示** - `ToolCallAgentPipelineView` / `ToolCallAgentView` 优化 Agent 角色类型标签展示，区分编程型/探索型 Agent。
- **参数补全** - `ToolCallBlockView` 工具调用卡片参数补全逻辑优化。
- **状态显示修复** - 修复 `ToolCallReadView` / `ToolCallWriteView` 在某些状态下进度圈不消失的问题；`BaseToolCallView` 新增 `TerminalStatus` 枚举和 `computeTerminalStatus` 统一终态判断。

### 协议与模型增强

- **`ModelProtocol` 默认方法** - `ModelProtocol` 接口新增 `supportsNativeTools()` / `supportsContextCompaction()` / `supportsImageGeneration()` / `supportsImageUnderstanding()` 4 个 `default` 方法返回 `true`；各协议按需覆写。
- **`AbstractToolCallBuilder`** - 新增 `cn.lineai.ai.protocol.AbstractToolCallBuilder`，提取 `OpenAiCompatibleProtocol` / `AnthropicMessagesProtocol` / `CodexResponsesProtocol` 共用的 tool_call JSON 构建逻辑。
- **`ReasoningDeltaExtractor`** - 新增 `cn.lineai.ai.protocol.reasoning.ReasoningDeltaExtractor`，统一提取各协议流式推理增量内容。
- **`ToolCallTextParser` 增强** - 工具调用文本解析健壮性提升，解析失败返回空数组而非抛异常。
- **`ModelCatalogClient` 重构** - 模型目录客户端清理冗余逻辑。

### i18n 与资源

- **新增 30+ 条字符串** - 图片选择、路径保护、上下文大小、搜索配置、安全设置等相关文案同步添加到 `values/strings.xml` 与 `values-zh/strings.xml`，各子模块（`data`、`markdown`、`feature-ssh`）各自持有资源文件。
- **模块级资源隔离** - `:data`、`:markdown`、`:feature-ssh` 等模块拥有独立的 `strings.xml`，不再全部集中到 `:app`。

### 版本

- 版本号升级到 `1.2.2`
- `versionCode` 升级到 `25`

---

## v1.2.1

### 上下文压缩

- **跳过已生成的隐藏摘要** - `ContextCompactionService.selectCompactableMessages` 与 `ContextCompactionController` 三处过滤逻辑统一：消息为 `hidden` 且 `responseInputItemJson` 非空时视为已压缩摘要，不再参与下一次压缩；`compactStatus` 非空的压缩进度块同样跳过。修复历史摘要在多次压缩循环中被反复覆盖、导致早期上下文丢失的问题。
- **摘要必须进入上下文** - `ContextCompactionController.createCompactedMessages` 构造摘要消息时显式将 `excludeFromContext` 置为 `false`（避免链式 `withResponseInputItemJson` 基于旧副本意外保留 `true`），保证压缩后的摘要能被后续模型请求看到，防止模型侧出现"上下文被清空"的幻觉。
- **统一可压缩内容判断** - `hasCompactableContent` 与 `isCompactionNeeded` 路径均接入同一规则，避免不同入口对隐藏摘要/压缩块的处理不一致。

### 工具参数解析容错

- **`ToolArgsCleaner` 参数清洗** - 新增 `cn.lineai.tool.ToolArgsCleaner` 纯工具类，对模型返回的工具参数字符串做清洗：剥离 Markdown 代码围栏、NFKC 归一化并移除零宽字符、删除注释与控制字符、移除对象/数组尾部多余逗号、将简单单引号字符串转为双引号。合法 JSON 原样返回，空/空白返回 `{}`。
- **`ToolExecutor` 接入清洗** - `ToolExecutor.execute` 在 `new JSONObject(...)` 之前先调用 `ToolArgsCleaner.clean(toolCall.getArguments())`，降低模型输出带围栏、注释、单引号或多余逗号时参数解析失败的概率。

### 工具结果进度状态

- **`GenerationFlowController` 不回退保护** - `publishToolResultProgress` 新增 `resolveProgressReviewState` 从进度 JSON 的 `status` 字段解析状态；若该工具已有完成/失败等终态结果，异步进度不再把它拉回 `running`，修复进度圈在某些异步进度发布后永不消失的问题。
- **`ToolMessageController.currentReviewState`** - 新增按 `toolCallId` 查询当前 `reviewState` 的方法，供进度发布时判断是否为终态。
- **Agent Pipeline 进度发布** - `AgentExecutionController.runAgentPipelineTool` 构造 `PipelineProgressSession` 时注入 `ProgressPublisher`，子 Agent 进度变化时直接调用 `host.addOrReplaceToolResult` + `host.render`，让流水线卡片状态与底层结果同步。
- **流水线完成判定修复** - `ToolCallAgentPipelineView.bind` 完成判定优先以进度 JSON 的 `status == "done"` 为准（不再单纯依赖 `reviewState` 时序），异常时仍按 `error` 处理，解决流水线已完成但进度圈未消失的问题。
- **会话恢复对 running/pending/accepted 空结果统一收敛** - `ConversationResumeSanitizer.isUnfinishedReviewState` 把 `accepted` 且内容为空也视为未完成；嵌套 Agent / Pipeline 进度里的子工具调用若处于 `running` 状态，同样被收敛为"上次生成已中断"的错误结果，避免恢复后仍显示运行中。

### 移除 HTTP 服务器工具

- **删除 `HttpServerTool`** - 移除内置工具 `http_server` 及其 `SimpleFileServer` 实现，包括 `MainCoordinator.onDestroy` 中的 `HttpServerTool.stopActiveServer()` 调用。
- **清理注册与分类** - `BuiltInToolProviders`、`ToolSettingsRepository`、`ToolCategory`、`ToolDisplayResolver`、`ToolDisplayCategory`、`MainDependencies`、`AgentExecutionController.isAgentToolAllowed` 中全部移除 `HttpServerTool` 相关引用与 `HTTP` 显示分类；删除 `HttpToolCallViewFactory`。
- **提示词与文案同步** - `ToolPromptRenderer`、执行模式描述字符串、`README.md` / `README_CN.md` / `CLAUDE.md` / `simple.md` 中不再把 HTTP 服务器列为本地/SSH 模式差异点；移除 `tool_call_block_http` 字符串。

### 手机控制工具远程可用

- **`ToolSettingsRepository` 分组模式调整** - `phone_control` 工具组由 `MODE_LOCAL` 改为 `MODE_ALL`，SSH Shell / 终端提供者模式下也可使用手机控制工具。
- **提示词补充说明** - `ToolPromptRenderer` 在 SSH / 终端提供者工具提示末尾追加"当会话模式为 Control 时，手机控制工具仍然可用"。

### UI 间距

- **消息默认 padding 恢复** - `UserMessageView` / `AssistantMessageView` 在构造时保存默认 padding，新增 `restoreDefaultPadding()`。
- **多选样式不再强制清零** - `ChatMessageListView.MessageAdapter.applyMultiSelectStyle` 在非多选/未选中状态下调用 `restoreMessagePadding` 恢复默认边距，仅选中消息保留 4dp 描边内边距；修复非多选模式下对话消息贴到屏幕边缘的问题。

### 测试

- 新增 `ToolArgsCleanerTest`（124 行）覆盖空输入、Markdown 围栏、单行/多行注释、字符串内注释保留、对象/数组尾部逗号、单引号对象、控制字符、合法 JSON 不变、嵌套尾部逗号等场景。
- 扩展 `ConversationResumeSanitizerTest`（+121 行）覆盖 `running` 非空内容、`pending` 空内容、`accepted` 空内容、嵌套 Agent running 工具调用、嵌套 Pipeline running 工具调用五种恢复收敛场景。
- 更新 `ToolSettingsRepositoryTest` / `AgentExecutionControllerTest` 断言，适配 HTTP 服务器移除与提示词调整。

### 版本

- 版本号升级到 `1.2.1`
- `versionCode` 升级到 `24`

---

## v1.2.0

### 切后台稳定性

- **生命周期钩子补全** - `MainUiController` 新增 `onEnterBackground()` 回调；`MainActivity.onPause` 在 `super.onPause()` 之前调用 `presenter.onEnterBackground()`，经 `MainCoordinatorDelegates` 转发到 `delegateView().hideOverlays()`，在应用切到后台时统一收起所有浮层。
- **斜杠命令弹窗后台清理** - `ComposerView.dismissSlashPopup()` 由 `private` 提升为 `public`，并在 `MainChatView.hideOverlays()` 中调用；修复 1.1.8 引入的 `SlashCommandPopup` 在输入框含 `/` 文本时切后台不 dismiss，导致 Window Token 失效回前台后整屏黑屏的问题。

### 错误信息 UTF 解码

- **`StringUtils.decodeUnicodeEscapes`** - 新增 `cn.lineai.util.StringUtils.decodeUnicodeEscapes`，把 Java 风格 `\uXXXX` 转义解码为对应字符；仅解码严格匹配 `\u` 后接 4 位十六进制（0-9a-fA-F）的片段，不完整的 `\u` 与 Windows 路径（`\Users`）等原样保留，避免误伤正常内容。
- **聊天内错误文本解码** - `GenerationFlowController.failGeneration` 在错误文本进入 `ChatMessage`、渲染到聊天前统一 `decodeUnicodeEscapes`，覆盖模型通信失败、流式输出失败、HTTP 404/500 等所有聊天内错误展示；`describeException` 对异常 message 同样解码，覆盖工具执行等错误文本。
- **SSE 流式错误乱码修复** - `OpenAiCompatibleProtocol` 新增 `describeError(Object)`，当 SSE 返回 `error` 为 JSON 对象时优先读取 `message` / `type` / `code` 字段（不再触发 `JSONObject.toString()` 把中文转义成 `\uXXXX`），并对结果兜底解码，修复流式错误在聊天里显示为一串 `\u` 转义符的问题。

### 日志脱敏 OOM 安全

- **超大负载截断脱敏** - `ErrorLogRedactor` 新增 `MAX_SAFE_LENGTH = 1MB` 上限，输入超过该长度时先取前缀脱敏、剩余以 `\n... [REDACTED_TRUNCATED]` 标注丢弃；避免模型协议层在记录含大段 base64 的错误响应时对几十 MB 字符串反复 `replaceAll` 复制导致 `OutOfMemoryError`（峰值内存由数十 MB 降为恒定 ~2MB，峰值内存不再随日志体膨胀）。

### 多选消息交互补全

- **列表项点击勾选** - `ChatMessageListView` 的 `ListView` 新增 `OnItemClickListener`，多选模式下点击消息即切换该条目的选中态（调用 `toggleSelection`），无需仅依赖操作条按钮。
- **选中态高亮描边** - `MessageAdapter.applyMultiSelectStyle` 在多选模式下为已选中消息绘制 `LineTheme.ACCENT` 圆角描边（`roundedStroke` 12dp）+ 4dp 内边距；`multiSelectMode` / `selectedMessageIds` 下沉到 adapter，随 `enterMultiSelectMode` / `exitMultiSelectMode` / `toggleSelection` 同步，逐条 `bind` 时应用样式。
- **底部操作条避让** - 进入多选模式时 `listView` 底部追加 `MULTI_SELECT_BAR_EXTRA_PADDING`（64dp）内边距，退出时还原，避免底部多选操作条遮挡最后一条消息。

### 测试

- **`ErrorLogRedactorTest.redactsLargePayloadWithoutOom`** - 验证 200 万字符超大输入不再 OOM，且头部 secret（Authorization）与长 base64 仍被脱敏、并标记 `[REDACTED_TRUNCATED]`。
- **`StringUtilsTest`** - 新增 `cn.lineai.util.StringUtilsTest`，覆盖基础 `\u` 解码、纯文本不变、不完整转义保留、Windows 路径不误解码、`null`/短输入、混合内容共 6 例。

---

## v1.1.9

### 消息引用与多选分享

- **`QuoteController` 纯逻辑控制器** - 新增 `cn.lineai.mvp.QuoteController`，持有引用文本与预览接口 `QuotePreview`（`showQuote` / `hideQuote`），提供 `setQuote` / `clearQuote` / `hasQuote` / `composeWithQuote`；`composeWithQuote` 自动把引用拼成 `"> ..."` 块再插入用户输入并清空，发送时一次完成。`setQuote` 文本超过 80 字符自动截断为预览。
- **`ComposerView` 引用预览条** - `ComposerView` 实现 `QuotePreview`，在输入框上方构建圆角描边的引用预览条：左侧 3dp 宽 `LineTheme.ACCENT` 竖线、中间预览文本 2 行 `END` 省略、右侧 28dp `IconButtonView.CLOSE` 关闭按钮；通过 `QuoteDismissListener` 在关闭时通知 `QuoteController.clearQuote`。
- **引用文本解析与渲染** - `UserMessageView` 在 `bind` 时解析 `"> quoted\n> lines\n\nactual"` 格式，把引用部分抽到 `quoteTextView` 单独渲染（最多 3 行省略），主消息文本只保留后半段；空引用时自动隐藏引用条，避免在历史消息上误显示引用块。
- **多选分享模式** - `ChatMessageListView` 新增 `multiSelectMode` / `selectedMessageIds` / `MultiSelectListener`；`buildMultiSelectBar` 在底部构建高 8dp 浮起的操作条（选中计数 + 导出按钮 + 关闭按钮）；`MessageActionBarView` 新增 `MultiSelectButton`（`IconButtonView.CHECK_SQUARE`）入口；`enterMultiSelectMode` / `exitMultiSelectMode` 切换模式并清空选择。
- **多选消息转 `ChatMessage` 列表** - `ChatMessageListView.getSelectedMessages` 通过 `adapter.getVisibleMessages` 过滤选中 id 输出完整消息列表，供 `ShareController.showFormatPicker` 复用导出流程。
- **多选发送队列** - `ComposerView` 在流式生成期间允许输入；发送按钮三态：流式 + 有内容 → 橙色 `IconButtonView.ARROW_UP` 追加排队、流式 + 无内容 + 有队列 → 橙色停止、流式 + 无内容 + 无队列 → 红色停止；`render` 监听 `wasStreamingBefore && !streaming` 自动 `post(sendPending)`，把队列消息在 AI 停下的瞬间继续发送。
- **`MessageActionBarView` 拆分接口** - `Listener` 重构为 `ActionListener`（`onCopy` / `onQuote` / `onShare`）+ `SelectListener`（`onSelect` / `onMultiSelect`）+ `RecallListener`（`onRecall`）三组接口；新增 `IconButtonView.SHARE` / `QUOTE` / `TEXT_CURSOR` / `CHECK_SQUARE` 4 个图标常量与对应 `ic_lucide_share_2` / `ic_lucide_quote` / `ic_lucide_text_cursor` / `ic_lucide_check_square` 矢量图。
- **`MessageActionListener` 接口扩展** - 新增 `onQuoteMessage` / `onShareMessage` / `onSelectText` / `onMultiSelectToggle` 四个回调；`AssistantMessageView` / `UserMessageView` 同步绑定到 `setActionListener` + `setSelectListener` + `setRecallListener`，`streaming` 构造参数时 `setActionsVisible(false)` 隐藏引用/分享/选中/多选按钮。

### 多格式聊天记录导出

- **`ExportFormat` / `ExportResult` 抽象** - 新增 `cn.lineai.share.ExportFormat` 接口（`displayName` / `execute`）与 `cn.lineai.share.ExportResult` 不可变结果类，支持 `ACTION_SHARE_FILE`（带 `File` + `mimeType` + `Uri`）/ `ACTION_CLIPBOARD`（带 `content`）/ `ACTION_SHARE_TEXT`（带 `content`）三类动作。
- **`ExportFormatResolver` 解析器** - 默认注册 5 种格式：`ClipboardFormat` / `PlainTextFormat` / `MarkdownFormat` / `PdfFormat` / `ChatImageFormat`；`getDisplayNames` / `get` / `size` 供 `ShareController.showFormatPicker` 弹窗消费，`register` 允许扩展。
- **`PlainTextFormat` / `MarkdownFormat`** - 纯文本格式用 `【我】` / `【AI】` 标签分段、末尾追加 `FOOTER_PLAIN = "—— 来自 LineCode Pro"`；Markdown 格式用 `##` 二级标题 + `---` 分隔 + `FOOTER_MD = "*—— 来自 LineCode Pro*"`。`ChatMessages.toPlainText` / `toMarkdown` 静态方法支持单元测试直接构造。
- **`ChatImageFormat` + `ChatBitmapRenderer`** - 用 `ChatLayout.measure` 阶段按 28sp 字体 + `IMG_WIDTH=720` 测每行宽度算出总高，再用 `ChatCanvas.draw` 在 `0xFF1E1E2E` 背景上按"我"/"AI"角色绘制深浅气泡（`0xFF3B3B5C` / `0xFF2A2A3E` 圆角 24）+ LTGRAY 角色名；最终 `Bitmap.compress(PNG, 100)` 写入 `cache/share/chat_screenshot.png`。
- **`PdfFormat` + `PdfRenderer`** - 自实现 A4（595×842pt）PDF 渲染：标题 + 正文双 `TextPaint`，每行按 `measureText` 自适应宽度自动换行，遇 `\n` 强制断行，遇页底 `finishPage` + `startNewPage`；最终由 `PdfExporter.save` 写入 `cache/share/`，再通过 `ShareFileProvider.uriFor` 转 `content://` URI。
- **`ChatMessages.wrapText` 通用换行** - 用 `Paint.measureText` 在指定 `maxWidth` 内逐字符测量，超过则回退到上一换行点；保留原始 `\n`，跳过段落间尾随空白，避免切到字符中间。
- **`ShareController` 调度中心** - 构造注入 `ExportFormatResolver`；`showFormatPicker` 弹 `AlertDialog` 列出 `displayNames`，选择后调用 `format.execute` 并按 `ExportResult.action` 派发到 `ShareHelper.shareFile` / `ShareHelper.copy` / `ShareHelper.shareText`；`ClipboardFormat` 在文本 > 5000 字时 `shouldWarn` 提示"内容较长，仅部分可能被复制"。

### 分享体验优化

- **合并转发多选 + 格式选择** - `MainChatView.showMultiSelectDialog` 弹 `setMultiChoiceItems` 多选列表（每行 `[角色] 前 50 字`），预选中长按触发的 `position`；`showMergeFormatDialog` 提供 5 种导出：对话截图 / PDF / Markdown / 纯文本分享 / 复制到剪贴板，分别走 `shareAsChatImage` / `shareAsPdfWithName` / `shareAsFile` / `Intent.ACTION_SEND` / `ClipboardManager.setPrimaryClip`。
- **AI 取名文件名** - `askFileNameAndShare` 弹自定义圆角卡片布局（深色卡片 + 提示文字 + 输入框 + "AI 取名"胶囊按钮），AI 取名按钮调用 `generateSmartFileName`：从首段有效内容（去 `^[#>*\-\s]+` 头，长度 ≥ 4 截到 20 字）抽取文件名，缺省回退到 `chat_<时间戳末 4 位>`；最后用 `name.replaceAll("[/\\\\:*?\"<>|]", "_")` 清洗非法字符再拼上后缀。
- **PDF / 对话截图生成器（`MainChatView` 内部版）** - 独立的 `shareAsPdfInternal` 用 `PdfDocument` + 双 `Paint` 手动绘制 A4 页面，分页阈值 `pageHeight - margin`；`shareAsChatImage` 用 `wrapText` + `Paint.measureText` 二次测量得到真实换行结果，再画左右气泡（用户右侧 `0xFF3B5998`、AI 左侧 `0xFF2D2D44`）+ 深色背景 + 居中页脚 `—— 来自 LineCode Pro`。
- **`ShareFileProvider`（`cn.lineai.log`）** - 全新自定义 `ContentProvider`（authority `<applicationId>.fileprovider`）替代 `ShareFileProvider`（`cn.lineai.share`），提供 `getType`（按扩展名返回 `application/octet-stream` / `application/pdf` / `image/png` / `text/plain`）、`openFile`（只读 + 规范化路径防穿越 + 文件存在校验）、`query`（返回 `OpenableColumns.DISPLAY_NAME` + `SIZE` 的 `MatrixCursor`，QQ/微信读取文件前会先 `query`）、`insert` / `update` / `delete` 全部禁用。`uriFor` 用 `new Uri.Builder().scheme("content").authority(...).appendPath(name)` 拼装 URI。
- **悬浮窗"再发"按钮** - `launchShareIntent` 启动分享 chooser 后延迟 6 秒 `postDelayed(showFloatingShareButton)`，使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 弹出 3 键悬浮条（"再发"紫色 + "返回"灰色 + "关闭"）；无悬浮窗权限时弹 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 引导页；`onWindowFocusChanged(hasWindowFocus=true)` 时自动清理悬浮窗与 `pendingShareFile`。
- **`MainChatView` 长按多选入口** - `messageListView.setOnItemLongClickListener` 触发 `MultiSelectListener.onMultiSelectTriggered(position)`，弹 `showMultiSelectDialog(position)` 时预选该位置。
- **长按文字选中** - `MainChatView.triggerTextSelection` 用 `EditText.setKeyListener(null) + setTextIsSelectable(true) + selectAll()` 构造只读可复制的对话框，规避 `setTextIsSelectable` 在聊天项内拦截兄弟按钮触摸事件的问题（同步在 `UserMessageView` / `MarkdownTextBlockView` 注释中明示）。
- **复制逻辑统一** - `MainChatView.copyMessage` 改用 `ShareHelper.copy`，与导出格式共享同一份 `ClipboardManager.setPrimaryClip("chat", text)` 实现。

### 安全设置与 HTTP 限制旁路

- **独立 `SecuritySettingsScreenView` 页面** - 新增 `cn.lineai.ui.component.SecuritySettingsScreenView extends ScreenScaffoldView`，拆出"HTTPS 与 HTTP 限制"+"浏览器 JavaScript"两个分区；`ScreenFactories.SecuritySettingsScreenFactory` 注册 `screenId = "security"`，由 `SettingsScreenView` 改为点击跳转到该新页面。
- **`UrlPolicy` 放宽模式** - 新增 `volatile relaxedHttpEnabled` 静态开关 + `setRelaxedHttpEnabled` / `isRelaxedHttpEnabled` 访问器；`normalizeHttpOrHttpsUrl` / `normalizeBrowserUrl` / `requireHttpOrLocalCleartextUrl` / `isHttpOrLocalCleartext` 四处在原 `isAllowedCleartextHttpHost` 校验之前先判断 `relaxedHttpEnabled`，开启时任意 `http://` 与 `https://` 一律放行。
- **`network_security_config.xml` 调整** - `base-config` 由 `cleartextTrafficPermitted="false"` 改为 `"true"`（让任意 http 请求至少能进到应用层），通过注释明确"cleartext 控制交由 `cn.lineai.security.UrlPolicy` 而非此处"，避免误以为系统层关闭。
- **`OutputSettings` 持久化字段** - 新增 `allowAnyHttp` 字段（`isAllowAnyHttp` / `setAllowAnyHttp`），由 `OutputSettingsRepository` 读写；`SettingsManagementController` 透传 `onAllowAnyHttpChanged` 回调到 `OutputSettingsController`，最终调用 `UrlPolicy.setRelaxedHttpEnabled`。
- **`minSdk` 升级到 26** - `app/build.gradle.kts.defaultConfig.minSdk` 从 24 提升到 26，与 `OutputSettings` 持久化、安全设置页、`TYPE_APPLICATION_OVERLAY` 等新 API 对齐。

### 数据库备份与恢复

- **`LineCodeDatabaseBackup` 自动备份** - 新增 `cn.lineai.data.db.LineCodeDatabaseBackup`，单线程 `ExecutorService` 串行化备份任务，目录 `context.getDir("backups", MODE_PRIVATE)/linecode/`，文件 `backup-<timestamp>.json` + `latest.json`（固定文件名），最多保留 `MAX_BACKUPS = 3` 份（`pruneOldBackups` 按时间戳删旧）。`saveAsync` 失败吞异常，绝不抛到主流程。
- **`LineCodeDatabaseArchive.exportFullSnapshot`** - 与现有 `exportSnapshot`（脱敏空 messages 正文）并列新增"完整导出"方法，遍历 `TABLES` 时对 `TABLE_MESSAGE_TEXT_CHUNKS` 表调用专用 `exportMessageTextChunksTable`，其他表复用 `exportTable`，输出 JSON 携带 `"full": true` 标记；`exportSnapshot` 与 `exportFullSnapshot` 共享 `LineCodeArchiveCodec.ENTRY_DATABASE` 文件名。
- **`LineCodeDatabaseErrorHandler` 自定义损坏处理** - 实现 `DatabaseErrorHandler` 替换 `DefaultDatabaseErrorHandler`：`onCorruption` 走"复制为 `.corrupted-<timestamp>` 存档 → 写入 `.needs-restore` 标记 → 删除损坏库文件"三步，全部 `try/catch Throwable` 吞异常，避免向上抛出二次崩溃。
- **`needs-restore` 自动恢复** - `LineCodeDatabase.onOpen` 新增 `maybeRestoreFromBackup`：`LineCodeSchema.DATABASE_NAME + ".needs-restore"` 标志存在时调用 `LineCodeDatabaseBackup.restoreLatest(this)` 把 `latest.json` 数据导入当前空库（`LineCodeDatabaseArchive` 反序列化），恢复成功/失败均删除标志，保留空库兜底。`integrityOk` 用 `PRAGMA integrity_check(1)` 启动期自检；`backupAsync` 公开方法供 `ConversationRepository` 在事务 `setTransactionSuccessful` 之后异步触发一次。
- **`ConversationRepository` 事务后自动备份** - `appendMessages` 在 `db.endTransaction()` 后调用 `database.backupAsync()`，保证每次成功落库后 6 个提交窗口内都有可用备份，应对系统杀死进程导致的库损坏。

### 工具调用系统重构

- **`ToolDisplayCategory` 枚举** - 新增 `cn.lineai.tool.ToolDisplayCategory`（`READ` / `WRITE` / `DELETE` / `SHELL` / `AGENT` / `AGENT_PIPELINE` / `TODO` / `IMAGE_GENERATION` / `PHONE_CONTROL` / `HTTP` / `GENERIC`），把工具显示路由从 UI 字符串判断改为枚举。
- **`ToolDisplayResolver` 解析器** - 新增 `cn.lineai.tool.ToolDisplayResolver` 取代 `ToolCallUtils` 的字符串散落判断，方法：`getDisplayCategory(name)`（优先用 `ToolRegistry.getCachedDisplayCategory`，缺省走 `fallbackDisplayCategory` 覆盖 `file_read` / `glob` / `list_dir` / `web_search` / `web_fetch` / `image_understanding` → `READ`、`file_write` / `file_edit` → `WRITE`、`file_delete` → `DELETE`、`http_server` → `HTTP`、`shell_execute` → `SHELL`、`agent` / `agentx_` → `AGENT`、`agent_pipeline` → `AGENT_PIPELINE`、`todo_update` → `TODO`、`image_generation` → `IMAGE_GENERATION`、`phone_*` → `PHONE_CONTROL`、`mcpx_` → `GENERIC`）、`getDisplayLabel` / `getActionName` 直接从 `BaseTool` 拿。静态 `setDefault` / `getDefault` 提供全局默认实例。
- **`ToolCallCardView` 统一接口** - 新增 `cn.lineai.ui.component.toolcall.ToolCallCardView` 接口（`setToolReviewListener` / `setProjectPath` / `bind(ToolCall, ToolResult)`），让 `ToolCallBlockView` 不再依赖具体子类型（之前对 `ToolCallWriteView` / `ToolCallDeleteView` / `ToolCallReadView` / `ToolCallShellView` / `ToolCallTodoView` / `ToolCallAgentView` / `ToolCallAgentPipelineView` 一连串 `instanceof` 判断）。
- **`ToolCallViewFactory` + `ToolCallViewFactoryRegistry`** - 新增 `cn.lineai.ui.component.toolcall.ToolCallViewFactory`（`createView(Context) -> ToolCallCardView`），内置 `ReadToolCallViewFactory` / `WriteToolCallViewFactory` / `DeleteToolCallViewFactory` / `ShellToolCallViewFactory` / `TodoToolCallViewFactory` / `AgentToolCallViewFactory` / `AgentPipelineToolCallViewFactory` / `HttpToolCallViewFactory` / `ImageGenerationToolCallViewFactory` / `PhoneControlToolCallViewFactory` / `GenericToolCallViewFactory` 11 个实现。`ToolCallViewFactoryRegistry` 按 `ToolDisplayCategory` 注册工厂，`createView(Context, category)` 返回对应 `ToolCallCardView`。
- **`ToolCallBlockView` 简化渲染** - 把 100+ 行 `if/else instanceof` 链替换为 `REGISTRY.createView(ctx, ToolCallUtils.getDisplayCategory(name))` + `childView.setToolReviewListener` + `childView.setProjectPath` + `addView(childView)` + `childView.bind` 5 行；`setToolReviewListener` / `setProjectPath` 也改用统一的 `ToolCallCardView` 接口。
- **`NestedToolCallParser` 嵌套工具调用解析** - 新增 `cn.lineai.tool.NestedToolCallParser.parse(JSONArray) -> List<NestedCall>`，把 Agent / Pipeline progress 数组中 `{"id", "name", "arguments", "result": {...}}` 解析为 `NestedCall(call, result)`，`ToolCallAgentView` / `ToolCallAgentPipelineView` 不再内嵌 JSON 解析逻辑。

### UI / 数据层解耦

- **UI 模型类独立包** - 新增 `cn.lineai.model` 下 6 个 UI 层不可变数据类：`ConversationUiModel` / `DiffUiModel` / `ExtensionItemUiModel` / `ExtensionKindUiModel` / `KeepAliveSettings` / `StorageStatsUiModel`，把 `Conversation` / `DiffRecord` / `ExtensionItem` / `ExtensionKind` / `KeepAliveSettings`（数据层原定义）等实体映射为 UI 专用模型，View 层只接收 UI 模型。
- **`ExtensionActionCallback` 回调接口** - 新增 `cn.lineai.mvp.ExtensionActionCallback`（`onSave` / `onDelete` / `onTest` 等），把扩展详情页的操作从 View 直接调用 Repository 改为回调到 Coordinator，View 不再依赖 Repository。
- **`DiffLoader` 接口** - 新增 `cn.lineai.ui.component.toolcall.DiffLoader`（`loadDiff(diffId) -> DiffRecord`），由 Controller/Assembler 实现，注入到 `ToolCallWriteView`，View 层不再直接持有 `DiffRepository`。
- **`CardViewHelper` 圆角卡片工具** - 新增 `cn.lineai.ui.component.CardViewHelper`，统一封装"圆角背景 + padding + 标题 + 描述"组合，被多个 Screen 复用，删除 `ExtensionDetailScreenView` 等页面内重复的卡片段。
- **`StringUtils` 字符串工具** - 新增 `cn.lineai.util.StringUtils`，提供空值 / 截断 / 路径安全 / 字符计数等纯函数，被多个 Repository 复用。
- **`SkillFrontmatterParser` + `SkillFileManager` + `SkillPromptBuilder`** - 把 `SkillRepository` 中"读 frontmatter + 文件管理 + prompt 拼装"三段混在一起的代码拆为 3 个独立类：`SkillFrontmatterParser` 解析 YAML frontmatter（`SkillPromptBuilder` 装配系统提示词、`SkillFileManager` 负责文件 IO + 缓存）。`SkillRepository` 内部行数从 665 减少到 ~50，只剩数据库 CRUD。
- **`ModelRepository` SQLite 迁移** - 新增 `cn.lineai.data.repository.ModelRepository implements ModelStore`：`ensureModelConfigColumns` 自检并补齐 `model_configs` 表列；`migrateLegacyPreferencesIfNeeded` 检测 `linecode_models` SharedPreferences 中的 `sqlite_migrated` 标记，未迁移时把 `KEY_MODELS` JSON 数组和 `KEY_SELECTED_ID` 一次性导入 SQLite；之后 `getModels` / `getModel` / `save` / `deleteModels` / `setSelectedModelId` / `getSelectedModel` / `clearAll` 全部走 SQLite（`ORDER BY selected DESC, updated_at DESC`）。
- **`ToolSettingsScreenView` 默认模式硬编码修复** - 之前默认执行模式硬编码为 `null`，改为从 `ToolSettingsRepository.getDefaultExecutionMode()` 读取，避免初次安装后界面显示空白。
- **`ComposerView` 等视图成员变量可修改** - `ComposerView` / `DrawerView` / `KeepAliveSettingsScreenView` 等视图的成员变量从 `final` 改为非 `final`，便于子类扩展与运行时切换。

### Agent 能力增强

- **`ModelProtocol` 默认方法** - `ModelProtocol` 接口新增 `supportsNativeTools()` / `supportsContextCompaction()` / `supportsImageGeneration()` / `supportsImageUnderstanding()` 4 个 `default` 方法返回 `true`；`AnthropicMessagesProtocol` / `CodexResponsesProtocol` / `LocalGgufProtocol` / `OpenAiCompatibleProtocol` 按需覆写其中部分为 `false`，调用方代码从 `if (protocol instanceof ...)` 改为 `if (!protocol.supportsXxx())`。
- **`ReasoningRequestStrategy` 推理策略** - 新增 `cn.lineai.ai.protocol.ReasoningRequestStrategy` 接口（`matches(baseUrl, modelId)` + `apply(JSONObject body, ReasoningRequestContext context)`）与 `ReasoningStrategyRegistry`（按顺序注册、按 `matches` 找到第一个命中的策略）。`OpenAiCompatibleProtocol` 把原来硬编码的 `if (url.contains("deepseek"))` 等分支改为遍历 registry；新增 `DefaultReasoningStrategy`（`matches` 始终返回 `true` 兜底）/ `DeepseekReasoningStrategy` / `DashscopeReasoningStrategy` / `MoonshotReasoningStrategy` / `MinimaxReasoningStrategy` 5 个实现。
- **多厂商推理支持** - 实现了 `DeepseekReasoningStrategy`（`baseUrl` 命中 `deepseek` 时在请求体加 `reasoning_effort` 字段）、`DashscopeReasoningStrategy`（`baseUrl` 命中 `dashscope.aliyuncs` 时加 `enable_thinking` + `thinking_budget`）、`MoonshotReasoningStrategy`（`baseUrl` 命中 `moonshot` 时加 `reasoning_content` 字段）等。`ReasoningRequestContext` 携带 `enabled` / `effort` / `preserveReasoning` / `baseUrl` / `modelId` / `thinkingBudget` 全部上下文，避免在每个策略里重新拼装。
- **Web 搜索多提供商** - 新增 `cn.lineai.tool.builtin.WebSearchProvider` 接口（`providerId` + `buildRequest` + `normalizeResults`）+ `WebSearchProviderRegistry`（注册 + 按 `providerId` 取出）；实现 `BingSearchProvider` / `BraveSearchProvider` / `SerpApiSearchProvider` / `TavilySearchProvider` / `DefaultSearchProvider` / `QueryCountSearchProvider` 6 个。`WebSearchService` 原本 160+ 行 `if (provider.equals(...))` 重构为注册表查找 + 通用 `HttpRequest` 调度；`SearchRequest` 与 `SearchResultItem` 数据类明确请求/响应契约。
- **扩展管理策略模式** - 新增 `cn.lineai.mvp.ExtensionKindDescriptor` 抽象接口 + `ExtensionKindRegistry` 注册表 + `AgentKindDescriptor` / `McpKindDescriptor` / `LinecodeKindDescriptor` / `SkillsKindDescriptor` 4 个实现。`ExtensionManagementController` / `ExtensionDetailScreenView` 把 `if (kind == AGENT) ... else if (kind == MCP) ...` 链条改为遍历注册表 + `descriptor.canHandle(item)` 分发，新增扩展类型只需注册 descriptor，不再动业务代码。
- **Agent 角色提示词模板** - `assets/prompts/` 新增 5 个 Agent 模板：`agent-role-coding-local.txt` / `agent-role-coding-remote.txt`（编程型 Agent 区分本地/远端 shell 模式）、`agent-role-explore-local.txt` / `agent-role-explore-remote.txt`（探索型 Agent 同样区分）、`agent-system-prompt-template.txt`（系统提示词主模板，含 `{{ROLE_PROMPT}}` / `{{TASK_DESCRIPTION}}` / `{{WORKSPACE_CONTEXT}}` / `{{SCOPE_CONTEXT}}` / `{{EXTENSIONS_CONTEXT}}` / `{{TOOLS_CONTEXT}}` 占位符）。`PromptTemplateRepository` 新增 `getTemplateText(id)` 读取方法。
- **流式渲染独立控制器** - 新增 `cn.lineai.mvp.StreamingRenderController` 把流式渲染的缓冲 / 调度 / 状态机从 `GenerationFlowController` 拆出来，便于针对 streaming 路径做单元测试与扩展。

### 工具系统增强

- **`BaseTool.NAME` 常量** - 25 个内置工具（`FileReadTool` / `FileWriteTool` / `FileEditTool` / `FileDeleteTool` / `GlobTool` / `ListDirectoryTool` / `HttpServerTool` / `ImageGenerationTool` / `ImageUnderstandingTool` / `ImageResponseParser` / `PhoneClickTool` / `PhoneClickViewTool` / `PhoneLongPressTool` / `PhoneSwipeTool` / `PhoneGlobalActionTool` / `PhoneScreenshotTool` / `PhoneViewHierarchyTool` / `ShellExecuteTool` / `WebFetchTool` / `WebSearchTool` / `TodoUpdateTool` / `AgentTool` / `AgentPipelineTool` / `CustomAgentExtensionTool` / `CustomMcpHttpTool`）全部添加 `public static final String NAME` 常量；`ToolCategory.isReadType` / `isWriteType` / `isDeleteType` / `isHttpType` / `isShellType` / `isAgentType` / `isAgentPipelineType` / `isTodoType` / `isImageGenerationType` / `isImageUnderstandingType` / `isPhoneControlTool` 全部从硬编码字符串改为引用常量。所有工具 `getName()` 返回 `NAME`，避免工具重命名时漏改一处。
- **`BaseTool` 扩展方法** - `requiresConfirmation` 标记 `@Deprecated`，新增 `needsConfirmation()`（`default false`）和 `isAllowedInReadonlyMode()`（`default false`）分别表达"是否需要用户确认"和"在只读模式下是否允许执行"；新增 `promptSupplement(String executionMode, boolean isSsh)`（`default null`）允许工具按执行模式与 SSH 标识补充提示文本，例如 `ImageUnderstandingTool` 在 SSH 模式下提示"通过 SFTP 读取 SSH 工作区图片"。
- **`ToolResult` 快捷工厂** - 新增 `static success(output)` / `error(error)` / `withReview(output, toolCallId, toolName, diffId, reviewState, reviewMessage)` 三个工厂方法，4 个旧构造器标记 `@Deprecated`，调用方从 `new ToolResult("", "", msg, true)` 简化为 `ToolResult.error(msg)`。
- **`ToolContext` 依赖注入扩展** - 新增 `ToolSettingsStore` / `ModelStore` / `SshFileTreeStore` / `ModelProtocolFactory` / `ModelClient` / `PromptTemplateRepository` 6 个可选依赖字段 + 4 个新构造器重载；`ToolExecutor` 在执行工具前调用 `needsInjection` 检查并 `injectDependencies` 把 Executor 持有的依赖注入到 `ToolContext`，工具不再需要在自己的 `execute` 里 `new ModelRepository(context)`。
- **`ToolExecutor` 8 参数构造器** - 新增 `ToolExecutor(registry, settingsRepository, diffRepository, modelRepository, sshFileTreeRepository, modelProtocolFactory, modelClient, promptTemplateRepository)` 全量依赖构造器；`MainDependencies` 改为调用 8 参数版，把整套依赖一次注入。
- **`ToolRegistry.getCachedDisplayCategory`** - 新增 `getCachedDisplayCategory(name)` 内部缓存 `Map<String, ToolDisplayCategory>`，避免每次 `getDisplayCategory` 都重新计算；启动期一次性 `preload` 全部内置工具。
- **图片生成工具依赖注入修复** - `ImageGenerationTool` 之前在自己构造时 `new` 一堆 repository，`dc6d82e` 改为从 `ToolContext` 取 `toolSettingsStore` / `modelRepository` / `modelProtocolFactory` / `modelClient` / `sshFileTreeRepository`，与 `ImageUnderstandingTool` 对齐；`ImageUnderstandingTool` 同步从 `Context` 构造改为 `ToolContext` 注入，并新增 `promptSupplement` 在 SSH 模式返回 "SFTP 读取图片" 提示。
- **`WebSearchTool` 环境提示** - `WebSearchTool.getDescription` 末尾追加"环境补充：搜索结果中给出的 URL 必须在 `web_fetch` 之前先用 `UrlPolicy` 校验"提示，提醒模型筛选可用 URL。
- **部分工具 readonly / confirmation 标识** - `FileReadTool` / `ListDirectoryTool` 标记 `isAllowedInReadonlyMode = true`（只读模式下仍允许执行）；`FileWriteTool` / `FileEditTool` / `FileDeleteTool` / `ShellExecuteTool` / `WebSearchTool` 等写操作工具标记 `needsConfirmation` 走原 `requiresConfirmation` 路径；`ImageGenerationTool` / `ImageUnderstandingTool` 标记 `needsConfirmation` 由用户在工具设置里决定。
- **`ImageGenerationTool` 协议工厂修复** - 之前 `ImageGenerationTool` 缺少 `modelProtocolFactory` 注入导致 `create(model.getProtocolType())` NPE，`dc6d82e` 注入后由 `ImageResponseParser` 统一解析图片 URL/Base64。
- **`HostBase` 抽象宿主** - 新增 `cn.lineai.mvp.HostBase implements CoordinatorHost`，把 `MainCoordinator` 公共方法（`basename` / `parentPath` / `projectPath` / `projectLabel` / `isSshExecutionMode` / `isTerminalProviderExecutionMode` / `showNotice` / `isViewAttached` / `viewHideOverlays` / `viewShowScreen` / `viewShowChatScreen` / `refreshVisibleScreen` / `returnToScreen`）抽到 `HostBase`，各 Controller 改为 `extends HostBase` 减少样板代码。
- **`ErrorLogController` / `PhoneControlController`** - 新增两个轻量 Controller，把 `ErrorLogRepository` / `PhoneControlRepository` 暴露给 `MainCoordinator`；`ErrorLogController` 提供 `list()` / `clear()`；`PhoneControlController` 提供 `isAccessibilityEnabled` / `isDisclaimerAccepted` / `isPermissionEnabled` / `setPermissionEnabled` / `setDisclaimerAccepted`。
- **`ToolCallTextParser` 健壮性** - 内部实现对工具调用文本解析失败时返回空数组而非抛异常；`ToolPromptRenderer` 路径从 `data/repository` 移到 `ai/prompt`，与 `SystemPromptProvider` 同包。

### 视图组件优化

- **`ChatMessageListView` 缓存复用重构** - 移除 `canReturnCachedView` 与 `bindCachedView` 两个内部方法，引入通用 `obtain(Class<T> type, String key, T created)` 工具：先查 `rowCache.get(key)`，类型匹配且 `getParent() == null` 则复用，否则用 `created` 替换并 `putCache`；`putCache` 顺手清理旧 view 的 `tag`。模型切换通知的缓存命中后不再调用 `bind`，直接复用 `convertView`。
- **`UserMessageView` / `AssistantMessageView` 复用判断** - `getView` 中先判断 `convertView` 是否同类型实例（`UserMessageView` / `AssistantMessageView`），同类型直接 `setXxxListener` + `bind` 复用；不同类型才走 `obtain` 路径，避免每条消息都 `new` 视图实例触发 `setToolReviewListener` / `setProjectPath` 副作用。
- **`MainChatView` 长按多选入口** - `listView.setOnItemLongClickListener` 把 `position` 透传给 `MultiSelectListener`，触发 `showMultiSelectDialog`。
- **`MarkdownTextBlockView` 触摸事件修复** - 删除原 `setTextIsSelectable(true)`，通过注释明确"会偷走兄弟按钮的触摸事件，改由 `TextSelectionDialog` 弹可选择 EditText"。
- **`ComposerView` 多选发送队列渲染** - `render(ChatUiState state)` 中检测 `wasStreamingBefore && !streaming && !pendingQueue.isEmpty()` 时 `post(sendPending)` 自动续发；`input.setEnabled(true)` 允许在流式期间继续输入；`attachButton` 仍按 `streaming` 灰化。
- **`UserMessageView` 引用块** - 新增 `quoteBlockView`（圆角深色 + 左侧 3dp `LineTheme.ACCENT` 竖线 + 最多 3 行省略的斜体引用文本）作为 `contentText` 之上的子视图，宽度限制与主气泡一致；空引用时 `setVisibility(GONE)` 隐藏。
- **`ChatMessageListView` 多选模式滚动条控制** - `updateScrollToBottomVisibility` 在 `multiSelectMode` 开启时强制隐藏"滚动到底部"按钮，避免遮挡底部操作条；`programmaticScroll` 标志在 `smoothScrollBy` 期间抑制"用户主动滚动"判断，避免流式跟随后误关闭 `followTailEnabled`。
- **`IconButtonView` 新增 4 个图标** - `SHARE` (80) / `QUOTE` (81) / `TEXT_CURSOR` (82) / `CHECK_SQUARE` (83)，对应 `ic_lucide_share_2` / `ic_lucide_quote` / `ic_lucide_text_cursor` / `ic_lucide_check_square` 4 个矢量图。
- **`MainChatView` 合并转发/AI 取名 UI** - 自定义圆角卡片布局：深色 `0xFF2A2A3E` 圆角 18 卡片 + "后缀自动添加: .pdf" 灰色提示 + `EditText` 圆角描边输入框（自动 selectAll + `inputType=text`）+ 紫色 `0xFF5B4FCF` 圆角 14 "AI 取名" 胶囊按钮。

### 许可证与构建优化

- **许可证资源打包方式重构** - 移除 `app/src/main/resources/META-INF/LICENSE`（674 行）与 `app/build.gradle.kts` 中 `META-INF/resources/META-INF/LICENSE` 的 `assets` 注入配置；新增 `app/build.gradle.kts` 自定义 `generateLicenseCopy` 任务，把仓库根 `LICENSE` 文件自动复制到 `app/build/generated/assets/license/LICENSE`，由 `processReleaseResources` / `processDebugResources` 作为额外 `inputs.file` 包含；统一许可证加载来源到根目录文件。
- **移除冗余 COPYING 文件** - 删除仓库根 `COPYING` 副本；同步更新中英文 `README.md` / `README_CN.md` 中对 `COPYING` 的引用，统一指向根目录 `LICENSE` 作为唯一许可证来源。

### 国际化与多语言

- **新增 11 条字符串** - `values/strings.xml` 与 `values-zh/strings.xml` 同步添加：`message_action_quote_desc`（引用消息）/ `message_action_share_desc`（分享消息）/ `message_action_select_desc`（选中文字）/ `message_action_multi_select_desc`（多选）/ `dialog_export_format_title`（导出格式）/ `dialog_select_text_title`（长按选中文字）/ `quote_preview_label`（引用）/ `export_button_label`（导出）/ `toast_clipboard_large`（内容较长，仅部分可能被复制）。
- **安全设置页文案** - `settings_row_security_allow_any_http_title` / `settings_row_security_allow_any_http_desc` / `screen_security_title` / `screen_security_section_http` / `screen_security_section_browser` 5 条新字符串。

### 兼容性

- **数据库迁移** - `LineCodeSchema` 表结构未变，仅在 `LineCodeDatabase.onOpen` 启动期增加 `integrity_check(1)` 自检与 `maybeRestoreFromBackup` 恢复，旧数据库直接打开不会丢失数据。
- **旧 `ToolSettingsRepository` API 兼容** - 旧调用方通过 `@Deprecated` 构造器仍可工作，新代码改用 `MainDependencies` 注入的 `ToolSettingsStore` 接口。
- **`ModelConfig` 旧构造器兼容** - 3 个旧 `@Deprecated` 构造器（`ModelConfig(id, name, protocolType, ..., modelId)` 等）保留可用；新代码使用 `ModelConfig.builder(...).toolCallLimit(...).compressionModelEnabled(...).build()` 链式构造。

### 版本

- 版本号升级到 `1.1.9`
- `versionCode` 升级到 `22`
- `minSdk` 升级到 `26`

### 测试

- 新增 `QuoteControllerTest`（94 行）覆盖 `setQuoteStoresText` / `composeWithQuotePrefixesQuote` / `composeWithQuoteClearsAfterSend` / `composeWithoutQuoteReturnsInputUnchanged` / `clearQuoteRemovesQuote` / `setQuoteNotifiesPreview` / `clearQuoteHidesPreview` / `longQuoteIsTruncatedInPreview` 等用例
- 新增 `ChatMessagesTest`（41 行）覆盖 `toPlainText` / `toMarkdown` / `wrapText` 三种格式的转换与换行
- 新增 `ExportFormatResolverTest`（49 行）覆盖默认注册顺序 / `register` 扩展 / `get` / `size` / `getDisplayNames` 行为
- 新增 `ExportResultTest`（32 行）覆盖 `forFile` / `forClipboard` / `forShareText` 三种 action 的字段填充
- 扩展 `AgentExecutionControllerTest`（+52 行）覆盖 `dc6d82e` 引入的 `isAllowedInReadonlyMode` / `needsConfirmation` / `promptSupplement(executionMode, isSsh)` 三组新方法
- `SlashCommandCatalogTest` 之前用例保持通过
- 已验证 `./gradlew :app:testDebugUnitTest` / `./gradlew :app:lintDebug` / `./gradlew :app:assembleDebug` / `./gradlew :app:assembleDebugUserCert`

---

## v1.1.8

### 聊天输入框斜杠命令

- **斜杠命令系统** - 聊天 composer 中输入 `/` 触发命令弹窗，按键入内容实时过滤候选项；支持主命令（`/chat`、`/plan`、`/agent`、`/control`、`/model`）、模型 id 补全与思考等级选择，点击候选项自动回填为完整命令文本
- **`SlashCommandCatalog` 解析与过滤** - 新增 `cn.lineai.ui.util.SlashCommandCatalog` 纯逻辑工具类，提供 `MAIN_COMMANDS` / `REASONING_LEVELS` 常量、`filterMain` / `filterModelIds` / `filterReasoningLevels` 前缀过滤、`parse` 整段文本解析为 `Kind.MODE` 或 `Kind.MODEL`；不依赖 Android 视图，便于 JUnit 覆盖
- **`SlashCommandPopup` 组件** - 独立实现 composer 上方展示的命令弹窗：圆角描边、行高紧凑化（标题 22dp）、`/` 前缀加 ACCENT 色 + BOLD 加粗、当前态圆点指示；`show` 在 `title+rows` 数量未变时跳过重建，`showAtAnchor` 自动对齐到 anchor 容器并向上 8dp 弹出
- **`/` 标签着色** - `SlashCommandPopup.formatLabel` 把以 `/` 开头的 token 第一个空白前的部分加 `ForegroundColorSpan(ACCENT)` + `StyleSpan(BOLD)`，弹窗中 `/chat`、`/model` 等命令一眼可辨
- **点击回调主线程投递** - 行 `OnClickListener` 改为 `new Handler(Looper.getMainLooper()).post(onClick)`，避免 popup `dismiss()` 与点击动作同帧触发的竞态
- **发送时拦截斜杠命令** - `ComposerView.handleSendClick` 在 send 时调用 `SlashCommandCatalog.parse`；命中 `MODE` 时走 `Listener.onModeChanged`，命中 `MODEL` 时同时调用 `onModelQuickSwitch` 与可选的 `onAiReasoningEffortChanged`；未命中时按原逻辑走 `onSend`
- **多语言文案** - `values/strings.xml` 与 `values-zh/strings.xml` 新增 `slash_command_main_title` / `slash_command_model_title` / `slash_command_reasoning_title` 以及 4 种模式、5 档思考等级的副标题，弹窗描述根据语言自动切换
- **流式生成时禁用** - `setStreaming` 在生成期间关闭并清理 `slashPopup`，避免与现有 model / mode 弹窗互相干扰

### Agent 工具审核

- **子 Agent 工具审核流程** - `AgentExecutionController.executeAgentToolCallWithReview` 在 `awaitReview` 之前调用新增的 `host.requestAgentToolReview(displayToolCallId, call, pending)`，主流程感知到 `pending` 审核态后立即持久化并 `render`；`finally` 块保证在执行完成、被拒绝或被中断时调用 `host.clearAgentToolReview` 清理
- **拒绝结果落库** - 拒绝分支不再直接返回新建的 `ToolResult`，而是同步调用 `progress.putToolResult` + `host.addOrReplaceToolResult(progress.snapshotResult())`，让 UI 在拒绝时立刻看到 `rejected` 状态卡片
- **`AgentProgressSession.snapshotResult` 透传状态** - 由 `new ToolResult(..., error)` 调整为 `new ToolResult(..., error, "", status, "")`，把 `pending` / `running` / `done` / `error` 等内部状态写进外层 `ToolResult.reviewState`
- **`GenerationFlowController` 跟踪待审核请求** - 新增 `pendingAgentToolRequests` 映射与 `requestAgentToolReview` / `clearAgentToolReview` / `isPendingAgentToolReview` / `acceptAgentToolReview` 入口；`cancelActiveGeneration` 同步清空；`MainCoordinatorDelegates.handleToolReview` 在收到审核回调时优先尝试 agent 路径
- **UI 审核态判断扩展** - `ToolCallAgentView` 读取 `result.getReviewState()`，与 progress status 任一为 `pending` 时都视为待审核；`pending` 卡片稳定显示，避免审核期间闪烁

### 工具调用与会话自动确认

- **会话级自动确认** - `AgentExecutionController.executeAgentToolCall` 在工具需要确认时先调用 `toolReviewAwaiter.isAutoConfirmed(call)`；命中则直接走 `toolExecutor.executeConfirmed`，跳过 `awaitReview` 阻塞与 UI 审核面板
- **`ToolReviewAwaiter` 接口扩展** - 新增 `boolean isAutoConfirmed(ToolCall call)` 默认返回 `false` 的方法，由主流程在初始化 `setToolReviewAwaiter` 时实现并与 `MainCoordinator.sessionAutoConfirmedTools` 联动
- **工具调用 UI 视图复用** - `ToolCallBlockView` 对 `ToolCallAgentView` / `ToolCallAgentPipelineView` / `ToolCallAgentView`（自定义 Agent）三类子视图在 `bind` 前先 `getChildAt(0)` 复用已有实例，避免每次刷新都重新 `new` 并触发 `setToolReviewListener` / `setProjectPath` 副作用
- **Todo 行高自适应** - `ToolCallTodoView` 将固定 `rowHeight` 改为 `minRowHeight`，通过 `setMinimumHeight` 实现最小 28dp + WRAP_CONTENT，长任务描述能自然撑高而短条目仍维持原视觉

### Agent 循环预算与远端模式

- **总时长预算替代最大轮次** - 移除 `AGENT_MAX_TURNS = 8` 硬轮次上限，新增 `AGENT_TOTAL_BUDGET_MS = 30 分钟`；`runAgentLoop` 把 `for (turn = 0; turn < 8)` 改为 `while (true)`，由取消 / 工具次数限制 / 时间预算三种条件共同决定退出，避免深度任务被 8 轮卡死
- **工具调用次数共享预算** - `GenerationFlowController.toolContext` 透传 `usedToolCallCount` 到 `AgentRunner`，`runAgentTool` / `runAgentPipelineTool` 在调用入口构造 `int[] toolCallBudget`；Agent / Pipeline / Pipeline 内部子 Agent 每次工具调用都 `toolCallBudget[0]++`，跨调用共享模型配置的 `toolCallLimit`
- **超过预算安全退出** - `runAgentLoop` 在每轮入口与执行工具前检查 `toolCallBudget[0] >= toolCallLimit`，命中返回 `AGENT_TOOL_LIMIT_MESSAGE`（"Agent 已达到主流程总工具调用次数上限。"）并把 progress 同步到 `error` 状态；Pipeline 收到含该消息的子结果立即中断后续层并写入错误摘要
- **时间预算检查** - 同样在每轮入口检查 `System.currentTimeMillis() - startedAt > AGENT_TOTAL_BUDGET_MS`，命中返回带"最后输出"的超时消息；Pipeline 整体 `pipelineBudgetMs = agents.size() * AGENT_TOTAL_BUDGET_MS` 在每个 level / agent 进入前双重判断
- **取消与初始检查** - `runAgentTool` / `runAgentPipelineTool` 入口增加 `cancellationToken.isCancelled()` 提前返回；Pipeline 在外层循环与每个 agent 进入前各检查一次取消与时间预算
- **Pipeline 进度汇总落库** - `PipelineProgressSession` 新增 `setStatus` / `setFinalSummary` / `getFinalSummary` / `payload` 公共方法与 `ProgressPublisher` 接口；`runAgentPipelineTool` 在最终/异常分支统一调用 `pipelineProgress.setFinalSummary` + `setStatus` + `publish(true)` + `addOrReplaceToolResult`，让 UI 在流水线结束时能直接拿到 summary
- **远端 Shell 模式提示词** - `agentRolePrompt` / `agentWorkspacePrompt` / `agentScopePrompt` 新增 `boolean remoteMode` 重载；SSH 或终端提供者模式下返回"远程 Shell"专用提示词：明确 `file_read / file_write / file_edit / glob / list_dir` 不一定可用、必须通过 `shell_execute` 完成探查与写入、禁止调用写操作类工具；探索 Agent 进一步禁止任何有副作用的命令

### 模型 HTTP 连接

- **读取超时延长到 10 分钟** - `AbstractHttpModelProtocol.openConnection` 把 `setReadTimeout(120000)` 调整为 `setReadTimeout(600000)`，匹配 Agent 30 分钟总预算下的长流式响应与 reasoning 内容；避免大输出 / 慢模型在 2 分钟后被强行断开
- **Connection: close 头** - 显式设置 `Connection: close` 请求头，避免长 keep-alive 在 NAT / 代理环境下被中间设备单边切断导致首包延迟或半包

### Agent 流水线视图

- **参数解析失败专用卡片** - `ToolCallAgentPipelineView` 在 `bind` 时调用 `parseInputError` 检查模型原始参数；JSON 解析失败或输入为空时直接渲染 `bindParseError` 视图（标题、红色 `CIRCLE_X` 状态、错误描述），不再误显示空 agents 列表
- **最终 summary 单独展示** - `progress.optString("summary")` 在 agents 为空时通过分割线 + `MarkdownView` 单独渲染，便于查看流水线终止原因

### 斜杠命令视觉

- **标题紧凑化** - `titleParams` 高度由 32dp 改为 22dp，margin 从底部 `XS` 调整为顶部 1dp，弹窗整体更紧凑
- **行间距** - 容器 vertical padding 由 0 改为 `LineTheme.SM`，描述行顶部 margin 1dp，行间层次更清晰

### 版本

- 版本号升级到 `1.1.8`
- `versionCode` 升级到 `21`

### 测试

- 新增 `SlashCommandCatalogTest`（134 行）覆盖 `filterMain` 空查询返回全部 / 大小写不敏感前缀匹配 / 未知前缀返回空、`parse` 模式命令解析、`/model` 必填 id 与可选思考等级、无效思考等级保留 id、未知命令与 `/model` 缺 id 返回 null
- 扩展 `AgentExecutionControllerTest`：
  - `subAgentToolReviewNotifiesMainFlowBeforeAwaiting` 验证子 Agent 工具审核在 await 之前就触发主流程的 `requestAgentToolReview` 并在结束时 `clearAgentToolReview`
  - `subAgentToolReviewClearsMainFlowAfterReject` 验证拒绝路径返回 `rejected` 状态、错误内容包含"用户拒绝执行"并清理主流程审核
  - `snapshotResultPropagatesStatusToOuterReviewState` 验证 progress 状态变化能正确透传到外层 `ToolResult.reviewState`
  - `shellExecuteAutoConfirmedSkipsReviewAndExecutesConfirmed` 验证会话级自动确认时不再走 `awaitReview` 且不触发主流程审核
  - `agentRolePromptSwitchesToShellInRemoteMode` 验证 `agentRolePrompt(type, false)` 提及 file 工具但不出现"远程 Shell"，`agentRolePrompt(type, true)` 出现"远程 Shell"、"shell_execute" 与"不一定可用"
  - `agentRolePromptExploreInRemoteModeRecommendsShell` 验证探索型 Agent 在远端模式下提示词同时出现"远程 Shell"与 `shell_execute`
- 已有 `shellExecuteWaitsForConfirmationBeforeRunningInsideAgent` 改名为 `shellExecuteWaitsForReviewAndResumesOnAccept` 并补充 `FakeHost` 默认实现 `requestAgentToolReview` / `clearAgentToolReview` / `isSshExecutionMode` / `isTerminalProviderExecutionMode` 桩方法
- 已验证 `./gradlew :app:testDebugUnitTest`

---

## v1.1.7

### 数据存储与导出

- **消息文本分块存储** - 新增 `message_text_chunks` 侧表，把 `messages.content` / `reasoning_content` / `raw_json` 等大文本按 64KB 切片存放，规避 SQLite `CursorWindow` 单行 2MB 限制；模型输出或大文件 diff 写入不再因为单行过大而触发 `SQLiteBlobTooBigException`
- **数据库版本升级到 4** - `LineCodeSchema.VERSION` 升到 4，并新增 `AddMessageTextChunksTable` 迁移，在 `onUpgrade` 时按顺序建表与索引；v3 数据库升级时数据不会丢失
- **Repository 适配分块读写** - `ConversationRepository`、`LearningContextRepository`、`StorageStatsRepository` 改为通过 `MessageTextChunkStore` 读写大文本；`ConversationIndexer` 写入索引时只保留 4000 字符的摘要，`raw_json` 不再落到 `conversation_index`，避免索引行过大
- **学习上下文兼容** - `LearningContextRepository` 在读取历史消息和索引时通过 `substr(..., 320)` 截取前缀，并通过 `message_text_chunks` 兜底读取分块文本；`storage_stats` 中的聊天与 diff 缓存统计改为基于 `MessageTextChunkStore.totalLength` 聚合
- **.linecode 归档适配分块** - `LineCodeDatabaseArchive` 导出时把 `messages` 表的大文本列置空并在 `message_text_chunks` 中按 64KB 切片存放；旧版归档中可能仍包含历史分块文本的兼容逻辑已补齐，新增 `nullCell` / `integerCell` / `stringCell` 工具方法

### 提示词与 Tool Call

- **Tool Call 配对修复** - `ModelPromptController.completeToolCallPairsForRequest` 会在构造模型请求前把缺失的 `tool` 消息补齐，避免 assistant `tool_calls` 没有对应结果导致 OpenAI / Anthropic / Responses 协议拒绝请求；同时把游离的 `tool` 结果移动到对应 assistant 之后，过期或孤儿的 `tool` 消息会被丢弃
- **中断补齐默认文案** - `ModelPromptController.Host` 新增 `interruptedGenerationMessage` 默认方法，中文返回 `上次生成已中断。`、英文返回 `Previous generation was interrupted.`；`MainCoordinator` 通过 `R.string.message_generation_interrupted` 覆盖默认文案，保证多语言一致
- **补齐逻辑覆盖 stop / kill 场景** - 用户主动停止生成、应用被强杀、进程重启后，模型请求都会带上补齐后的 `tool` 配对，对外模型不会因缺结果而报错或要求重复工具调用

### UI 与导航

- **屏幕切换无动画刷新** - `MainChatView.showScreen` 新增 `animate` 重载；`invalidateScreen` 重建当前页时不再播放转场动画，避免数据刷新时的闪烁；`ScreenView` 新增 `evictScreen` 默认方法，调用链路更清晰
- **前进 / 返回方向控制** - `ScreenNavigationController.Host.showScreen(id, forward, animate)` 默认实现转发到 `showScreen(id, forward)`，新接口允许在 `backFrom` 路径上保留方向感知的反向动画
- **抽屉方向修复** - `DrawerView` 从顶部面板调整为左侧侧边栏样式：`Gravity.START` + `DrawerWidth 360dp` + `translationX` 动画；与 v1.1.6 的描述一致，避免小屏上文件树与会话列表被压缩
- **`refreshVisibleScreen` 不再误触发动画** - `MainCoordinator` 中两处 `view.invalidateScreen` 调用改为 `view.evictScreen` + `showScreen(id, true, false)`，重建当前页时直接落到终态，行为符合直觉

### 版本

- 版本号升级到 `1.1.7`
- `versionCode` 升级到 `20`

### 测试

- 新增 `ModelPromptControllerTest` 覆盖：缺失 `tool` 结果补齐、已有 `tool` 结果重排到 assistant 之后、孤儿 `tool` 结果丢弃、空输入不抛异常等场景
- 新增 `AddMessageTextChunksTableTest` 校验迁移目标版本为 4
- 扩展 `LineCodeSchemaTest` 覆盖 `message_text_chunks` 表与索引的建表 SQL 声明
- 扩展 `ScreenNavigationControllerTest` 覆盖 `refreshVisibleScreen` 无动画重建与 `backFrom` 反向动画
- 已验证 `./gradlew :app:testDebugUnitTest`

---

## v1.1.6

### Agent 与工具审批

- **子 Agent 工具确认机制** - Agent 内部调用需要确认的工具时不再直接失败，而是进入与普通工具一致的待确认流程；Shell、删除等高风险工具会显示等待确认状态，用户确认后继续执行，拒绝后写入拒绝结果
- **Agent 进度待确认状态** - Agent 进度 JSON 新增 `pending` 状态展示，卡片中显示“需要确认”，并保留嵌套工具调用、确认状态、执行结果与错误信息，避免等待确认时被误判为运行失败
- **Agent Pipeline 待确认展示** - Agent 流水线卡片支持统计和展示子 Agent 的待确认状态；每个 Agent 行可展开显示其内部工具调用，审批按钮与普通工具调用保持一致
- **会话级自动确认延续到 Agent** - 子 Agent 内部工具确认支持“本次会话自动执行”，确认策略与普通 Shell 工具一致，减少重复审批

### Bug 修复

- **SSH 连接池锁异常** - 修复 SSH 测试连接和执行命令在输出正确后仍抛出 `IllegalMonitorStateException` 的问题；新建连接会正确持有锁，释放、丢弃和重建连接时只由持锁线程解锁
- **中断后重开进度圈残留** - 修复用户停止生成、直接关闭 APP 或进程被杀后，重新打开会话仍显示进度圈的问题；加载历史会话时会把残留的 `streaming`、`running`、`pending`、缺失 tool result、Agent / Agent Pipeline 运行态统一收敛为“上次生成已中断。”并落库
- **Agent / Pipeline 停止状态清理** - 主动停止生成时复用同一套进度清理逻辑，Agent、子 Agent 工具调用、Agent Pipeline 运行态与待确认态都会立即变为错误完成态，避免 UI 继续等待不存在的后台任务

### UI 与导航

- **屏幕切换动画** - 设置页、模型页、扩展页等二级页面新增前进/返回方向感知动画，返回时使用反向过渡，导航栈状态更清晰
- **抽屉与弹层动画优化** - 优化抽屉、底部操作表、目录选择器、附件选择器的入场和退场动画，关闭时会取消旧动画并复位状态，减少快速切换时的闪烁和残留
- **抽屉布局调整** - 会话/文件抽屉改为更适合移动端的顶部面板样式，并限制高度，文件树与会话列表在小屏上更稳定

### 版本

- 版本号升级到 `1.1.6`
- `versionCode` 升级到 `19`

### 测试

- 新增 `AgentExecutionControllerTest` 覆盖子 Agent Shell 工具确认、拒绝、自动确认与权限边界
- 新增 `SshConnectionPoolTest` 覆盖新建连接释放、丢弃重借、陈旧连接移除等锁行为，防止回归到 `IllegalMonitorStateException`
- 扩展 `ScreenNavigationControllerTest` 覆盖前进/返回方向传递
- 新增 `ConversationResumeSanitizerTest` 覆盖重启恢复时清理 streaming、pending 工具、缺失 tool result、Agent pending、Pipeline running 等残留状态
- 已验证 `./gradlew :app:testDebugUnitTest`

---

## v1.1.5

### Tool Call 与提示词

- **扩展工具顺序稳定** - 自定义 MCP / 扩展工具在工具提示词中按工具名排序输出，避免同一批工具因 Map/加载顺序变化导致提示词不稳定
- **工具调用次数提示去重** - 移除 `ModelPromptController.buildToolPrompt` 中重复拼接的工具调用次数限制说明，改由工具提示构建逻辑统一处理；次数耗尽时仅提示“当前没有可用工具”，减少重复和冲突文案
- **系统提示模板顺序调整** - `{{TOOLS_CONTEXT}}` 从 Agent 协作规则前移到工作目录上下文之后，让工具清单更贴近当前工作区信息，降低提示词前段噪音

### Bug 修复

- **Agent 进度回调栈溢出** - 修复 Agent / Agent Pipeline 工具进度回调在主线程同步派发时递归调用自身的问题；Release 混淆栈中的 `hi.j()` / `t.run()` 死循环已定位为 `GenerationFlowController` 匿名 Host 回调未限定外部类方法，现改为明确调用外层控制器，避免 `StackOverflowError`
- **保活通知权限** - 补齐 Android 13+ `POST_NOTIFICATIONS` 权限声明与运行时申请；用户开启前台服务通知或假音乐播放时会主动申请通知权限，权限返回后自动重新应用保活设置
- **前台保活通知实现** - 完善 `KeepAliveService` 前台通知，补齐默认状态文案、通知点击回到 LineCode、service 分类、ticker 与可见性设置；无保活开关时会停止服务，避免空启动
- **假音乐播放保活** - 补上静音 `AudioTrack` 循环播放实现，用户开启“假音乐播放”后后台任务期间会实际启动静音音频；任务结束或关闭开关时会停止并释放音频资源

### 测试

- 新增 `ToolSettingsRepositoryTest` 覆盖扩展工具按名称稳定排序输出
- 新增 `GenerationFlowControllerTest` 覆盖 Agent 工具结果回调只派发一次，防止回归到递归栈溢出
- 已验证 `testDebugUnitTest` 定向测试、`compileDebugJavaWithJavac` 与 `assembleDebug`

---

## v1.1.4

### 架构与维护

- **MVP 控制器继续拆分** - 将聊天交互、生成流程、模型提示、会话持久化、上下文压缩、目录选择、扩展草稿、扩展管理、IPC Provider、文件树交互、项目工作区、模型交互、覆盖层动作、工具审批、工具消息与存储维护等职责拆到独立控制器，`MainCoordinator` 进一步收敛为协调入口，降低后续功能改动的冲突面
- **屏幕缓存与刷新机制** - `MainChatView` 新增屏幕缓存，切换设置/模型/扩展等页面时复用已构建视图；同时新增 `invalidateScreen` 刷新入口，支持在数据变化后精准重建当前页面
- **模型列表错误日志** - 查询模型列表与压缩模型列表失败时会写入错误日志中心，并对 API key 等敏感字段脱敏，方便定位第三方模型服务返回异常

### Tool Call 与上下文

- **停止生成时补齐 tool 消息** - 用户停止生成或工具执行被中断时，会为所有未完成的 `tool_call` 追加错误态 `tool` 结果，并替换仍处于 `running`、`pending` 或空内容 `accepted` 状态的工具结果，避免后续请求因缺少 tool 消息而触发模型协议顺序错误
- **上下文窗口保持工具调用分组** - `ContextManager` 选择上下文窗口时会把 assistant 的 `tool_calls` 与对应 `tool` 结果作为一组保留，避免只保留 tool 结果或只保留 assistant tool_call 导致 OpenAI/Responses 等协议拒绝请求

### SSH 与 Shell 修复

- **SSH 成功命令不再误报错误** - 修复 SSH 命令已成功结束但收尾读取阶段被中断时误标为失败、界面显示 `错误:null` 的问题；命令通道关闭后会继续读取退出码和剩余输出，再恢复线程中断状态
- **工具执行错误文案更准确** - `ToolExecutor` 区分参数解析失败与工具执行失败；异常无 message 时显示异常类型或“未知错误”，不再把 `null` 拼进用户可见错误
- **Shell 退出码展示修复** - IPC/终端提供者 shell 返回非 0 退出码且已有输出时，会同时保留命令输出和退出码，便于判断失败原因
- **SSH 设置页错误显示修复** - SSH 连接测试失败时会显示可读异常描述，避免无 message 异常显示空白或 `null`
- **主题页面文案修复** - 修复 Release 资源裁剪后主题页面显示 `color_background`、`starter_high_contrast` 等带下划线 ID 的问题；主题模式、创作起点、颜色项和“当前编辑”标签改为直接引用多语言资源，中文环境下正常显示中文名称

### 测试

- 新增 `ToolMessageControllerTest` 覆盖停止生成时为所有未完成 tool call 补齐结果、保留已完成结果、替换 running 结果
- 新增 `ToolExecutorTest` 覆盖无 message 异常不再渲染 `null`
- 扩展 `ContextManagerTest` 覆盖 assistant tool_calls 与 tool 结果在上下文窗口中的成组保留

---

## v1.1.3

### 新功能

- **控制手机 (实验)** - 新增手机控制能力，可在 `设置 -> 高级功能 -> 控制手机` 中开启并配置权限；支持截图、坐标点击、滑动、长按、View 层级读取、按文本/资源 ID 点击 View，以及返回、主页、最近任务、通知栏、快捷设置、锁屏、退出当前应用等系统动作
- **截图与图片理解联动** - 手机截图改为保存到应用缓存目录并仅向 AI 返回图片路径，AI 可直接将路径传给现有图片理解工具；截图缓存会在应用启动时清理，避免长期占用存储
- **错误日志中心** - 新增 `设置 -> 数据 -> 错误日志` 页面，遇到解析、HTTP、流式、SSE、IO、未捕获异常等错误时自动保存完整日志到应用私有目录；列表可查看日志，点击后可通过其他应用打开完整堆栈
- **HTTP/解析诊断日志** - HTTP 与响应解析相关错误会记录完整请求、完整响应、流式已接收内容和异常堆栈，并自动脱敏 `Authorization`、`x-api-key`、JSON 密钥字段与大段图片/base64 内容
- **关于页 GitHub 入口** - 关于页面新增 GitHub 仓库入口，点击可跳转到 `github.com/LangLang03/LineCodePro`

### 优化与体验

- **手机控制工具卡片** - 控制手机工具调用不再按 MCP 通用 JSON 卡片展示，改为与文件读取、目录列表、搜索等工具一致的简洁展示；标题直接显示 `截图`、`点击`、`滑动`、`长按`、`系统动作` 等具体操作名称
- **手机控制设置页** - 优化控制手机设置页面布局，权限申请、权限管理标题与开关行对齐；移除权限管理底部窄卡片背景，视觉上更接近普通设置列表
- **手机控制 i18n** - 手机控制工具卡片、权限项、错误提示、执行结果等用户可见文案全部资源化，补齐英文与中文翻译，避免硬编码中文
- **图片理解模型提示调整** - 控制手机页面不再因未配置图片理解模型而禁用功能；未配置时由图片理解工具在实际调用时给出提示
- **LLM 通信保活** - LLM 生成、流式响应和手动压缩期间临时启用前台保活服务与 WakeLock，降低应用进入后台后与 LLM 通信断开的概率；生成结束后会释放临时保活，不影响用户手动保活状态

### Agent 与远端工具

- **SSH 子 Agent 修复** - 修复 `ssh` 模式下 Agent、Agent 编排、探索 Agent、Sub Coding 工具列表为空的问题，子 Agent 可正常使用远端 shell、搜索、任务清单、图片理解等工具
- **SSH 只读策略调整** - SSH/终端提供者模式下不再通过工具过滤强制子 Agent 只读，而是通过提示词约束探索类 Agent 只执行只读命令；Agent 与 Agent Pipeline 在子 Agent 内继续黑名单递归调用，避免无限嵌套
- **终端提供者工具隔离** - SSH Shell 与终端提供者使用独立工具开关存储，关闭终端提供者的图片理解、Shell 等工具不会影响 SSH 模式，反之亦然
- **终端提供者命名优化** - 终端提供者里的 Shell 工具组改为 `IPC Shell`，描述改为通过终端提供者 IPC 执行，避免与 SSH Shell 混淆

### Bug 修复

- **无障碍状态检测** - 修复系统已开启无障碍但应用重进后工具调用误报“无障碍没打开”的问题；当授权存在但服务连接尚未恢复时，会短暂等待连接并给出更准确提示
- **截图兼容性崩溃** - 修复部分 Android 版本 `AccessibilityService.ScreenshotResult` 无 `getBitmap()` 方法导致的反射崩溃问题
- **手机控制系统动作** - 新增独立系统动作工具与权限开关，补齐返回、主页、最近任务、通知栏、快捷设置、锁屏、电源菜单、退出当前应用等能力，避免 AI 无法退出 APP 或返回上一级
- **错误日志打开与清理** - 错误日志通过只读私有 `ContentProvider` 授权单个日志文件给外部应用打开，右上角支持一键清空全部日志

### 安全与合规

- **GPL 许可证打包** - 将 GPL 许可证作为 APK `META-INF/LICENSE` 随包分发，不放入 `strings.xml`，不放入 Android `res/raw`；Gradle packaging 保留该文件，避免被默认排除
- **日志脱敏** - 错误日志保存前自动脱敏密钥、鉴权头与超大图片内容，降低诊断日志泄露敏感信息的风险

### 测试与验证

- 新增 `ToolSettingsRepositoryTest` 覆盖 SSH/终端提供者工具开关隔离、远端工具过滤与 IPC Shell 展示
- 新增 `ErrorLogRedactorTest` 覆盖错误日志密钥、鉴权头和大段内容脱敏
- 已验证 `compileDebugJavaWithJavac`、`lintDebug`、`assembleDebug`，并检查 APK 内包含 `META-INF/LICENSE`

---

## v1.1.1

### 优化与重构

- **IPC 架构模块化** - 新增 `ipc` 独立 Gradle 模块（`ipc/`），将原 `app` 内的 IPC 服务端/客户端抽象（`IpcProviderManager`、`IpcProviderRegistry`、`IpcProviderScanner`、`IpcProviderConnectionState`、`IpcProviderStateListener`、`IpcProviderFactory`、`BaseIpcProvider`、`AbstractIpcProviderService`、`IpcServerExecutors`、`IpcServerPermission` 等）抽离到独立模块；`terminal-provider` 模块新增 `TerminalIpcProvider` / `TerminalProviderServiceFactory` 接入新抽象；`cn.lineai.ipc.terminal` 模块继续作为终端提供者实现，整体 IPC 拓扑从「app 内混合」改为「core 抽象 + provider 实现」分层
- **Provider 管理器重构** - `IpcProviderManager` 改为支持工厂注册扩展（`IpcProviderRegistry`），新增全局观察者模式与连接状态机（`IpcProviderConnectionState`），供 UI 订阅 provider 连接生命周期
- **MainCoordinator 内部类外提** - 8 个内联内部类（`AgentRunResult`、`PipelineAgent`、`AgentProgressMirror`、`AgentProgressSession`、`PipelineProgressSession`、`PipelineAgentState`、`ToolExecutionBatch`、`PendingToolExecution`）抽取为 `cn.lineai.mvp.agent` 包下的独立顶层类；`MainCoordinator` 由 561 行 → 180 行；统一以 getter 替换直接字段访问，简化 `Collections.singletonList` 调用
- **AgentExecutionController 抽取** - 将 `MainCoordinator` 内联 Agent 与 Pipeline Agent 执行相关代码（590 行）抽离到独立 `AgentExecutionController`；`MainCoordinator` 由 639 行 → 49 行，职责简化为协调调度
- **实验性功能与插件页清理** - 移除 `ExperimentalSettingsScreenView`、`PluginPageScreenView` 页面与 `ScreenFactories` 中的注册；清理 `SettingsScreenView`、`ScreenNavigationController`、`MainChatView`、`SimpleScreenContent` 中的实验性入口与冗余资源；`values/strings.xml` 与 `values-zh/strings.xml` 同步删除对应文案
- **教程页重构** - 新增 `app/src/main/assets/tutorials/simple.md`（78 行）与 `pro.md`（217 行）两套内置教程源；`TutorialScreenView` 支持从 assets 读取 Markdown 渲染，新增简单模式与专业模式分类
- **Markdown 渲染** - `MarkdownView` 扩展 36 行，支持教程页与错误状态文本渲染
- **AssistantMessageView 错误态** - 新增错误状态纯文本渲染支持
- **附件选择器** - `AttachmentPickerCoordinator` 调整 73 行以适配新 IPC 抽象

### 文档

- **README 社交预览** - `README.md` 与 `README_CN.md` 顶部新增动态社交预览横幅
- **ipc 模块文档** - 新增 `ipc/README.md`（741 行）与 `ipc/README_CN.md`（739 行），介绍 IPC 抽象、Provider 注册、连接状态机、权限模型与扩展方式

---

## v1.1.0

### 新功能

- **IPC 终端提供者** - 新增 `cn.lineai.ipc.terminal` 模块，通过 AIDL 跨进程调用终端提供者服务执行 SHELL/SFTP/文件读写操作；支持 `executeShell`、`readFile`/`writeFile`、`readFileChunk`/`writeFileChunk`（大文件分块，1MB 上限）、`listDirDetailed`、`statFile`、`getFileSize` 等接口
- **AIDL 混淆保护** - `proguard-rules.pro` 新增 IPC 接口 keep 规则，保护 AIDL 生成的 `Stub` / `Proxy` / `asInterface` 入口类名，避免 release 包跨进程 Binder 调用失败

### 优化与重构

- **Repository 接口抽象** - 为 `ConversationRepository` / `ProjectRepository` / `ModelRepository` / `DiffRepository` / `ExtensionRepository` / `IpcProviderRepository` / `LearningContextRepository` / `ToolSettingsRepository` / `FileTreeRepository` / `IpcFileTreeRepository` / `SshFileTreeRepository` 抽取 `*Store` 接口；`MainDependencies` 字段类型改为接口
- **BaseRepository 公共基类** - 抽取 `database` 字段与 `value/intValue/longValue/safe` Cursor 辅助方法，消除各 Repository 重复代码
- **ExtensionRepository 拆分** - 1232 行巨型门面拆为 `AgentExtensionRepository`、`McpExtensionRepository`、`SkillRepository` 三个子 Repository；`ExtensionRepository` 降为 103 行纯门面
- **LearningContextRepository 拆分** - 793 行拆为 `MemoryRanker`（BM25 排序）、`TextTokenizer`（CJK 双字分词）、`ConversationIndexer`（对话索引）三个独立类
- **MainCoordinator 拆分** - 5046 行 → 4981 行，提取 `AttachmentPickerCoordinator` 接管附件选择器的 8 个状态字段与 7 个方法
- **MainChatView 拆分** - 1589 行 → 789 行（-50%），提取 `ScreenFactory` 注册表（582 行 `buildScreen` → 7 行委托）、`DialogManager`、`PermissionUiHelper`、`MainChatViewLayoutBuilder`、`SimpleScreenContent`、`FileActionRow`、`BackNavigation`、`DialogDimensions`、`SafPickerDelegate` 共 9 个独立类
- **巨型 Controller 拆分** - `SettingsController` 拆为 7 个细粒度接口（AiBehaviorSettingsController / InputSettingsController / OutputSettingsController / ThemeSettingsController / McpSettingsController / ArchiveController / StorageController）；`MainContract.View` 拆为 5 个细粒度接口（ChatRenderView / OverlayView / PickerView / ScreenView / PermissionView）
- **过长方法拆分** - `CodexResponsesProtocol.stream()`（122→28）、`AnthropicMessagesProtocol.stream()`（87→30）、`ToolCallWriteView.bind()`（130→20）、`MarkdownRenderer.renderBlock()`（70→9）
- **重复代码消除** - `RemoteFileTreeController` 公共基类；`BaseTool.ok/error` 上移；`FileIo.readUtf8` 提取；`ToolArgs.requireNonEmpty` 提取；`BaseToolCallView` 抽象基类；`thinkingBudget` 上移到 `AbstractHttpModelProtocol`
- **OCP 改造** - `ModelProtocolFactory`、`Migrations`、`ArchiveSecretRedactor` 改为注册表实现，新增工具通过 `BuiltInToolProvider` 注册到 `BuiltInToolProviders.defaults()` 即可
- **SshService 拆分** - 提取 `SshConnectionPool`（连接复用缓存）与 `TermuxHelper`（Termux 集成独立类）；`termux_setup.sh` 移至 assets 资源
- **ContextCompactionService 依赖注入** - 构造函数注入 `ModelClient` / `OpenAiResponsesCompactionProtocol` / `CodexResponsesProtocol` / `PromptTemplateRepository`；`MemoryExtractionService` 从 `data.repository` 包移至 `context` 包修复反向依赖
- **字符串外部化** - 所有硬编码中文字符串替换为 `strings.xml` 资源引用，英文 / 中文分别放在 `values/` 和 `values-zh/`
- **兼容性代码清理** - 移除 `getSelectedProjectLegacy`、`SshService.TERMUX_*` 委托字段、`SshService.TermuxSetupResult` 委托类、`MainActivity.Legacy branch` 注释等兼容代码

### Bug 修复

- **HttpServerTool 健壮性** - 请求头读取改为循环至结束标记（8192 字节上限）；`new Thread` 替换为 10 线程固定线程池；`Socket.setSoTimeout(30s)`；异常记录 `Log.w` 不再静默
- **AIDL 大文件** - `ITerminalProviderService` 新增 `readFileChunk` / `writeFileChunk` / `getFileSize` 三个分块方法，避免 1MB+ 事务缓冲区溢出
- **SshService TOFU** - `TrustOnFirstUseUserInfo.promptYesNo` 添加警告日志，降低中间人攻击风险
- **数据库迁移** - `LineCodeSchema` 集中 SQL 常量；`AddToolCallObservabilityColumns.apply` 通过 `PRAGMA table_info` 检查避免重复列；`onCreate` 与 `onUpgrade` 职责厘清
- **IPC 权限** - 新增 `IpcProviderScanner` / `BaseIpcProvider` 校验 target service signature fingerprint

### 安全

- **UrlPolicy SSRF 加固** - 增加内网 IP 段黑名单（192.168/10/172.16-31）、端口白名单（80/443/8080/8443）、URL 用户信息（user:pass@host）校验
- **KeepAliveService** - 移除静音音频 hack；WakeLock 与任务生命周期绑定
- **requestLegacyExternalStorage** - 移除冗余声明
- **AIDL 调用方权限** - 在 `ITerminalProviderService.aidl` 注释中声明调用方所需权限

### 测试

- 新增 `UrlPolicyTest` 覆盖 SSRF 新增场景
- 新增 `LineCodeSchemaTest`、`AddToolCallObservabilityColumnsTest`、`MigrationsTest` 覆盖迁移框架
- 新增多个 Repository / Controller / Service 单测

---

## v1.0.9

### 新功能

- **数据库迁移框架** - 新增版本化迁移系统：抽象类 `DatabaseMigration`、`Migrations.all()` 迁移注册表、`schema_migrations` 迁移记录表；`onUpgrade` 路径由"DROP 全部表 + onCreate 重建"改为"按版本顺序应用迁移"，升级不再破坏用户数据
- **tool_call 可观测字段** - `tool_calls` 表新增 `duration_ms INTEGER NOT NULL DEFAULT 0` 与 `error_message TEXT` 两列，支持记录工具耗时与失败原因
- **TODO 任务清单** - 新增 `todo_update` 工具与卡片视图，模型在每轮开始可把计划拆分为三态任务项（未开始 / 进行中 / 已完成），工具卡片会跟随消息流滚动，并在每次列表变化时追加新卡片
- **TODO 状态注入** - 当前任务清单每轮自动注入到系统提示词的 `{{TODO_STATE}}` 占位符，模型能感知待办进度
- **工具与执行页开关** - 任务清单、Agent、Agent Pipeline 在本地模式与 SSH 模式下均显示开关；Agent 与 Agent Pipeline 在 SSH 模式下可用，子 Agent 通过 `shell_execute` 在 SSH 环境内完成文件操作

### Bug 修复

- **数据库迁移崩溃** - 修复 `LineCodeDatabase.applyMigrations` 在 `onUpgrade` 路径上未先创建 `schema_migrations` 表就尝试 `INSERT` 导致的 `SQLiteException: no such table: schema_migrations` 崩溃；v1→v2 升级路径现在安全
- **.linecode 归档导入校验** - `LineCodeDatabaseArchive` 拒绝导入由更高版本生成的快照，避免数据丢失；旧版本快照导入时打印警告日志
- **归档 schemaVersion 硬编码** - `LineCodeArchiveCodec` 写出的 `schemaVersion` 从硬编码 `2` 改为读取 `LineCodeSchema.VERSION`，未来升级不再失同步

### 测试

- 新增 `LineCodeSchemaTest`、`AddToolCallObservabilityColumnsTest`、`MigrationsTest` 单测覆盖迁移框架与 schema 变更

---

## v1.0.8

### Bug 修复

- **添加自定义 MCP** - 修复 `SettingsSectionView.addRow` 中 view parent 状态泄漏导致的 `IllegalStateException`，连续添加 3 个及以上自定义请求头不再闪退；`McpExtensionEditScreenView.save()` 在未查询 tools 或 URL 变更时拒绝保存，避免保存无效 MCP

---

## v1.0.7

### Bug 修复

- **自定义 Agent UI** - 修复用户自定义 Agent 扩展（agentx_*）使用 MCP 通用组件渲染的问题，现在正确使用内置 Agent 专用组件，支持类型标签、嵌套工具调用展示、thinking 块、流式进度等完整 Agent UI 能力，并修复Agent无法使用部分TOOL和MCP的问题

---

## v1.0.6

### CI/CD 修复

- 修复 GitHub Actions Release 变量传递问题，正确显示版本名称

---

## v1.0.5

### UI 优化

- **模型选择器** - 模型快速切换按钮宽度改为自适应，根据模型名称长度自动调整

### Bug 修复

- **通知权限** - 修复 Android 13+ 保活服务通知权限检查问题
- **CI/CD** - 修复 GitHub Actions 签名配置路径问题

---

## v1.0.4

### 新功能

- **保活服务** - 新增后台保活功能，支持长时间运行任务
- **存储管理** - 添加存储统计与管理页面
- **代理流水线** - 实现 Agent Pipeline 多步骤执行能力
- **模型快速切换** - 聊天界面新增模型下拉框，支持快速切换模型
- **模型身份提示** - 支持模型身份信息注入到系统提示词

### CI/CD

- **自动发布** - 新增 GitHub Actions Release APK 自动构建与发布流程
- **版本触发** - 版本号变更时自动触发构建，发布到 GitHub Releases
- **更新日志** - 新增 `update.md` 文件，自动读取版本更新内容

### 优化与重构

- 重构模型管理 API 与工具检查逻辑
- 更新聊天消息处理流程

---

## v1.0.3

### 新功能

- **图片生成工具** - 新增 ImageGenerationTool，支持 AI 图片生成
- **图片理解工具** - 新增 ImageUnderstandingTool，支持图片内容分析
- **数据导入导出** - 支持 `.linecode` 格式的完整数据备份与恢复
- **会话级工具确认** - 实现会话级自动确认工具执行，减少重复确认
- **输入设置** - 新增输入设置页面，支持回车键行为自定义

### 安全加固

- 完成安卓合规审计相关的安全加固与优化
- URL 策略强制 HTTPS，限制明文流量
- WebView 安全配置强化

### 文档

- 新增多语言文档
- 添加许可证文件

---

## v1.0.2

### 新功能

- **提示词模板管理** - 新增提示词模板管理功能
- **消息附件支持** - 实现消息附件支持与消息操作功能
- **输入设置页面** - 新增输入设置页面和回车键行为控制

### 优化

- 多项功能优化与 bug 修复

---

## v1.0.1

### 新功能

- **聊天模式** - 添加聊天模式功能
- **扩展系统** - 新增扩展系统与技能管理能力
- **SSH Shell 工具** - 新增 SSH Shell 工具与上下文压缩功能
- **项目管理** - 完善项目管理与 UI 体验

---

## v1.0.0

### 核心功能

- **AI 聊天** - 基于 LLM 的智能对话系统
- **工具系统** - 文件读写、Shell 执行、Web 搜索等内置工具
- **工作区管理** - SAF 文件树选择与项目管理
- **MVP 架构** - 单 Activity MVP 架构设计
- **多模型协议** - 支持 OpenAI Compatible、Anthropic、Codex Responses 等协议
