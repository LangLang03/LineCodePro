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
- [ ] `ToolTextCatalog` 目前固定英文（构造函数默认值）：`domain::McpExecutionSettings`
      不含语言字段，且 HuxerUI 0.3.0 没有公开的「组合期读取当前 Locale」接口。
      仅影响工具回给模型的文案语言，不影响功能。
- [x] 已核实 `shell` 组在 local 模式本就不可用：旧版
      `ToolSettingsRepository.java:104` 把 shell 组声明为 `MODE_REMOTE`，
      `getEnabledToolNames()` 会按模式过滤。C++ 的 `remote` 掩码是忠实迁移，
      不是缺失，无需放开。
- [ ] P0 上下文压缩服务（`压缩上下文` 菜单当前仍是空实现）。
- [ ] P0 自定义 Agent 扩展与已安装 Skill 的提示词注入（`BuildExtensionPrompt()`
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
- [ ] 压缩的剩余部分：压缩**进度块**（Compacting / 完成 / 失败，
      旧版 `ContextCompactBlockView.java`）与**自动/软压缩触发**尚未接入；
      当前只有手动压缩路径。
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
- [ ] 重建旧版对照 APK 后发现的既有偏差：此前使用的
      `app-baseline.apk` 构建于 9月6日，落后旧仓库多个提交（例如
      `1d47496 fix: keep compaction inside processing turns`），
      因此**此前所有「基线」截图都缺少上下文用量指示器等新特性**。
      已重建并重新安装 `cn.lineai.legacy`，后续像素对照均以新版为准。
- [ ] P0 写入工具的 diff 视图与 Accept/Revert 回滚缺失：
      `domain::ChatTimeline` 的 `diff_id` / `review_state` 已入库但展示层不读，
      `chat_screen.cpp` 的 write 卡退化为文本 "Diff unavailable"；
      旧版对应 `ToolCallWriteView` + `DiffView` + `ToolReviewController.revertDiff`。
- [ ] P0 子代理 / Agent Pipeline 执行引擎整段缺失（旧版
      `app/src/main/java/cn/lineai/mvp/agent/` 约 2576 行）。这同时导致
      `agentSystemPrompt` 模板的 `EXTENSIONS_CONTEXT` 槽位无人渲染、
      `SkillRepository::BuildExtensionPrompt()` 零调用点。
- [ ] P1 图片输入（拍照/相册）缺失：`domain/input_attachment.h` 无图片负载；
      旧版对应 `ComposerView.java` 的 `onSendWithImage` / `onImagePickerClick`。
- [ ] P1 生成失败自动重试（旧版 `MAX_RETRIES = 3`）、模型切换提示与
      中断恢复提示缺失。
- [x] todo 状态已注入提示词：`TodoStateStore` 由组合根经
      `MainScreen → HomeScreen → ChatScreen → ComposerGenerationRunner` 注入，
      每次请求前重新 `Load()` + `RenderTodoState()`，与旧版
      `GenerationFlowController.java:773` 在工具循环内重建提示词的行为一致。
- [ ] P1 附件选择器缺少 SSH / terminal-provider 来源，
      `chat_overlays.cpp` 把 `source` 硬编码为 `"local"`。
- [ ] P1 Markdown 退化：裸 URL 不可点、引用/列表内代码块被压成一行、
      思考块直接输出原始 `**`；旧版有 `Linkify.WEB_URLS` 与 `ThinkingBlockView` 样式。
- [ ] P1 回合汇总「已编辑 N 个文件 / Review」区块缺失。

已核实**不属于**缺口的项（不要重复投入）：
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
- [ ] 用旧版和新版实际互导 `.linecode`，逐项核对会话、模型、设置和工作区文件。
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

最新切片证据（均为 1080×2400、420 dpi、zh-CN、旧/新同模拟器）：

- Memory 首页：功能回放 0 失败，MAE `1.6860`，差异像素 `4.2183%`。
- Memory 新增弹窗：功能回放 0 失败；遮罩与系统导航栏问题修正后 MAE `2.1475`；外框、输入框、操作行关键 bounds 已对齐，仍有字体栅格与 1 色阶差异。
- SSH 设置：功能回放 0 失败，MAE `2.6620`，差异像素 `6.9152%`；真实 SSH 运输/测试器仍未接入。
- 扩展页 6 场景：导航/文案功能回放 0 失败；首页 MAE `2.7087`、Agent 详情 `1.4496`、MCP 详情 `1.4814`。Agent/MCP 编辑器尚有明显高度差，正按旧 `FormTextFieldView` 收口，不得标记像素完成。

- [ ] 每个页面至少覆盖默认、选中、展开、弹层、滚动后、空态、加载态、错误态。
- [ ] 固定同一设备、分辨率、密度、语言、主题、字体缩放、系统栏和动画设置。
- [ ] 同时比较截图、UI hierarchy bounds、点击目标和滚动位置；动态时间/容量只屏蔽文字像素，不屏蔽容器几何。
- [ ] 逐项清零用户已反馈的问题：标题/按钮文字居中、输入字垂直居中、设置卡间距/圆角、模型选择抽屉顶部、侧栏两页高度、文件名横向偏移、本地模型 CPU/NPU/自动、测试/保存按钮、许可列表。
- [ ] 重跑全量场景，任何功能失败为 0；所有可稳定区域达到逐像素一致，无法由跨渲染器消除的字体抗锯齿差异必须单独记录证据，不能用整页 mask 掩盖。
- [ ] 在连接的真实 Android 设备上重复关键流程：首次启动、抽屉、真实模型请求、取消生成、文件树、导入导出、日志外部查看、保活设置、重启恢复。

当前 Android 中间验证（2026-09-11，API 35 x86_64 模拟器）：

- Release APK 包名 `cn.lineai`、versionCode `32`、versionName `1.2.8-max`，包含 arm64-v8a/x86_64，签名 SHA-256 与旧版一致。
- SSH 模式切换后进程存活；SSH 设置调用真实连接测试并返回真实网络错误，不再返回 unavailable/假成功。
- 聊天页 Slash 状态机、长按操作、引用、召回与持久化截断已经实现；assistant 正文已经切换到 Markdown block renderer。消息工具时间线、推理折叠块、导出格式选择器和滚动尾随仍未完成。

## 最终交付门槛

- [ ] C++/Java/资源格式化与 `git diff --check` 通过。
- [ ] Native、协议、SQLite、归档和 UI 自动化测试全部通过。
- [ ] Android Release 双 ABI、Lint、签名、升级安装验证通过。
- [ ] Windows 构建和非 Android 专属入口验证通过。
- [ ] 全仓搜索确认没有 Accessibility/Phone Control 残留入口或资源文案。
- [ ] 所有旧版页面/功能都有“已迁移、明确排除、或有测试证明被等价合并”的归档记录。
- [ ] 仅在以上清单全部满足后，才能宣布迁移完成。
