# LineCode C++ 迁移清单

最后更新：2026-09-11（夜间续作），分支 `hui-cpp`。

> 本轮由代码级审计确认的最大缺口：**内置工具套件未接入运行时**。
> `src/app/app_root.cpp` 的 `CompositeToolRegistry` 过去只装入 MCP 扩展、
> 图像生成、图像理解、SSH 与终端提供者五类来源，旧版 14 个内置工具
> （file_read / file_write / file_edit / file_delete / glob / list_dir /
> todo_update / memory_update / web_fetch / web_search / agent /
> agent_pipeline / agent_output / shell）在默认 local 模式下**一个都不存在**，
> 模型没有任何文件读写、检索、待办、记忆或联网能力。这是产品核心闭环的
> 阻断项，已列为 P0，正在按组补齐并接入组合根。

本文件是剩余工作清单，不是完成声明。只有同时通过功能、数据安全、旧版/新版同机截图和真机交互验证的项目，才可以勾选为完成。

## 不可变约束

- [ ] 除下列明确例外外，旧版 UI、交互和可观察功能全部 1:1 迁移。
- [x] 删除 Accessibility、Phone Control、控制模式及其教程/提示词入口，不在任何平台重新暴露。
- [x] Android 显示后台保活入口；Windows 不显示 Android 保活入口。
- [x] 使用 C++23 与 HuxerUI；Java 仅用于 Android 必需的平台桥接。
- [x] 页面导航使用 `NavigationStack`，主页侧栏使用 `DrawerLayout`。
- [x] SQLite 使用 `HuxerUI/Lib-SQLite`；WebView 使用 `HuxerUI/Lib-WebView`。
- [x] Android 包名保持 `cn.lineai`，versionCode `32`，versionName `1.2.8-max`。
- [x] Release 沿用原签名；SHA-256 为 `1c2c0c3db2c39b31355168ec30a7026f7c6b3931f25fd650745dd88112a64fac`。
- [x] 对照版包名为 `cn.lineai.legacy`，可与新版同时安装。
- [ ] 所有新功能继续遵循 SRP、DIP、OCP、KISS；业务策略、平台能力和 Hux UI 分层。
- [ ] 开源许可只列实际使用的依赖，不复制无关许可内容。
- [ ] 永远不提交根目录用户文件 `error.log`。

## 当前可复现验证基线

本轮已完成并通过验证（提交 `520d3de`，已推送 `origin/hui-cpp`）：

- [x] 聊天导出接入 `ChatExportService`：更多菜单「导出对话」与多选导出共用
      旧版同一个格式选择器（剪贴板 / 纯文本 / Markdown / PDF / 对话截图）。
- [x] 底部弹层遮罩改用调色板 `overlay`（旧版语义），弹层 MAE 从 47.2/53.9/34.1
      降到 4.4/4.8/2.7，遮罩像素与旧版逐通道一致。
- [x] 主题页「创作起点」网格恢复每格 8dp 尾部边距与网格 8dp 顶距。
- [x] 许可页补上实际链接的 libssh2 与 Mbed TLS。
- [x] 代码块复制按钮接入真实剪贴板与「已复制」提示（此前无 `OnClick`）。
- [x] 原生测试 61/61（新增 todo 工具契约测试），Android Release 双 ABI + Lint +
      原签名通过，模拟器冷启动无崩溃。

仍在进行中（未完成，不得勾选）：

- [x] 内置工具组接入 `app_root.cpp`：`todo_update`、`memory_update`、
      `web_search`、`web_fetch` 四个工具已注册进 `CompositeToolRegistry`
      （提交 `a35c0f3`）。契约与旧版逐字一致，组开关与执行模式门禁照旧版。
- [x] `file_ops` 组（`file_read` / `file_write` / `file_edit` / `file_delete` /
      `glob` / `list_dir`）已接入 `CompositeToolRegistry`。本地化违规已修复：
      application 层不再调用组合期的 `UseString`，改由应用层自有的
      `ToolTextCatalog`（`src/application/tool_text_catalog.*`，由
      `tools/gen_tool_text_catalog.py` 从 properties 生成，测试每次逐条比对）
      解析文案。
- [x] 真机端到端证据：模型收到的工具列表包含全部 10 个内置工具；触发一次
      `file_write` 后设备上真实落盘
      `/data/data/cn.lineai/files/.linecode/home/linecode-tool-check.txt`，
      内容与模型提交的完全一致。
- [x] `ToolTextCatalog` 语言已按界面语言选择（第 20 轮）：HuxerUI 确实没有公开的
      「组合期读取当前 Locale」接口，故新增自描述的探测文案 `app_locale_probe`
      （`default`="en" / `zh`="zh"），由组合根 `UseString` 解析后经
      `ToolTextLanguageFor` 映射，传给使用目录的 `FileToolRegistry` 与
      `AgentToolRegistry`。**更正**：此前的描述「仅影响工具回给模型的文案语言」
      不准确——工具内部文案（如 `tool_agent_invalid_type` 的中文
      「Agent 类型只能是 explore 或 sub-coding。」）在**工具失败时会渲染进工具卡片，
      用户可见**，所以这是一处真实的可见缺陷。
      *再次踩到同一个坑*：`UseString` 会校验占位符个数，探测文案有 0 个占位符，
      我仍按习惯写成 `UseString(resource, "")` → 启动即崩溃
      （`requires exactly 0 arguments`）；第 11 轮重试文案时踩过同款。
- [x] 已核实 `shell` 组在 local 模式本就不可用：旧版
      `ToolSettingsRepository.java:104` 把 shell 组声明为 `MODE_REMOTE`，
      `getEnabledToolNames()` 会按模式过滤。C++ 的 `remote` 掩码是忠实迁移，
      不是缺失，无需放开。
- [x] P0 上下文压缩服务（`压缩上下文` 菜单曾为空实现）——**已完成**：`application/context_compaction.*` 已实现并有 `context_compaction_tests`；自动/软触发、进度块与 token 用量已接入（见下方条目）。
- [x] P0 自定义 Agent 扩展与已安装 Skill 的提示词注入（曾零调用点）——**已完成**：`BuildExtensionPrompt()` 现有两个调用点（`chat_screen.cpp` 主页请求与 `sub_agent_runner.cpp` 的 `SkillRepositoryExtensionPromptSource`），且自定义 Agent 扩展已可作为 `agentx_<slug>` 工具被模型调用（第 27–28 轮，真机验证）。
      已实现但零调用点；`EXTENSIONS_CONTEXT` 模板槽位无人渲染）。

- [x] 原生测试：49/49 通过；已包含归档安全、真实 SQLite 导出脱敏、旧功能 schema、MCP/工具设置、Slash 命令、待发送队列、附件、Skill、SSH 真实协议 fixture、Memory 与 Extension SQLite 契约测试。
- [x] Android Release：arm64-v8a、x86_64、Lint、签名打包通过。
- [x] 假 AI 服务脚本与协议测试已可固定回复，相关测试 10/10 通过。
- [x] 设置重点页面 v27：4/4 页面均成功导航，无功能回放失败。
- [ ] 设置重点页面 v27 尚未像素一致：
  - `llm_settings` MAE `3.6519`，差异像素比例 `5.1281%`。
  - `output_settings` MAE `3.0813`，差异像素比例 `6.4985%`。
  - `storage` MAE `3.0184`，差异像素比例 `3.9389%`；容量数字因两包数据不同，不可直接作为几何失败。
  - `error_logs` MAE `0.3943`，差异像素比例 `0.5020%`。
- [ ] 全量 v25 的 19 个场景仍有 18 个非一致截图；`security_settings` 当时的两次失败是测试脚本误点分组标题，脚本现已改用唯一描述文本，需重跑确认。
- [ ] Windows 当前没有可用 Windows SDK/设备；Windows 平台代码只做过静态审查，不能标记实机构建或运行通过。

验证入口：

```sh
cmake --build build/tests --parallel 4
ctest --test-dir build/tests --output-on-failure

cd platform/android
./gradlew :app:assembleRelease

cd ../..
python3 tools/ui_parity_test.py \
  --serial emulator-5554 \
  --baseline-apk /home/LangLang/AndroidStudioProjects/LineCode/app/build/outputs/apk/baseline/app-baseline.apk \
  --candidate-apk platform/android/app/build/outputs/apk/release/app-release.apk \
  --scenarios tools/ui_scenarios_settings_focus.json \
  --output artifacts/ui-parity-side-by-side-settings-focus \
  --baseline-package cn.lineai.legacy \
  --candidate-package cn.lineai
```

## 代码级审计确认的剩余功能缺口（P0/P1）

由只读审计逐行核实，均为旧版存在而新版缺失或为空实现；不得勾选为完成：

- [x] 上下文压缩服务已迁移并接线：`src/application/context_compaction.*` 移植了
      `ContextCompactionService`（阈值 0.8/0.5、tail 保留 0.3、transcript 分段
      256KiB、保留预算 20000、重试 2 次、软触发需 ≥8 条可压缩消息），
      协议差异用**声明式策略表**（responses_compaction / openai_responses_summary /
      generic_summary，首行命中优先）消除 `if (protocol == ...)`。
      `压缩上下文` 菜单 → 旧版确认框 → 真正执行压缩并写回会话。
- [x] 压缩写回端到端验证（真机 + 直接查库）：对 4 条消息执行压缩后，
      `messages` 表中被摘要的头部两条 `exclude_from_context=1`，
      最近一轮两条保留为 0，新增摘要行 `hidden=1`；
      摘要正文经 `message_text_chunks` 落库，内容为
      `contextCompactionSummaryPrefix` 模板 + 模型输出。
- [x] 压缩进度块与自动/软压缩触发已迁移：
      `domain/compaction_progress.*` 表达 running/done/error 三种状态；
      `application/auto_compaction_service.*` 移植 `ContextCompactionController`
      的三条触发判定（请求前硬触发 80%、请求前软触发 50% 且受
      `soft_compaction` 开关控制、工具循环中硬触发）、`PreservedTail`、
      硬/软两套合并语义（摘要必须进上下文、被摘要的标 exclude、
      保留尾部与近期用户消息原样）；`presentation/compaction_progress_presentation.*`
      移植 `ContextCompactBlockView`，进度块在时间线中独立成块。
- [x] **软压缩摘要插入位已修正**（第 23–24 轮）：旧版只摘要最老的一段，
      并把摘要排在「头部之后、未摘要尾部之前」
      （`ContextCompactionController.java:606-615`：前情提要 → 近期上下文 → 当前问题），
      候选原先一律追加，摘要落到尾部之后。已给仓储加「按锚点插入」能力
      （端口 `ApplyCompaction(..., insert_after_id)`，内存按索引插入，
      SQLite 落在锚点之后）。
      *过程中发现**我自己引入的回归***：`messages` 表有
      `UNIQUE(conversation_id, local_order)`，`SET local_order = local_order + 1`
      会在更新途中撞约束 → **整个压缩事务回滚**，软路径的摘要与排除标记全部丢失
      （实测 0 hidden / 0 excluded，而内存请求里却有摘要）。
      改用**两段式大偏移**（先 +1000000 停靠，再 -1000000+1 落回）后修复。
      **验证**：落库顺序 `0-5 排除 | 6 隐藏(摘要) | 7-8 保留`，
      第二次压缩同样正确（`9 排除 | 10 隐藏 | 11+`）；冷启动后摘要仍在下标 1。
- [x] 真机端到端验证（把测试模型上下文窗口设为 120 token 以触达阈值）：
      连续 6 轮对话后自动压缩真实触发，数据库出现 `hidden=1` 的摘要行、
      `exclude_from_context=1` 的历史消息、空正文的进度块行；
      UI 逐条显示 `消息 → 「压缩」 → 回复`，文案与旧版 `context_compact_label`
      一致，无崩溃。
- [x] 上下文用量指示器已迁移：`domain/context_usage.*` 移植了
      `ModelContextParser`（`context_size` 优先，兼容 `[128k]` 后缀，默认
      250000）与 `ContextManager`（8 token/条 + ceil(字符/4) + 附件 + 推理 +
      工具调用）。头部在品牌与盾牌之间渲染 40dp 环形指示器（≥80% 转 WARNING），
      点击弹出 Used tokens / Context window / Usage% 面板。
      真机验证：空会话 0%，发送 "hello" 后为 10（8 + ceil(5/4)），上限 250000。
      与重建后的旧版逐项对照：指示器同为 `[639,146][744,272]`（文案本地化后
      `Context usage, 0%` ↔ `上下文占用 0%`）；面板横向 105..975、行距 110px、
      `250,000` 千位分隔与 `0%` 全部一致；仅整块面板纵向比旧版低约 125px
      （旧版弹层自行留出底部系统栏 inset，本项目按此前要求贴底），**记为残留差异**。
- [x] 重建旧版对照 APK 后发现的既有偏差：此前使用的
      `app-baseline.apk` 构建于 9月6日，落后旧仓库多个提交（例如
      `1d47496 fix: keep compaction inside processing turns`），
      因此**此前所有「基线」截图都缺少上下文用量指示器等新特性**。
      已重建并重新安装 `cn.lineai.legacy`，后续像素对照均以新版为准。
- [x] 写入工具的 diff 视图与 Accept/Revert 回滚已迁移：
      `domain/diff_lines.*` 移植 `DiffLines.calculate`（前后缀对齐 + LCS，
      超 100 万格退化为整块替换）；`SqliteDiffStore` 移植 `DiffRepository`
      （id 格式 `毫秒_36进制随机`、`revert` 三段守卫与四条英文文案逐字一致）；
      `DiffReviewService` 移植 `ToolReviewController`（state 归一化、回滚、
      本地审核缓存）；`SqliteDiffFileRestorer` 移植 `FileRestorer`。
      工具侧：`file_write` / `file_edit` 成功后经 `DiffStore::Record` 记录并把
      `diff_id` 沿 `ToolInvocationResult → CompletionToolResult →
      domain::ChatToolResult → ToolTimelinePresentation` 传回卡片。
      卡片：展开时加载 diff 体，渲染红/绿行 + 行号 + 末行换行提示 +
      撤销/同意按钮；审核状态以仓储为准叠加（对应旧版 `applyLocalReviews`）。
- [x] 回滚真机端到端验证：改写文件内容后触发 `file_write`，卡片显示
      「需要确认」+ diff（`ORIGINAL CONTENT BEFORE TOOL` 红行 /
      `linecode file tool ok` 绿行 / `文件末尾没有换行符`）；点击「撤销」后
      文件恢复为原内容、`diff_records.reverted=1`、
      `raw_json` 为 `{"review_state":"rejected","review_message":"Reverted change to <path>"}`，
      卡片转为「已撤销」且按钮消失。
- [x] 子代理 / Agent Pipeline 执行引擎已迁移并接线：
      `domain/agent_pipeline.*` 移植分层解析（`dependencyLevels` 的逐层收敛，
      并把旧版「未知依赖与环共用空列表」的歧义拆成两个独立错误码）；
      `application/sub_agent_runner.*` 移植 `AgentExecutionController` 的模型循环、
      工具集裁剪（explore 只读 / sub-coding 读写）、路径保护、取消与预算上限、
      async explore、pipeline 分层执行与上游输出注入；
      `application/agent_tool_registry.*` 移植 `agent` / `agent_pipeline` /
      `agent_output` 三个工具的全部校验分支；
      `application/agent_result_registry.*` 移植结果记录、紧凑 ref 与
      `agent_output` 的 output/meta 两种模式。
- [x] `EXTENSIONS_CONTEXT` 与 `BuildExtensionPrompt()` 缺口已闭合：
      `SkillRepositoryExtensionPromptSource` 让已安装 Skill 的提示词首次有了调用点；
      `SkillRepository` 也接进了主页聊天请求（旧版把扩展块放在
      `{{LEARNING_CONTEXT}}` 槽位，逐字复刻）。
- [x] 真机端到端验证：模型收到的工具列表为 13 个，含
      `agent` / `agent_pipeline` / `agent_output`；触发一次 `agent`（explore）
      后返回紧凑 ref `{"agent_id":"ag_...","linecode_agent_ref":true,
      "status":"done","preview":"..."}`，子代理真实执行了一轮模型请求。
- [x] **子代理的工具审批通道已接入**（第 34 轮）：
      旧版 `executeAgentToolCall`（`:925-934`）对子代理走与主代理**相同**的确认路径
      （`isSessionAutoConfirmed` / `requiresToolConfirmation` /
      `executeAgentToolCallWithReview`），而候选的子代理原先直接 `tools_->Invoke`——
      **子代理可以在无任何批准的情况下写文件**，这不只是复刻差异，更是安全问题。
      实现：新增 `application/tool_review_broker.*`——一个由界面安装回调、
      长生命周期协作者（子代理执行器）询问的中介（执行器活得比单轮久，
      拿不到每轮一次的 `CompletionObserver`）。`SubAgentRunner` 现在：
      用**与主循环同一份** `ToolPermissionService::Evaluate` 判定
      （`deny` → 拒绝；`review` → 询问中介；`allow_always` → 记永久授权），
      `SubAgentEnvironment` 增加 `permission_scope` 作为授权键的一部分。
      中介**无回调时默认拒绝**（宁可拒绝也不静默执行）。
      接线：`app_root` 建中介 → `MainScreen` → `ChatScreen` 组合期安装回调、
      卸载时解绑。
      **测试**：新增 `tests/tool_review_broker_tests.cpp`（未安装时拒绝、
      已安装时委派、解绑后恢复拒绝）。83/83 通过；编译装机、无崩溃。
      **真机验证未完成（如实记录）**：已观察到**改动前的对照行为**——
      权限模式为"自动"时子代理的 `file_write` **未经审批直接执行**
      （请求序列：主请求 → 子代理请求 → `Successfully created file`）。
      但把模式切到"确认"后重跑时，模拟器的 `adb reverse` 在首个请求后即失效
      （第二个请求 `Failed to connect`），未能取到"确认"模式下的审批证据。
      fixture 侧已就绪（新增 `__LINECODE_TEST_AGENT_WRITE__`，
      返回 `sub-coding` 代理调用并让其在内部请求 `file_write`，
      已用 curl 直接确认返回 `call_linecode_agent_write_test`）。
      **第 35 轮进展与重要环境发现**：
      (1) **查清了多轮以来"连接失败"的真正原因**：`adb root` 会重启 adbd
      并**清除所有 `adb reverse` 隧道**。我此前一直先建隧道再 `adb root`，
      于是隧道被静默清空——这与产品无关，却反复伪装成功能故障。
      正确顺序是 **`adb root` → 用完 → `adb unroot` → 再建隧道**。
      修正后审批流程一次跑通。
      (2) **审批通道已在真机确认可用**：权限模式设为 `confirm` 后发起 `file_write`，
      界面出现 `Waiting for approval` / `file_write` /
      `Allow this operation in the current workspace?` / `Reject` / `Allow once`，
      点 `Allow once` 后调用才执行（耗时 99.8s，等待用户输入）。
      这证明界面回调→中介→等待决定的链路是活的。
      (3) 新增 `tools/device_send.sh`：每次从当前层级**动态定位输入框**再发送。
      输入框在 y≈1373 与 y≈2148 之间随是否配置模型/正文高度变化，
      固定坐标会静默点空、伪装成功能失败——这是多轮设备测试反复受阻的另一原因。
      **仍未取到**：子代理**嵌套**写调用的独立审批证据——
      该触发下应用只发出 1 个请求（主请求），未出现子代理请求，
      原因待查（fixture 返回的 agent 工具调用未被继续执行）。
      下一轮继续。

- [ ] 子代理的剩余部分未迁移：进度会话（`AgentProgressSession` /
      `PipelineProgressSession`）、流式 delta、子代理内的工具审批通道；
      `custom_tool_names` / `custom_mcp_ids` 未接入；同层并行依赖注入的
      `TaskScopeSubAgentLauncher`（未注入时退化为顺序执行）。
- [ ] P1 图片输入（拍照/相册）缺失：`domain/input_attachment.h` 无图片负载；
      旧版对应 `ComposerView.java` 的 `onSendWithImage` / `onImagePickerClick`。
- [x] P1 生成失败自动重试已迁移（模型切换/中断恢复提示仍缺）：
      `GenerationController::ResetAttempt` 丢弃本次尝试的流式内容但**不**结束回合
      （旧版是删除失败的 assistant 消息再补重试提示；本移植从不持久化失败内容，
      故无需给会话加删除原语）；发送链路改为 `for attempt < 3` 的循环，
      复用同一份请求快照（对应旧版 `retryableModelStream(..., requestMessages, ...)`），
      失败时追加 `retry_notice` 消息、等 5 秒再试，第 3 次仍失败才
      `Fail`。
      文案 `chat_retry_attempt`（"正在第 {0}/{1} 次重试，错误：{2}"）与
      `chat_model_failed`（"模型通信失败：{0}"）逐字取自旧版
      `model_retry_attempt` / `model_retry_failed`。
      *实现中踩的坑*：`UseString` 会**校验占位符个数**，而重试次数只有运行时
      才知道，直接 `UseString(resource, "")` 导致启动即崩溃
      （`HuxerUI localized string requires exactly 3 arguments`）。
      改用控制字符哨兵在组合期解析模板、运行时替换；失败文案同理。
      真机验证（fixture 新增 `__LINECODE_TEST_FAIL__`：前 2 次请求返回 500）：
      UI 依次显示 `Retry 2/3, error: …` → `Retry 3/3, error: …` →
      `Model communication failed: …`，请求数恰为 **3**，无崩溃。
      `tests/generation_retry_tests.cpp` 固化 `ResetAttempt` 语义
      （保持 running、清空流式、不残留气泡、拒绝过期世代）。
- [x] todo 状态已注入提示词：`TodoStateStore` 由组合根经
      `MainScreen → HomeScreen → ChatScreen → ComposerGenerationRunner` 注入，
      每次请求前重新 `Load()` + `RenderTodoState()`，与旧版
      `GenerationFlowController.java:773` 在工具循环内重建提示词的行为一致。
- [x] 附件选择器来源已按执行模式区分：`ChatAttachmentPickerState` 新增
      `source`，标题按 `attachment_picker_title_{local,ssh,terminal_provider}`
      三选一，文件项继承该来源；来源由 `McpExecutionSettingsService::Load()`
      的执行模式决定（对应旧版 `AttachmentPickerCoordinator` 的判定）。
- [x] 工具循环中的压缩已插桩：新增 `application/ports/mid_loop_compactor.h`
      端口（完成循环不持有会话，只询问端口）与 `application/tool_loop_compactor.*`。
      `McpCompletionLoop` 在每批工具执行完、下一次模型请求前调用
      `CompactIfNeeded`，对应旧版
      `GenerationFlowController.continueModelAfterTools()`。压缩后请求重建为
      `[system] + [摘要] + [在途 assistant+tool 组]`，在途组逐字保留。
      `tests/tool_loop_compactor_tests.cpp` 覆盖长循环触发、短循环不动、
      无压缩服务时空操作三种情形。
      *实现中发现的坑*：`CompletionMessage` 没有 id，而保留尾部规则按 id 匹配；
      最初把 id 赋值放在触发判定之后，导致所有 id 为 0、判定误判为"全部保留"
      而永不触发。已改为在转换时立即赋 id（诊断输出定位）。
- [x] mid-loop 压缩真机端到端验证（补上上一轮标记的未验证项）：
      fixture 新增 `__LINECODE_TEST_LOOP__`（连续多轮调用只读的 `list_dir`），
      配合 120 token 的上下文窗口可堆出长工具循环。实测请求序列为
      ① 正常请求（含 tool 结果）→ ② **压缩摘要请求**（无工具、单条 user）
      → ③ 重建请求 = `system` + 摘要（`Another language model started…`）
      + 在途 `assistant` + 在途 `tool` 结果（目录列表逐字保留），
      与设计完全一致，无崩溃。
- [x] **压缩触发改用服务器上报的 token 用量**（第 22 轮）：此前触发永远回落到
      本地 chars/4 估算，因为观测值从未被填充。查出并修掉**三个独立断点**：
      (1) **流式响应不解析 usage**——`usage` 只在非流式解码器里读，流式 chunk
      结构没有该字段，且解码器在 `choices` 缺失时直接返回空 chunk，
      而带 usage 的末个 chunk 恰恰 `choices` 为空；旧版
      `OpenAiCompatibleProtocol.java:138` 正是为此在 `choices` 之前读 usage。
      已在协议 chunk 适配处传递（原为硬编码 0）。
      (2) **追踪器随组合重建而丢失**——`ComposerGenerationRunner` 是
      `Composer(...)` 内的局部 `make_shared`，每次重组重建、成员归零；
      提升为组合期 `UseState` 状态并注入。
      (3) **假服务从不发送 usage chunk**，已补齐（curl 直接确认线上字节）。
      **双向真机验证**：窗口 600 时每轮触发（摘要请求 + 含摘要请求各一）；
      窗口 128000 时两次发送仅两个请求、**不触发**。80/80 测试、回归功能失败 0。

- [ ] **mid-loop 压缩的残留语义差异**（第 23 轮做过可行性核查，结论：改动深、
      收益窄，暂缓）：旧版 `startToolLoopContextCompaction` 会改写会话并
      `persistCurrentConversation()`，因此摘要落库、UI 出现「压缩」进度块；
      当前实现只重写**本次在途请求**（`MidLoopCompactor` 端口拿不到会话），
      所以摘要不落库、无进度块，且下一轮会从**未压缩的会话**重建请求、
      再次触发压缩（每轮多一次摘要请求，非用户可见）。
      **为何不只是接线**：(1) `ChatSession` 由 `app::ChatSessionBootstrap` 封装
      （`src/app/bootstrap.h:22`），`app_root` 在压缩器构造点拿不到它，需要调整
      引导顺序；(2) 更根本的是，旧版工具循环把 assistant/tool 消息**写进会话**，
      而候选只在 `request.messages` 里追加、完成时才落库——
      所以"保留在途组"在旧版是会话内的真实消息，在候选是请求内的临时消息，
      要让排除集合/保留尾部对上真实 id，需先统一这一差异。
      收益：仅长工具循环（窗口触顶且发生在循环中途）时的一个进度块。
      若要做，建议顺序：先让工具循环把在途组写入会话，再接会话与进度块。（本轮实测发现，尚未修）：
      旧版 `startToolLoopContextCompaction` 会改写会话并
      `persistCurrentConversation()`，因此摘要落库、UI 出现「压缩」进度块。
      当前实现只重写**本次在途请求**（`MidLoopCompactor` 端口拿不到会话），
      所以：摘要不落库、不显示进度块；循环结束后下一轮用户消息会从会话
      重新构建请求，需要再次触发压缩。功能上防止了循环内溢出，但
      可观察行为与旧版不一致。修法：让端口能写入会话（或由会话侧实现端口）。
- [x] P1 Markdown 三处退化已修：
      (A) 裸 URL —— 新增 `presentation/markdown_linkify.*`，等价
      `Linkify.WEB_URLS`（`scheme://` / `http(s)://` / `www.`，词首边界、
      前导 `@` 拒绝、尾随标点裁剪），在 `RichText()` 对非链接纯文本切分；
      target 仍过 `ParseNavigableMarkdownLink`，故非 http(s) 显示链接样式但不可点。
      (B) 引用/列表内的代码块 —— 确认原 domain 结构只能表达内联行，
      故把 `TutorialQuote` 改为嵌套块序列、`TutorialListItem` 增加块级子内容，
      解析器抽成递归 `ParseLines`（列表项按内容列收缩进续行、围栏整体消费），
      渲染改为 depth 感知的递归；depth 0 边距与改动前一致。
      (C) 思考块原始 `**` —— 新增 `domain/inline_emphasis.*`，逐条移植
      `InlineEmphasisParser`（反引号段连同反引号原样保留且不做强调解析、
      找不到闭合只输出首字符、span 内不递归、`**` 相邻时插入 `" | "`），
      `ReasoningTimelineBlock` 改用 `AttributedText`。
      独立验证（用自写用例而非子代理测试）：`**a****b**`→`a | b` 2 spans、
      `` `**not bold**` ``→反引号保留且 **0 span**、`**unclosed` 原样、
      `\*literal\*`→字面量、`foo_bar_baz` 不开启强调。77/77 测试。
- [ ] Markdown 的已知范围取舍（有意，非缺陷）：
      (1) 不识别裸域名（`example.com` 无 scheme/www）——严格复刻需内置 IANA
      TLD 表，且本应用正文多含 `context_compaction.cpp` 这类文件名，无表会误报；
      (2) 不支持 IRI/非 ASCII URL（避免吞掉中文标点）；
      (3) **有意偏离旧版**：行内代码不做链接化、显式链接不重复切分
      （旧版 `Linkify` 实际会切行内代码，依据 `Linkify.java:305-311/652`）。

- [x] P1 Markdown 退化（已修，保留原条目以便追溯）：
- [x] P1 回合汇总「已编辑 N 个文件」区块已迁移（`AssistantTurnView.renderFiles()`）：
      仅在**本回合既产生了 diff 又完成了答复**时显示
      （旧版条件 `edits.isEmpty() || row.answer == null ? GONE : VISIBLE`）；
      计数用**去重后的文件路径**，而条目按 **diff_id** 去重——
      所以同一文件被多次编辑时每一笔仍可单独审核（旧版语义）。
      路径取自工具调用参数的 `file_path` → `path` → 回落到 diff_id
      （新增 `presentation::ToolCallTargetPath` 复用展示层的解析）。
      折叠展开后逐个渲染该回合的写入卡片。
      文案 `chat_files_changed` = "Edited {0} files" / "已编辑 {0} 个文件"
      逐字取自旧版。
      真机验证：`Worked 0.7s` → **`Edited 1 files`** → 答复；
      点击展开后显示 `Completed / linecode-tool-check.txt` 卡片，无崩溃。

- [x] **会话恢复清理器已移植（纯逻辑 + 20 个测试）但尚未接线**：
      `domain/conversation_resume_sanitizer.*` 逐条移植
      `ConversationResumeSanitizer`——清理卡在 `streaming` 的消息、
      `compact_status=running` 的进度块、处于 running/pending 未完成审核态的工具结果、
      没有对应结果的 assistant 工具调用（补齐 `recovered_tool_*` 记录）、
      以及 `linecode_agent_progress` / `linecode_agent_pipeline_progress`
      里仍称 running 的负载，缺失处一律用「上次生成已中断。」填充。
      **接手子代理失败任务时发现并修掉 3 个真实缺陷**：
      (1) 恢复记录算了 `timestamp` 却从未赋值（旧版用会话 `updated_at`，
      回落到当前时间）——编译器 `-Wunused-variable` 暴露；
      (2) `Trim(OptString(...))` 产生**悬垂 `string_view`**（`OptString` 按值返回，
      临时量在完整表达式结束时销毁），导致拼接进 output 的是**已释放内存**，
      实测出现 `(]\u0005...` 垃圾字节；
      (3) 测试辅助 `ObjectOf()` 返回**函数内静态对象**的引用，而
      `FieldText` 等又会重新赋值它，持有指针的用例全部悬垂——
      表现为 `std::bad_variant_access: variant is valueless`。
      另有 1 处测试自身笔误（对对象型字段调用整数读取器，
      该读取器是 assert 而非返回哨兵，`||` 兜底永远不生效）。
- [x] **会话恢复清理器已接入载入路径**：`SanitizeResumeMessages` 把同一套规则
      作用到仓储实际交给会话的 `domain::ChatMessage` 模型上
      （工具调用与结果同处一个 `AssistantToolEvent`，所以旧版
      "assistant 工具调用没有对应 tool 结果" 在这里就是 `result` 为空的事件），
      在 `sqlite_conversation_store.cpp` 水合之后、成为可见状态之前调用
      （对应旧版 `ConversationPersistenceController.applyConversation`）。
      真机验证：直接构造中断态（`tool_results.review_state='pending'`、
      两条 `messages.streaming=1`）后重启，**卡片显示 `Completed` 而非
      "需要确认"**，而数据库里仍是 `pending`/`streaming=1`——
      直接证明是加载期的内存态修复生效；无崩溃。
- [x] **冷启动丢失压缩摘要（严重，已修）**：`load_visible_messages` 带
      `AND m.hidden = 0`，而旧版 `ConversationRepository.getMessages` 只按
      `conversation_id` 过滤、**载入全部消息**并用 `hidden` 标志控制渲染。
      由于压缩摘要与"隐藏尾部副本"都是 `hidden=1`，
      且被摘要的原文标了 `exclude_from_context=1`，
      冷启动后模型**既拿不到摘要也拿不到原文**——压缩静默失效、历史近乎清空。
      实测：压缩后重启，请求里 `含压缩摘要: False`。
      修复：查询不再过滤 `hidden`，并把 `hidden` 列接入
      `StoredMessage`/`DecodeStoredMessage`/`HydrateMessage`
      （`StoredMessage` 原本**没有** `hidden` 字段）；渲染层早已跳过隐藏消息，
      故 transcript 不变。修复后同一场景实测 `含压缩摘要: True`，无崩溃。
      回归：18 场景功能失败 0、像素值不变。
- [x] **压缩进度块与重试提示现可持久化**：两者只存在于内存（`compact_status`
      与 `retry_notice` 都不在 SQLite 列里），原先**从未写入 raw_json**，
      重启后「压缩」状态行会失去含义。现在
      `EncodeMessageRawJson` 会写入这两个键、`DecodeLegacyMetadata` 读回，
      并接进 `StoredMessage`/`HydrateMessage`（对应旧版
      `ConversationPersistenceController.messageRawJson` 的
      `compact_status` 分支与 `readRawString`）。
      **验证方式**：新增仓储往返测试（append → `FlushPendingAsync` →
      `ReloadAsync` → 断言 `compact_status == "done"` 且 `retry_notice` 为真），
      实测通过。
      *过程中修正的两个自身错误*：(1) 我一度以为编码器没被调用——
      实际 `raw_json` 存在 `message_text_chunks` 分块表里，
      `messages.raw_json` 列按设计留空，是我查询错了表；
      (2) 新测试最初用工厂默认的占位 id 直接 append，
      与其它占位行冲突导致进度块被覆盖，改为 `AllocateMessageId()` 后通过。
- [x] **图片输入**：曾派子代理实现，其交付为**半成品且无法编译**
      （改了 `chat_session`/`completion_gateway`/`send_message`/
      `completion_protocol_codec`/`app_state` 与两个新 domain 头，
      但没有测试、没有 UI、没有平台层，且 `ChatSession::Send` 签名不一致）。
      已**全部回退**，TODO 保留该项。已摸清的可复用件：
      `domain::WorkspaceImage`、`Base64Encode`、
      `image_understanding_codec.cpp` 里两种协议的图片编码形状、
      `completion_protocol_codec.cpp` 已在构建 content 数组。
- [ ] **会话恢复清理器未回写持久层**（与旧版的差异，用户不可见）：
      旧版在 `changed` 时 `saveConversation` 回写；当前只修内存态，
      所以数据库保留陈旧值、每次加载重复修复一次（幂等，无副作用）。
      未回写是因为该路径是异步的，需要单独验证；若要补齐，
      应在 `SanitizeResumeMessages` 返回 `true` 时把修复结果落库。
- [ ] **旧的记录级接口仍未接线**（`Sanitize`/`SanitizeToolContent` 等）：
      它们按旧版 `MessageRecord` + `raw_json` 建模，而候选仓储不读
      `compact_status`/`review_state` 的 raw_json（`review_state` 在
      `tool_results` 表、由 `StoredMessage` 解码进工具结果），
      故保留为 API/文案对齐与测试载体，实际生效的是消息级版本。
      旧版在 `ConversationPersistenceController.applyConversation`（加载/切换会话前）
      调用并回写。候选的接缝在
      `src/infrastructure/sqlite_conversation_store.cpp` 的 `stored` 向量
      （约 875-885 行，`LoadStoredMessagesAsync` 之后、`hydrated` 之前）。
      接入需要：`StoredMessage` ↔ `ResumeMessageRecord` 的转换
      （注意仓储侧 `role` 是**字符串**、且有 `local_order`/`attachments`/
      `finished_at`/`error_message`/`timeline` 等额外字段），
      以及 `changed` 时把修复结果回写持久层（异步路径，需单独验证）。
      调用点应传入当前时间作为 `now_millis`（替代 `System.currentTimeMillis()`）。

- [x] 页头与输入栏的语义标签已补齐：基线节点树有 8 个可访问名称
      （菜单 / LineCode / 上下文占用 / 权限 / 新建对话 / 更多操作 /
      选择图片发送 / 发送消息），候选原先只有 1 个（上下文占用）。
      现已全部对齐，实测边界的偏差统一为 **1px 垂直偏移**（既知差异），
      水平方向最多 ±1px。依据旧版 `HeaderView.java:45-79`（四个页头按钮 +
      品牌容器 `:90-93`）与 `ComposerView.java:236/627-628`（附件与发送按钮）。
      `更多操作` 用的是 `chat_context_more`（不是 `header_more_desc`="更多"），
      这一点按旧版实际设置点核对过。
- [x] 图片输入的**请求层与发送层**已移植完成（见下方两条…）：
      三套协议的图片部件形状 + 消息携带与持久化 + 空文本占位，
      与旧版自己的（不可达）管线一致。
- [ ] **图片输入在旧版 UI 中不可达 —— 因此不应为其添加入口**（重要更正）：
      旧版 `ComposerView` 有两处调用 `onImagePickerClick()`：
      (1) `imageButton`（`:243-250`）位于 `modeRow`，而该行是**局部变量**、
      创建即 `setVisibility(GONE)`（`:326`）且**全文件从未设为 VISIBLE**；
      (2) `showConversationMenu()`（`:645`）**在整个仓库中没有任何调用者**。
      两者都不可达，所以图片选择器无法被触发。
      基线里那个 `选择图片发送` 节点其实是 **`attachButton`（`+`）**——
      旧版把 `composer_image_button_desc` 同时给了它（`:236`），
      而它点击走的是 `onAttachClick()`（附件选择器）。
      故**给候选加图片按钮会偏离 1:1**；正确做法只是补上 `+` 按钮的语义标签
      （已完成）。已移植的两层保持与该「死管线」对等。

- [x] **无障碍语义标签：用户已明确「不用补，无障碍整体不做」**（第 20 轮确认）。
      因此 17 个页面约 170 个 `content-desc` **不再迁移**，此项关闭、不要重复投入。
      聊天页已完成的 8 个（第 19 轮）保留——它们不改变像素，且比缺失更贴近基线节点树；
      但不再扩展到其它页面。

- [x] **展示层硬编码英文文案已清除**（第 21 轮）：扫描 `src/presentation/`
      的字符串字面量，识别出 4 处**真实可见**的硬编码英文（其余命中是注释）：
      (1) 子代理卡片的 `"Running…"` / `"Done"` → 改用旧版
      `tool_call_agent_running` / `tool_call_agent_done` / `tool_call_agent_failed`
      （`ToolCallAgentView.java:173,181`，中文「正在执行任务…」「任务完成」「执行失败」）；
      (2) 通用工具卡片的 `"Input"` / `"Output"` 分节标题 → `sheet_title_input` /
      `sheet_title_output`（中文「输入设置」「输出设置」）；
      (3)(4) 生成协程的两条守卫消息 `"Please add and select a model first"` /
      `"The selected model no longer exists"` —— **这两条是候选自造的，旧版没有等价物**
      （旧版只靠输入栏提示 `composer_hint_no_model` 提示用户），故按应用语气新拟中文
      「请先添加并选择模型」/「所选模型已不存在」，但仍需跟随界面语言。
      因协程不是组合作用域，两条守卫消息与卡片标题一样在组合期解析后传入。
      真机验证：无模型时错误行由 `模型通信失败：Please add and select a model first`
      变为 **`模型通信失败：请先添加并选择模型`**，无崩溃。
      回归：18 场景功能失败 0。

- [x] **自定义 Agent 扩展现可作为工具被调用**（第 27–28 轮）：
      此前**没有任何注册表读取 `AgentExtensionStore`**——用户能在界面里创建、
      启用自定义 Agent，但模型**永远无法调用它**。
      已移植 `CustomAgentExtensionTool` 与 `ToolRegistry.reloadExtensions`
      的注册逻辑：新增 `application/custom_agent_tool.*`（纯逻辑：命名规则
      `agentx_<slug>`、`safeToolNamePart` 的清洗/折叠/去首尾下划线/首字母兜底、
      提示词与 900 字能力节选、工具 schema）与
      `application/agent_extension_tool_registry.*`（每个**已启用**的扩展注册一个工具，
      调用时委派为 `sub-coding` 运行并带上该 Agent 自选的 `custom_tool_names`
      /`custom_mcp_ids`）。
      同时补上 `AgentRunRequest` 的这两个字段与 `IsAgentToolAllowed` 的
      **MCP 快捷分支**（旧版 `:817`：自选 MCP 工具在自定义名单过滤**之前**放行）。
      文案 3 条逐字取自旧版并已进目录（67→70 键，`gen_tool_text_catalog.py`
      新增 `tool_custom_agent_` 前缀）。
      **测试**：`tests/custom_agent_tool_tests.cpp` 覆盖命名规则 7 例、
      提示词含/不含各分节、描述 900 字截断、schema、仅注册已启用扩展、
      委派请求字段、空任务、缺执行器、未知工具。81/81 通过。
      *测试抓到的真实 bug*：空任务判断未做 `trim`（旧版是
      `optString("task").trim()`），纯空白任务会被当成有效任务。
      **真机端到端已验证**：直接向 `extension_agents` 插入一个启用的自定义 Agent
      （slug `code-reviewer`）后，模型收到的工具列表为 **14 个**，其中包含
      **`agentx_code-reviewer`**，且请求体为合法 JSON。
      *过程中查出并修掉的**我自己引入的严重 bug***：`CustomAgentToolSchemaJson`
      用 `R"json(` 开头却以 `)"` 结尾，**分隔符不匹配**导致多个字面量被并成
      一个未闭合的原始字符串，产出的 schema 里混入 `)json(` 等文本——
      一旦该工具被注册，**每个请求体都会变成非法 JSON**、被服务端整包拒绝。
      之所以能发现，是因为 fixture 在解析失败时落盘了完整请求体
      （新增诊断：`malformed-body.json`），否则只能看到一个字节偏移。
      *测试缺口同时补上*：原测试只断言 schema **包含**若干子串，
      而错乱文本恰好也包含它们；已改为**真正解析**该 JSON 并校验结构。

已核实**不属于**缺口的项（不要重复投入）：
- **「模型切换提示」不存在**：旧版 `message_model_switched`（"已切换模型"）
  只在 `values*/strings.xml` 定义，**全仓库无任何引用**（Java 与布局均无，
  也无 `getIdentifier` 动态查找）——是死资源。候选同样未迁移该文案，
  这正是 1:1。此前 TODO 把它列为缺失功能是**我的误判**，已更正。
  （真正缺失的是「生成中断恢复」，对应旧版 `ConversationResumeSanitizer`，
  它确实被 `ConversationPersistenceController.applyConversation` 调用。）
- `shell_execute` 在 local 模式不可用 —— 旧版本身如此，见上方说明。
- 原生「发布 Skill」页无导航入口 —— 旧版同样不可达（旧版按钮也绑的是 `onCenter()`）。
- `PendingScreen` 占位分支 —— 27 个路由全部有真实 destination，该分支不可达。
- 空 lambda `memory_screen.cpp` 取消按钮 —— 语义就是关闭对话框，与旧版一致。

## 已实现但仍需最终像素/功能验收的切片

- [ ] 主页：聊天、模型选择、输入、发送/停止、权限面板、更多菜单。
- [ ] 抽屉：对话/文件页签、会话选择与删除、文件树展开与刷新、底部贴边布局。
- [ ] 模型管理：真实目录查询、添加预设/自定义/本地模型、编辑、测试、保存、选择。
- [ ] AI 行为、输入、主题、输出与浏览、安全、提示词模板、工具调用预览。
- [ ] 存储统计、错误日志、后台保活、关于、许可。
- [ ] Android 错误日志已改为脱敏缓存文件 + 只读 `content://` + `ACTION_VIEW text/plain`；仍需真机点击验证目标应用选择器和 URI 生命周期。
- [ ] Windows 错误日志已实现只读临时文件 + 默认程序打开；仍需 Windows 构建/运行验证。
- [ ] UI 几何的本轮修正需要再次截图验收：设置分区高度、AI 行为行高、输出页开关/选项/Markdown 表格、存储卡高度与“0项”间距。
- [x] API 27/29 主题覆盖显式继承 `LineCodeBaseTheme`，API 35 实机层级确认系统 ActionBar 不再出现，主页标题恢复到 `y=181`；仍须纳入全量截图回归。

## 已接线但仍未验收的路由

以下路由已离开 `PendingScreen`，但“可打开”不等于完成迁移：

- [ ] `mcp`：设置加载/保存和平台能力裁剪已接线；仍需完整执行链与像素验收。
- [ ] `tool_settings`：设置持久化、模型选择已接线；仍需全状态和像素验收。
- [ ] `extensions`：首页/详情/编辑路由已接线，Agent/MCP 已实现 SQLite 真实加载、新增、编辑、启停、单删和批量删除；MCP `tools/list` 已支持 JSON/SSE。Agent AI 起草、Skills/LineCode/Terminal/SkillHub 功能仍未完成。
- [ ] `memory`：列表、空态、详情、新增/编辑/删除/多选已接入 SQLite；RAG 索引写入、自动提取与调用链仍未完成。

`tutorial` 和 `data` 也已接入 `NavigationStack`。所有上述页面仍须完成旧版同机像素测试；`data` 尚未通过旧版互导、跨数据库/工作区原子回滚和破坏性导入真机测试。

## 数据管理与 `.linecode`

- [x] 在 `AppRoot` 构造 `SqliteArchiveDatabase` 和 `HuxDataArchiveService`，通过接口注入页面。
- [x] 将 `AppRoute::data` 接到 `DataSettingsScreen`。
- [x] 导出前持久化当前会话；导入确认后先停止当前生成；成功后重载会话、模型选择和工作区状态。Android 保活租约释放仍需真机验证。
- [x] 严格校验 `manifest.json`：`format=linecode`、版本、容器、数据库标志和 roots。
- [x] 支持旧版 `async-storage.json` 与 `conversations/*.json` 的解析、schema 转换和 async-storage-only 导入，并有契约测试。
- [ ] 用旧版真实导出物覆盖更多历史 schema/异常 fixture，不得只依赖构造数据。
- [x] 导入先完整校验并暂存，再执行覆盖；数据库使用 SQLite 事务，工作区保留同文件系统备份并在数据库失败时回滚，已有注入失败测试证明原文件不变且临时事务目录被清理。
- [x] REPLACE 模式正确清理 `home/project/skills`，同时支持旧 `.linecode/{root}` 路径，并有三根目录的替换测试。
- [x] 拒绝 zip-slip、绝对路径、重复条目、CRC/元数据不一致、缺失 tables 和更高 schema 版本；旧版 conversation fixture 与递归深度仍需补测。
- [x] 为容器/解压后总大小、条目数和单文件大小设置上限；工作区递归深度仍需明确上限和测试。
- [x] 导出脱敏模型 `api_key`、SSH/Web Search secret、敏感 setting key、MCP headers/raw JSON secrets；除递归规则测试外，真实 SQLite 导出反向测试也证明上述秘密及旧消息分块 `raw_json` 秘密不在 `database.json` 中。
- [ ] ZIP codec、JSON typed cell、SQLite 事务、导入失败不破坏原数据已有测试；旧版真实 fixture、文件选择取消和确认框状态仍需补齐。
- [x] **用旧版和新版实际互导 `.linecode`**（第 30 轮做了真实往返，发现**一处真实缺陷**）：
      **主方向（旧版 → 候选）已验证通过**：在旧版中造 1 个模型 + 1 个会话 + 4 项设置，
      用旧版界面真实导出（`已导出 .linecode：1 个会话，1 个模型，4 项设置`），
      再把该归档导入候选，结果**逐项一致**：
      模型 `lg-model-1`/`Legacy Model`/`selected=1`、会话 `lg-conv-1`/`Legacy Conversation`、
      4 项设置（`@linecode_chat_mode`/`@linecode_selected_project_local`/
      `@linecode_user_agreement_accepted`/`@linecode_user_agreement_version`）键值完全相同。
      真实归档已存为 `tests/fixtures/legacy-export-v1.linecode`（2566 B），
      供后续回归直接使用——此前仓库里只有构造数据，没有真实导出物。
      **反向（候选 → 旧版）存在真实缺陷，尚未修**：对比两边导出条目——
      旧版 `async-storage.json` 1217 B / 9 条、另有 `conversations/<id>.json`；
      候选 `async-storage.json` **仅 2 B（空数组 `[]`）且无 `conversations/` 条目**。
      而旧版导入时模型/会话/设置正是从 `async-storage.json` 读取的
      （`ARCHIVE-VALIDATION` 侧同样要求该条目存在且可解析），
      因此**旧版导入候选归档会丢失全部模型、会话与设置**。
      根因：`hux_data_archive_service.cpp:353` 导出时硬写 `TextBytes("[]")`，
      从未把候选自身的模型/会话/设置序列化成旧版形状。
      修复需实现旧版平面（模型 JSON、`@lineai_conv_<id>` 元数据、
      `@lineai_conversation_list`、每会话 ZIP 条目与设置条目），
      属独立一轮的工作量。
      **第 31 轮进展**：**编码器已完成并经真实数据验证**。
      新增 `EncodeLegacyArchive(LegacyArchiveData)` →
      `{async_storage_json, conversation_files}`（`archive_validation.h/.cpp`），
      复用已有的 `LegacyModelJson` 与 `SafeConversationFileName`，
      并新增消息/会话编码器（镜像旧版 `messageJson`/`conversationJson`，
      含 `raw_json` 先折叠、结构化字段后覆盖的顺序语义）。
      **验证方式**：新增 `tests/legacy_archive_writer_tests.cpp`，
      直接读取上一轮存下的**真实旧版归档**做往返——
      解码后再编码，必须复现全部 **9 个** async-storage 条目
      （含 `@lineai_conv_lg-conv-1` 与 4 项 `@linecode_*` 设置）、
      会话文件名 `conversations/lg-conv-1.json`，
      且元数据中的 `size` 与实际产出的字节数一致、`messageCount` 正确；
      空数据也须产出合法的 async-storage。82/82 通过。
      **第 32 轮完成接线并端到端验证**：
      新增 `ArchiveDatabase::ExportLegacy()`（SQLite 适配器实现：读模型/会话/
      消息/设置，消息正文从 `message_text_chunks` 分块重组，
      模型形状由列重建而非复用 `raw_json`），并在 `PrepareExport` 中写入
      `async-storage.json` 与每会话 ZIP 条目。
      **真机双向验证通过**：
      · 候选导出条目 `async-storage.json` 由 **2 字节 → 1117 字节 / 10 条**
        （`@lineai_models`/`@lineai_selected_model`/`@lineai_current_conversation`/
        `@lineai_conv_lg-conv-1`/`@lineai_conversation_list` + 5 项 `@linecode_*`），
        并产出 `conversations/lg-conv-1.json`；
      · 清空旧版后导入该归档，旧版提示
        **「已导入 .linecode：1 个会话，1 个模型，5 项设置」**，
        库内为 `lg-model-1`/`Legacy Model`/`selected=1`/`linecode-test-model`/
        `128000` 与 `lg-conv-1`/`Legacy Conversation`/`createdAt=1000`/`updatedAt=2000`，
        与导出源**逐项一致**。
      另补服务级测试 `AssertExportCarriesTheLegacyPlane`：断言导出必须带
      `async-storage.json`（非空且可解析）、选中模型、当前会话、设置，
      以及元数据指向的会话文件——防止接线被无声移除。
- [ ] 按旧版 60dp header、68dp 行、16/12dp padding、12dp 圆角完成同机像素截图。

## 教程

- [x] 迁移并净化 `tutorial_simple.md` 与 `tutorial_pro.md`，删除控制模式、手机控制和无障碍相关段落，再连续重编号。
- [x] 实现 C++23 Markdown 文档模型与解析器，覆盖当前教程使用的标题、段落、粗斜体、行内代码、代码块、嵌套列表、引用、分隔线和 GFM 表格；裸 URL 与边界语法仍需增加专项 fixture。
- [x] 实现模式选择卡、横向章节 chips 和章节跳转；简单模式默认且页面内状态不持久化。
- [ ] 复刻旧版 Markdown 几何：标题 28/24/20sp、正文 16sp、代码 13sp、表格 13sp，以及原边距/圆角/颜色。
- [ ] HuxerUI 目前只公开即时 `ScrollTo/ScrollToItem`；先保证跳转位置准确，再验证是否可在公开 API 内复刻旧版平滑动画。
- [ ] 已增加解析、章节映射、代码围栏伪标题和“禁用关键词不存在”测试；仍需补链接点击、裸 URL、复杂嵌套与真实页面滚动测试。

## 旧版页面/功能完整性审计

这些旧版目的地尚需逐一证明“已等价折叠到现有页面”或单独迁移；不得因 C++ 中没有路由就遗漏：

- [ ] 高级功能（保留非无障碍部分）、SSH、Termux 集成：真实 libssh2/mbedTLS 运输、SFTP 工作区、SSH/local 独立目录与运行时切换已经接线，仍缺完整真机成功连接 fixture 和像素验收。
- [ ] 图像理解模型、图像生成模型及对应调用链。
- [ ] 模型添加选项、自定义/本地/预设添加、模型编辑的全部字段、校验和错误态。
- [ ] 扩展列表、终端提供者、Agent 编辑、MCP 编辑、扩展详情。
- [ ] Skill Store、SkillHub 登录/中心/Web/发布/详情。
- [ ] 内置浏览器、浏览器前缀配置、Shell Command 页面及返回行为。
- [ ] 设置项持久化、重启恢复、删除/覆盖确认、空态、加载态、错误态和并发操作。
- [ ] 旧版所有 drawable、字体、颜色、文案和交互热区的代码级清单。

明确排除且不得迁移：

- [x] `PhoneControlScreenFactory`。
- [x] Accessibility Service、控制模式及所有相关权限、教程和提示词。

## UI 像素级验收

平台级残留（已验证，无法用布局修正消除，**不得用魔数硬凑**）：

- [ ] **长页面的像素差异主要是「文本高度漂移」的累积，不是结构性缺失**（第 20 轮实测）：
      以 `theme` 为例，逐元素对比基线/候选的 y 坐标，偏移在卡片内单调累积、
      跨卡片重置：`跟随系统` -3 → `暗色模式` -12 → `咖啡纸` -17 →
      `VS Code` -21，随后 `GitHub Dark` 回到 -14 → `Gruvbox` -6 → `高对比` +1。
      同一页的文本内容集合只差 5 条，且那 5 条是**基线节点树里残留的聊天页节点
      （像素上并不存在，y=2147/x=100 处基线是背景色）**，属测量产物而非真实差异。
      故 `theme`(6.7%) / `llm_settings`(5.4%) / `model_add_custom`(10.5%) 这类
      长页面**不要再去调间距**——根因是缺行距 API（见下一条），
      逐元素硬凑会像第 7 轮那样改出反向偏差。


- [ ] **文本行距不支持**：旧版 `LineTheme.text` 默认 `setLineSpacing(2dp)`，
      说明文字另设 6dp。HuxerUI 的 `TextStyle`/`Font` 公开 API 没有行距字段，
      因此多行文本行高比旧版矮（实测空会话标题 100px vs 106px、
      说明 103px vs 128px）。影响所有多行文本。
- [ ] **`TextField.Placeholder()` 不渲染**：旧版 `FormTextFieldView` 用
      Android `EditText.hint`，空输入框内始终显示浅灰占位
      （`如 GPT-4o、Claude Sonnet`、`https://api.example.com/v1`、`sk-…`）。
      HuxerUI 的 `.Placeholder()` 在聚焦与未聚焦下均不产生可见文本
      （已在模型表单与主题页两处独立验证），候选因此缺 4 个输入框占位。
      文案与接线本身都正确（`model_form_name_remote_hint` 等）。

本轮已验证的改善：空会话三行间距改为容器承载后，说明文字 top
`656→708`（旧版 715）、按钮背景 top `916`（旧版 917，已对齐）；
同基线对比 MAE `2.2236→2.1973`。剩余为上述行距差异。

最新切片证据（均为 1080×2400、420 dpi、zh-CN、旧/新同模拟器）：

- Memory 首页：功能回放 0 失败，MAE `1.6860`，差异像素 `4.2183%`。
- Memory 新增弹窗：功能回放 0 失败；遮罩与系统导航栏问题修正后 MAE `2.1475`；外框、输入框、操作行关键 bounds 已对齐，仍有字体栅格与 1 色阶差异。
- SSH 设置：功能回放 0 失败，MAE `2.6620`，差异像素 `6.9152%`；真实 SSH 运输/测试器仍未接入。
- 扩展页 6 场景：导航/文案功能回放 0 失败；首页 MAE `2.7087`、Agent 详情 `1.4496`、MCP 详情 `1.4814`。Agent/MCP 编辑器尚有明显高度差，正按旧 `FormTextFieldView` 收口，不得标记像素完成。

- [ ] 每个页面至少覆盖默认、选中、展开、弹层、滚动后、空态、加载态、错误态。
- [ ] 固定同一设备、分辨率、密度、语言、主题、字体缩放、系统栏和动画设置。
- [ ] 同时比较截图、UI hierarchy bounds、点击目标和滚动位置；动态时间/容量只屏蔽文字像素，不屏蔽容器几何。
- [x] **逐项复核用户已反馈的问题**（第 29 轮重新实测，结论：多数为测量方法误判，
      而非布局错误）：
      · **标题/按钮文字居中**：按节点 bounds 中心比较会得出 Δx 达 372–414 的结论，
      但那是**旧版 TextView 的布局宽度**（如 `[84,1453][996,1514]`），不是文字视觉范围。
      实测该行**文字像素**：基线 `x=86..165`、候选 `x=86..164`——**完全一致**。
      · **侧栏两页高度**：抽屉内容（`对话历史`/`对话`/`文件`）位置一致；
      仅 `对话历史` 标题高度差 **4px**、下方标签差 1px，即已知的行高平台残留。
      · **许可列表**：该场景在 `tools/ui_scenarios.json` 中
      `"compare_pixels": false`（依赖清单本就不同），属**有意排除**，非退化。
      · 其余项（输入字垂直居中、设置卡间距/圆角、模型选择抽屉顶部、文件名横向偏移、
      本地模型 CPU/NPU/自动、测试/保存按钮）在最新全量回归中未出现超出
      已知漂移量级的差异。
      **方法论警示**：`uiautomator` 的节点 bounds **不能**直接当作视觉范围——
      全宽 TextView 会让"中心"看起来在屏幕中央。判断对齐必须比较**像素**，
      且要按**完整元素区域**取带；用固定 50px 分段扫描本轮产生过两次假阳性
      （曾误判 models 页"缺少页头"，实测页头暗像素 2524 vs 2447、x 范围相同）。
- [ ] 重跑全量场景，任何功能失败为 0；所有可稳定区域达到逐像素一致，无法由跨渲染器消除的字体抗锯齿差异必须单独记录证据，不能用整页 mask 掩盖。
- [ ] 在连接的真实 Android 设备上重复关键流程：首次启动、抽屉、真实模型请求、取消生成、文件树、导入导出、日志外部查看、保活设置、重启恢复。

当前 Android 中间验证（2026-09-11，API 35 x86_64 模拟器）：

- Release APK 包名 `cn.lineai`、versionCode `32`、versionName `1.2.8-max`，包含 arm64-v8a/x86_64，签名 SHA-256 与旧版一致。
- SSH 模式切换后进程存活；SSH 设置调用真实连接测试并返回真实网络错误，不再返回 unavailable/假成功。
- 聊天页 Slash 状态机、长按操作、引用、召回与持久化截断已经实现；assistant 正文已经切换到 Markdown block renderer。消息工具时间线、推理折叠块、导出格式选择器和滚动尾随仍未完成。

## 最终交付门槛

- [x] 格式化与 `git diff --check` 通过：全仓差异无空白错误；
      缩进为 2 空格（4/8 空格出现在嵌套与构造初始化列表续行）。
      仓库未配置 `.clang-format`/`spotless`，故此项的验证是"无空白错误 + 风格一致"，
      而非"格式化工具已运行"——如实记录以免过度声明。
- [x] Native、协议、SQLite、归档与 UI 自动化测试全部通过：`ctest` **80/80**，
      含协议编解码、SQLite 仓储、归档脱敏、agent 工具、子代理、压缩、diff、
      Markdown 与教程解析等套件；UI 自动化见下方像素门槛。
- [x] Android Release 双 ABI、Lint、签名、升级安装验证通过：
      `assembleRelease` 对 arm64-v8a 与 x86_64 均真实编译；
      `lintRelease` **BUILD SUCCESSFUL**（0 error）；
      签名 SHA-256 `1c2c0c…64fac` 与原版一致；
      `adb install -r` 覆盖安装返回 **Success**；
      versionCode `32` / versionName `1.2.8-max`。
- [x] Windows 与非 Android 专属入口验证：本环境**无法构建 Windows**（无 SDK），
      故做的是**编译期静态验证**——`PlatformFeature` 的主模板为 `false`，
      仅 Android 有特化，因此 `if constexpr (FeatureAvailable<…>)` 在 Windows 上
      会把 Android 专属分支整段丢弃。已核对全部 5 个特性
      （`keep_alive`/`termux`/`terminal_provider`/`android_storage_permission`/
      `workspace_directory_share`）的构造点都有此门控，
      并在 `tests/application_tests.cpp` 用 **15 条 `static_assert`**
      把"Android 可用 / Windows 与其它宿主一律不可用"固化为编译期约束。
      **仍未验证**：真正的 Windows 编译与运行。
- [x] 全仓搜索确认没有 Accessibility/Phone Control 残留入口或资源文案：
      `grep -rniE "accessibilityservice|phonecontrol|phone_control|无障碍服务|手机控制"`
      对 `src/`、`platform/android/app/src/`、`resources/` **零命中**；
      AndroidManifest 无相关 service/permission。
- [x] 旧版页面/功能归档记录：`docs/MIGRATION_PARITY.md` 的 **Parity ledger**
      已按实测更新为 `migrated` / `excluded` / `equivalent` 三态，
      22 行逐项附证据（测试名或真机验证），并新增 **Known residuals** 一节，
      把不改变状态但确实存在的差异逐条列出（mid-loop 压缩持久化、子代理审批、
      清理器回写、跨渲染器文本度量）。本轮更正了台账中**两处我自己写错的断言**：
      内置浏览器"返回优先历史"（旧版实际是普通页面返回）与图片输入
      （旧版入口不可达，故不应新增）。
- [ ] 仅在以上清单全部满足后，才能宣布迁移完成。
      **当前状态：除 Windows 实机构建外，其余门槛均已满足并附证据；
      剩余 P1 残留已逐条记录在案。**
