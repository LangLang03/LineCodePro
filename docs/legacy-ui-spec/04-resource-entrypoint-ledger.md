# 旧版 UI 规格 04：资源与非 `ui` 入口零遗漏台账

> 审计源：`/home/LangLang/AndroidStudioProjects/LineCode`。除特别说明外，源码路径均相对此目录。本文不重述 01/02/03 已记录的页面 View tree，而负责交叉证明：哪些非 `cn/lineai/ui` 代码、Manifest、资源和平台入口会改变用户可见状态，既有文档是否已经覆盖，以及迁移时还缺什么。

## 1. 结论与权威顺序

- `app/src/main` 共核验 `253` 个文件；其中 Java `225` 个，位于 `cn/lineai/ui` 之外的 Java `128` 个；没有 Kotlin/KTS。
- `app/src/main/res` **没有** `layout/`、`menu/`、`anim/`、`animator/`、`navigation/`、`font/`、`raw/` 资源。所有页面均由 Java View 代码构造；不存在尚未抄录的 XML 页面布局或菜单。
- 01 已充分覆盖主壳/聊天/消息/工具卡的 View 结果，02 已覆盖设置与详情页，03 已覆盖通用弹层、选择器、次级 UI 和主题资源。新增的真实缺口主要是：Activity 启动与窗口状态、Controller 动态文案/条件、Android 保活通知、系统 picker/share fallback，以及表现端口的 attach/detach 语义。
- 本文发现 03 §2.4 两处笔误：`ScreenScaffoldView` 默认 content padding 是 `0/0/0/100dp`，不是 `16/8/16/100dp`；`ActionRowView` 描述是 `11sp`、chevron well/glyph 是 `20/17dp`，不是 `13sp`、`24/16dp`。证据为 `ScreenScaffoldView.java:21-27`、`ActionRowView.java:39-52`；02 §2.2 已写对，因此尺寸权威顺序是 **源码 > 02 > 03**。
- Accessibility/Phone Control 不进入目标实现；本文只登记完整删除闭包。Keep Alive 只在 Android 构造页面、通知和系统入口，Windows 连入口和占位都不构造。

覆盖标记：`01`、`02`、`03` 表示已有文档覆盖；`补04` 表示本文补齐；`删` 表示禁止迁移；`平台口` 表示 UI 意图保留但 Android API 要换成 HuxerUI/OS capability。

## 2. 非 `ui` 可见入口覆盖矩阵

### 2.1 根 Activity、表现端口和装配入口

| 文件 | 可见/交互职责 | 01/02/03 状态 | 本次结论 |
|---|---|---|---|
| `app/src/main/java/cn/lineai/MainActivity.java` | 启动画面、协议弹窗时序、系统栏/IME/inset、返回、主题重建、系统 picker、权限回调 | 页面结果分散覆盖，入口状态未覆盖 | `补04`，见 §3 |
| `mvp/MainContract.java` | 将 `ChatRenderView`、`OverlayView`、`PickerView`、`ScreenView`、`PermissionView` 合并为表现端口（`:3-9`） | 未显式登记 | `补04`；C++ 应拆为 capability/port，不让业务层依赖 Android View |
| `mvp/ViewProxy.java` | view 未 attach 时静默丢弃所有 render/dialog/picker/navigation/permission effect；attach/detach 是 UI 生命周期闸门（`:9-26,28-239`） | 未覆盖 | `补04`；必须保留生命周期安全语义，不能把异步回调投递给已销毁窗口 |
| `mvp/MainDependencies.java` | 构造 repository/service/controller 依赖，并持有 Android `Context` | 未作为 UI 规格 | 装配根；迁移为 C++ composition root + 平台 adapter |
| `mvp/MainControllerInitializer.java` | 把文件、权限、设置、模型、扩展、归档、分享等 controller 回调接到表现端口 | 结果分散在 01/02/03 | 保留事件映射；不可把 controller 文案/路由遗漏为“非 UI” |
| `mvp/MainCoordinator.java` | attach/detach/destroy、screen/picker/overlay 的总事件 facade | 结果分散覆盖 | 保留单窗口 coordinator；析构必须取消任务、解除表现端口 |
| `mvp/MainUiController.java`、`NavigationController.java`、`ScreenNavigationController.java`、`ScreenView.java` | route 与前后向导航、回聊天、外链、theme recreate、screen evict/invalidate | 01 §2.3、02 §3 | 已覆盖结果；C++ 用 typed route + `NavigationStack`，禁止继续散落裸字符串 |
| `mvp/OverlayView.java`、`PickerView.java`、`PermissionView.java`、`ChatRenderView.java` | 弹层、picker、权限、聊天渲染的完整 effect surface | 01/03 | 已覆盖结果；这是平台边界清单，不是可删的“旧 MVP” |

### 2.2 会改变可见内容的 Controller/Assembler

| 文件组 | 可见行为 | 覆盖与遗漏 |
|---|---|---|
| `ChatInteractionController`、`ChatController`、`QuoteController`、`StreamingRenderController`、`ConversationPersistenceController`、`ConversationResumeSanitizer` | 草稿、引用、清空、streaming/resume 后消息状态 | 01 已覆盖主要结果；生命周期补见 §3.3 |
| `ChatUiStateAssembler.java` | 未选模型显示硬编码“未选择模型”；空 SSH path 显示“SSH 登录目录”；context label 为 `percent + "% / " + label`（`:50-85`） | 01 未精确登记这三个派生字符串，`补04`；需进入本地化资源 |
| `OverlayActionController.java` | More sheet 固定顺序为 Tutorial → Settings → Export → Select export → Compact → Clear（`:83-106`）；点击后除 settings/tutorial 外隐藏 overlay（`:109-153`） | 01/03 已有视图，`补04` 记录顺序与 dismiss 例外 |
| `ProjectSheetController.java` | 项目项可删条件；Local/Termux-SSH 才有本地 SAF 和全部文件权限；纯 SSH 隐藏两者；所有模式都有 Create；标题在“工作区/工作区 SSH”切换（`:75-138`） | 01/03 部分覆盖；精确平台/模式条件与硬编码中文 `补04` |
| `ProjectWorkspaceController.java` | 创建名称输入、删除确认、权限说明、成功/失败 notice，以及 Android 全文件权限入口 | 01/03 只覆盖容器；动态文案与授权回流为 `补04/平台口` |
| `FileOperationController.java` | 新建文件/目录、复制、粘贴、重命名、删除 action；输入/危险确认；错误 notice | 01/03 覆盖行样式，未完整登记 controller 生成文案；`补04` |
| `AttachmentPickerCoordinator`、`DirectoryPickerController` | picker loading/empty/error/title/subtitle/source 的状态机 | 03 §6 已覆盖视图；硬编码中文和异步失效保护仍需迁移 |
| `PermissionModeController` | Auto/Confirm/Readonly/Manage-all-files/Revoke 选项、选中态与权限说明 | 03 §4、§6；Android 设置入口为 `平台口`，Windows 不出现 Android 权限项 |
| `ContextCompactionController` | 手动 compact 确认、进度、失败原因、完成 notice | 01 §7.4/03 §7 只覆盖呈现形态；多条 controller 错误文案未逐项登记，`补04` |
| `LineCodeArchiveController` | import/export picker、覆盖确认、校验/成功/失败 notice | 02 §9.1 只覆盖入口；结果反馈和失败分支 `补04/平台口` |
| `ModelInteractionController.java` | streaming 时 quick switch 无动作（`:47-53`）；模型测试成功 dialog 拼接耗时+raw response，失败为长 Toast 且追加 `(Nms)`（`:55-80`） | 02 §7 未精确覆盖，`补04` |
| `ModelManagementController`、`ModelController`、`ModelPromptController` | 模型增删改选、prompt、测试结果 | 02 §7；以上 streaming/结果格式为新增 |
| `ExtensionManagementController`、`ExtensionController`、`ExtensionDraftController` | 保存/安装/删除后的 route refresh、错误 notice | 02 §10 覆盖页面，post-action route/error 为 `补04` |
| `ExtensionKindRegistry`、`AgentKindDescriptor`、`McpKindDescriptor`、`SkillsKindDescriptor`、`LinecodeKindDescriptor` | 注册 kind 与动态 row title/desc/icon/action | 02 覆盖结构；动态 desc/registry 边界见 §5.3 |
| `AiBehaviorSettingsController`、`InputSettingsController`、`OutputSettingsController`、`ThemeSettingsController`、`McpSettingsController`、`SettingsController`、`SettingsManagementController` | 设置保存、屏幕失效/重建、MCP mode change | 02 已覆盖；theme rebuild 时序补见 §3.4 |
| `ErrorLogController`、`StorageController`、`StorageMaintenanceController`、`IpcProviderController` | 清理、扫描、确认、Toast/notice、刷新 | 02 §6.5、§9；Android open-with/IPC 是 `平台口` |
| `FileTreeInteractionController`、`SshFileTreeController`、`RemoteFileTreeController`、`IpcFileTreeController` 及各 Host | 文件树 loading/error/open/action | 01 §4.3/03 picker；平台文件树 provider 必须能力注入 |
| `ShareController.java` | 导出格式 native dialog、成功/失败反馈、后台导出 | 03 §5.2/§7.2；C++ 不得用 detached raw thread |
| `WorkspaceShareHelper.java` | 先 `ACTION_VIEW resource/folder`；失败再 `ACTION_SEND` path+root URI；最终失败长 Toast（`:20-42`） | 03 只登记入口，精确 fallback `补04/平台口` |
| `GenerationFlowController`、`GenerationController`、`GenerationLifecycleController`、`ToolConfirmationController`、`ToolReviewController`、`ToolMessageController`、`ToolRunController`、`ToolExecutionScheduler` | 生成状态、审批、review、工具消息 | 01 §§11-12、03 §8；Keep Alive 生命周期见 §4 |
| `agent/AgentExecutionController`、`AgentProgressMirror`、`AgentProgressSession`、`PipelineProgressSession` | agent/pipeline progress、可见工具状态 | 01 §11.5；其余 agent DTO/解析器不直接构造 UI |
| `ActivityGenerationLifecyclePolicy.java` | Home/多任务不取消；仅 finishing/destroy/用户 Stop 取消（`:3-32`） | 未独立登记，`补04`，见 §3.3 |
| `PhoneControlController`、`PhoneControlRepository`、`LineCodeAccessibilityService` | 手机控制 permission/state/UI effect | `删`，见 §6 |

### 2.3 已筛过但不直接构造窗口/控件的外部文件

下列文件仍可能产生页面数据或错误文本，因而迁移业务功能时要保留其 domain 语义；但它们不新增 View tree、dp/sp、颜色或动画：

- prompt：`ai/prompt/{MemoryPromptBuilder,SkillPromptBuilder,ToolPromptRenderer,ToolPromptService}.java`。
- import/data：`data/importer/{LineCodeArchiveService,LineCodeImportService}.java`；`data/repository/{ChatModeRepository,CommandPermissionRepository,ExtensionRepository,LearningContextRepository,ProjectRepository,SkillRepository,SshFileTreeRepository,ThemeSettingsRepository,ToolSettingsRepository}.java`；`PhoneControlRepository.java` 属删除闭包。
- service/client：`data/service/{ContextResourceProvider,GitHubSkillInstaller,SkillFileManager,SkillHubClient,SkillHubSessionClient}.java`。
- controller 支撑/模型：`mvp/{ArchiveController,BackgroundRunner,BackgroundTaskRunner,ChatInteractionHost,ChatSessionStore,ContextCompactionHost,ExecutionModeFileStoreRegistry,ExtensionItem,ExtensionKindDescriptor,FileOperationHost,GenerationFlowHost,HostBase,IpcFileTreeHost,MainThreadDispatcher,ProjectRuntimeState,ProjectWorkspaceHost,SshFileTreeHost,UiDispatcher,WorkspaceController}.java`。
- agent domain：`mvp/agent/{AgentPromptBuilder,AgentResultRecord,AgentResultRegistry,AgentRunResult,PendingToolExecution,PipelineAgent,PipelineAgentState,PipelineDependencyResolver,ToolExecutionBatch}.java`。
- platform/data：`service/{KeepAliveService,LearningContextService}.java`、`workspace/{SafPathResolver,StoragePermissionManager,WorkspaceFileProvider}.java`；这些不创建应用内页面，但会触发通知、系统授权、provider 或 picker，边界见 §3-§4。

由此，`cn/lineai/ui` 外没有第二套隐藏页面框架；可见 UI 只会通过上表的表现端口、Android 系统 UI 或 notification 出现。

## 3. `MainActivity` 遗漏的根窗口行为

### 3.1 启动帧与协议时序

- `onCreate` 先应用主题/窗口、初始化错误日志并清空 `PhoneScreenshotCache`，再创建 SAF/权限代理（`MainActivity.java:35-50`）。截图 cache 清理属于 Phone Control 删除闭包。
- 首帧不是完整 UI，而是一个全屏空 `FrameLayout`，背景严格为 `LineTheme.BG`，用于避免白屏（`:52-55`）。目标端也应有同色、无 spinner 的 loading/root placeholder。
- `MainDependencies` 和 `MainCoordinator` 在裸后台线程构建；回 UI 线程且 Activity 尚未 finishing/destroyed 时，才创建 `MainChatView`、替换 content、attach presenter、注册返回（`:57-72`）。目标端用受生命周期管理的 C++ task/executor；任务完成时必须验证 window token/lifetime。
- 用户协议检查发生在启动后台构建**之后**；协议弹窗可能覆盖 splash，后台任务仍可能替换其下的 root；拒绝执行 `finishAffinity()`（`:74-83`）。1:1 验收必须包含首次启动、初始化很快/很慢两种竞态。

### 3.2 窗口、IME、safe area 与返回

- window soft input 为 `adjustResize`；status/navigation bar 都取 `LineTheme.BG`（`:227-230`）。
- API 30+ 调 `setDecorFitsSystemWindows(false)`，内容是 edge-to-edge；主 View 自己消费 inset（`:231-233`，具体 padding 见 01）。迁移用 HuxerUI `WindowContentMode::SafeArea`/等价 inset 策略，不能再硬编码一层重复顶部 padding。
- 系统栏图标明暗并非只读 XML bool：Activity 计算 BG 的 sRGB 加权亮度，阈值严格为 `> 0.64`，API 26+ 同步导航栏图标（`:234-255`）。目标用 `SystemBarsAppearance`/平台窗口 adapter；Windows 无 Android system bar。
- API 33+ 注册 predictive-back callback；先交给 `mainView.handleBackPressed()`，未消费才 `finish()`；旧 API 走 `onBackPressed` 同一逻辑（`:158-205`）。HuxerUI route stack 的 pop 优先级必须先于窗口关闭。

### 3.3 前后台与生成任务

- `onPause` 只通知 presenter 进入后台（`:86-92`）。
- `onStop` 只有 `isFinishing()==true` 才 reset generation；Home、多任务切换不会取消 stream/review/keep-alive（`:94-103`; `ActivityGenerationLifecyclePolicy.java:16-24`）。
- `onStart` 永远不 reset；process-death 残留 streaming 状态在 conversation load 时清理（`MainActivity.java:105-113`; policy `:26-32`）。
- `onDestroy` 注销 back callback 并 `presenter.destroy()`，这是明确 teardown（`:115-123`）。Windows 虽无 Android 保活 UI，后台生成的任务/lifecycle 语义仍要由跨平台 app session 明确定义，不能误等同于窗口暂时失焦。

### 3.4 主题重建与系统 picker

- theme change 不是局部 repaint：重新 `configureWindow`，detach 旧 view，new `MainChatView`，重新 attach，并按 `screenId` 恢复 screen（`:258-272`）。目标可改进为 reactive palette，但必须保留当前 route、overlay 关闭规则、scroll/draft 状态的可观察结果。
- Android 通过 SAF/Activity Result 实现目录、`.linecode` import/export、图片选择（`:137-155,274-373`）。Windows 应显示等价 native picker；不可展示 Android 权限、SAF、URI grant 文案。picker cancel 仍需回传 controller，避免 loading 卡死。

## 4. Keep Alive：Android-only 的可见系统 UI

02 §11.1 已覆盖应用内设置页，但以下**通知 UI**此前仅登记了图标，必须补齐：

- channel id `linecode_keep_alive`、通知 id `1001`；channel importance `LOW`、描述取 keep-alive 文案、badge 关闭（`KeepAliveService.java:129-139`）。
- 实际合成规则：wake lock = manual wake lock 或 generation active；fake audio = manual fake audio 或 generation active 且 generation fake-audio；foreground = manual foreground 或 generation active 或 fake audio（`:142-169`）。因此生成期间即使用户没打开“前台服务”手动开关，也会有 foreground notification。
- partial wake lock 带 `ON_AFTER_RELEASE`（`:187-198`）；silent audio 为 mono/PCM16/8kHz、2 秒静音、静态 buffer 无限循环（`:208-268`）。两者没有额外应用内 UI，但决定开关真实状态。
- 通知 small icon 为 `ic_keepalive_notification`；title 为 `notification_keep_alive_title`，正文优先动态 `currentStatus`，否则默认 text；点击回 `MainActivity`；ongoing、service category、public visibility、ticker、low priority（`:270-303`）。通知更新仅在系统通知启用时执行（`:306-309`）。
- generation 在 API 26+ 无条件 `startForegroundService`（`:347-357`），与手动 start 的条件路径不同（`:316-331`）。生命周期规则见 §3.3。
- Android 保留 Manifest 的 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`POST_NOTIFICATIONS`、`WAKE_LOCK`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 和 `KeepAliveService`/special-use subtype（`AndroidManifest.xml:6-10,55-62`）。
- Windows：Settings hub 不创建 Keep Alive row，不注册 route/factory，不打包通知图标/Android strings，不留下 divider/spacing；后台生成只使用 Windows 合法的普通任务/窗口生命周期策略，不伪造 Android wake lock、假音频或前台服务 UI。

## 5. 动态可见内容：页面代码之外的遗漏

### 5.1 项目、文件与上下文

- 默认项目 label/desc 是 `LineCode` / “默认 home 工作区”；默认 SSH 是 `SSH` / “SSH 登录目录”；空 external/SSH 名分别回退“外部工作区”“SSH 工作区”（`ProjectRepository.java:202-245,338-365`）。这些会进入 drawer/project sheet，必须资源化。
- Project sheet 的删除 label、标题、打开本地项目、创建工作区、全文件权限及说明均硬编码中文（`ProjectSheetController.java:83-115,127-138`）。旧版俄语/默认语言下仍可能出现中文；1:1 可先保留实际输出，改进版应补 locale key，但不可改布局或分支。
- `FileOperationController`、`ProjectWorkspaceController`、`DirectoryPickerController`、`LineCodeArchiveController`、`ContextCompactionController` 也直接拼出用户可见中文 notice/dialog。迁移不得只搬 `strings.xml` 后假设文案齐全；应将这些 literal 纳入 strings catalog。
- `ChatUiStateAssembler` 的“未选择模型”“SSH 登录目录”和 context 百分比格式同理（`:58-70`）。

### 5.2 系统反馈与分享

- Android Toast 是反馈端口，不是页面控件。03 §7.2 的文本/触发条件保留；Windows/HuxerUI 使用 native transient feedback 或应用内 snackbar，但不得漏掉失败分支。
- `WorkspaceShareHelper` 的 folder-view → text/share fallback 是 Android Intent 特有（`:20-42`）。Windows 应优先 native “在文件管理器打开”；若失败再走等价 share/copy-path 能力，UI 文案和错误反馈保留。
- model test 成功使用应用内确认 dialog，内容为本地化 summary、空行、raw-response label、原始响应；失败才是长 Toast，且末尾追加耗时毫秒（`ModelInteractionController.java:55-80`）。

### 5.3 Extension 描述符生成的 row 内容

- registry 只注册 `agent`、`mcp`、`skills`、`linecode`（`ExtensionKindRegistry.java:17-22`）；Terminal Provider 是独立设置路径，不应误塞入 extension kind。
- Agent row desc 为 `<slug> · <N> tools`（`AgentKindDescriptor.java:60-65,80-82`）；MCP 为 `<N> tools · <URL>`（`McpKindDescriptor.java:60-65,80-82`）；两者允许 Modify。
- Skill row desc 为 `<locationLabel> · <skillMdPath>`，不允许 Modify（`SkillsKindDescriptor.java:49-65`）。LineCode 没有 installed items，也不允许 enable/delete/add（`LinecodeKindDescriptor.java:18-25,48-65`）。
- 单词 `tools` 是硬编码英文，会跨 locale 泄漏。迁移应改为 plural/localized formatter，同时保持 row 结构与分隔点。
- `ToolSettingsRepository.displayConfigForMode` 在 terminal-provider 模式把 shell 显示名替换为 `IPC Shell`，描述取 `tool_group_ipc_shell_desc`（`:249-256`）；这属于 Android-only Terminal Provider 页面/工具行。Windows 不显示此 provider 集成。

## 6. Accessibility / Phone Control 完整删除闭包

以下仅为删除范围，不是目标 UI 规格：

- Manifest：删除 `BIND_ACCESSIBILITY_SERVICE` permission（`AndroidManifest.xml:16`）以及 exported accessibility `<service>` 和 meta-data（`:64-74`）。
- 资源：删除 `app/src/main/res/xml/accessibility_service_config.xml` 全文件；删除三套 app strings 中 `screen_phone_control_*`、accessibility 描述/状态/权限等键。Gradle 未发现专用 Accessibility 依赖。
- 服务与数据：删除 `service/LineCodeAccessibilityService.java`、`data/repository/PhoneControlRepository.java`、`mvp/PhoneControlController.java`，以及相关 model/tool/cache（需按全仓 `rg 'PhoneControl|phone_control|Accessibility|accessibility'` 闭包继续删除）。
- 装配与路由：删除 `MainDependencies`、`MainCoordinator`、`MainUiController` 中 phone-control controller/state/event；删除 `MainActivity.java:40` 的 `PhoneScreenshotCache.clear`。
- 旧 UI 耦合（只用于定位）：`MainChatView`、`AdvancedFeaturesScreenView`、`PhoneControlScreenView`、`ScreenFactories`、`HeaderView`、`ScreenHeaderView`、`AssistantTurnView`、`ToolApprovalView`、`WorkingStatusView` 中相关入口/模式/icon/渲染全部删除。不能留下空 section/divider、不可达 route 或 disabled row。
- 工具配置：`ToolSettingsRepository.java:281-299` 的 CONTROL chat mode、`phone_*` 工具和 permission map gate 全删；同时删除 phone tool implementation/prompt/schema，不只是隐藏页面。
- Android `accessibility_service_config.xml:1-7` 请求全事件、读取窗口、报告 view id 和截图，是完整敏感能力；Windows 与 Android 目标均不得模拟、保留或改名迁移。

## 7. Manifest 与 `app` 资源逐文件台账

### 7.1 Manifest 可见/平台影响

| 范围 | 证据 | 迁移决定 |
|---|---|---|
| 网络 | `INTERNET`（Manifest `:5`）；manifest 自身 cleartext=false（`:42`），但 network config base=true | Android 保留；跨平台由 URL policy + HTTP transport adapter 决定 |
| 保活 | Manifest `:6-10,55-62` | Android-only，见 §4；Windows 完全隐藏 |
| Termux/terminal provider | `RUN_COMMAND`、自定义 IPC permission、package/intent queries（`:11,18-29`） | Android-only；Windows 不构造页面/route |
| 文件访问 | MANAGE/READ/WRITE external storage（`:12-15`） | Android-only 权限 UI；Windows 用 native filesystem/picker capability |
| App/window | icon、round icon、largeHeap、RTL、AppTheme（`:31-42`） | icon/RTL/theme 意图保留；`largeHeap` 不形成跨平台 UI contract |
| Providers | error log、share、workspace provider（`:76-96`） | Android URI adapter；Windows 替换 open/share/file API |
| Accessibility | `:16,64-74` | 删除 |

### 7.2 `app/src/main/res` 完整文件清单（24 个）

- `drawable/ic_keepalive_notification.xml`：24×24dp/viewport24、白色 code brackets（`:2-9`）；只打进 Android。
- launcher bitmap：`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher.webp,ic_launcher_round.webp}`，共 10 个；03 已登记。Windows 应使用目标平台 icon bundle，而不是运行时 UI glyph。
- theme：`values/colors.xml`、`values-night/colors.xml`、`values/styles.xml`、`values-v27/styles.xml`。
- locale：`values/strings.xml`、`values-zh/strings.xml`、`values-ru/strings.xml`。
- XML：`xml/accessibility_service_config.xml`（删）、`xml/backup_rules.xml`、`xml/data_extraction_rules.xml`、`xml/file_paths.xml`、`xml/network_security_config.xml`、`xml/share_file_paths.xml`。

精确行为：

- light XML baseline：BG `#FCFCFD`、accent `#333B46`、light bars `true`；night：BG `#171819`、accent `#E5E9EE`、light bars `false`（两份 `colors.xml:2`）。runtime `LineTheme` palette 仍是实际应用内组件权威，见 03 §2.2。
- `AppThemeBase` 是 `Theme.Material.NoActionBar`，无 title/action bar，sans，窗口/status/nav 同 BG（`styles.xml:3-14`）；API 27+ 才从 XML 增加 light navigation bar（`values-v27/styles.xml:3-5`），但 Activity 又做 runtime 同步。
- backup 和 device transfer 对 file/database/sharedpref/external 全部 exclude（`backup_rules.xml:8-13`; `data_extraction_rules.xml:6-19`）；不直接改变 UI，但决定迁移/恢复后的首屏状态。
- `network_security_config.xml:3-14` 在系统层全局允许 cleartext，安全由 `UrlPolicy` 负责；Windows transport 不能仅照抄 manifest 的 false。
- `share_file_paths.xml` 只暴露 cache `share/`（`:2-4`），并被 Manifest `:82-90` 使用。
- `file_paths.xml` 声明 cache/share 与三个 `.linecode` files path（`:2-7`），但全 `app/src` 没有 `@xml/file_paths`/`R.xml.file_paths` 引用；判定为 orphan，除非 merged manifest 或变体另有证据，否则不迁移。
- 空目录也已核验：`drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`、`mipmap-anydpi-v26` 没有文件。

### 7.3 字符串与 assets

- app default/zh/ru 每套均为 `1377` 个 `<string>`；03 §11.3 已登记 locale parity。这里新增的硬编码 controller 字符串不在该计数内，见 §5。
- `assets/tutorials/simple.md`、`assets/tutorials/pro.md` 是 Tutorial 可见正文，03 §3 已覆盖；`assets/termux_setup.sh` 不是 View 资源，但会影响 Android-only Termux 操作结果。

## 8. `ui-theme` 完整台账

### 8.1 Java（6 个）

- `BoundedScrollView.java`：03 §2.3 已覆盖 max-height measure。
- `FlowLayoutView.java`：03 §2.3 已覆盖 flow/gap；工具 chips/标签仍依赖它。
- `IconButtonView.java`：01/02/03 已覆盖 icon well、type→drawable 映射和 tint；迁移时应成为语义 icon component，不要散落图片路径。
- `InlineEmphasisParser.java`：03 已登记 inline emphasis；是文本解析，不新增平台 UI。
- `LineTheme.java`：03 §2.1-2.2 权威覆盖 dp/token/shape/text helpers。
- `ThinkingBlockView.java`：01 §7.2/03 §2.3 已覆盖。

### 8.2 Drawables（84 个，全集）

所有文件都位于 `ui-theme/src/main/res/drawable/`，均已由 03 §2.3/§11 统计；以下清单用于打包零遗漏校验：

```text
ic_lineai_mcp.xml
ic_lucide_archive.xml
ic_lucide_arrow_up.xml
ic_lucide_battery_charging.xml
ic_lucide_bell.xml
ic_lucide_book_open.xml
ic_lucide_bot.xml
ic_lucide_box.xml
ic_lucide_boxes.xml
ic_lucide_brain.xml
ic_lucide_bug.xml
ic_lucide_check.xml
ic_lucide_check_square.xml
ic_lucide_chevron_down.xml
ic_lucide_chevron_left.xml
ic_lucide_chevron_right.xml
ic_lucide_circle_alert.xml
ic_lucide_circle_check.xml
ic_lucide_circle_x.xml
ic_lucide_clock_3.xml
ic_lucide_code.xml
ic_lucide_coffee.xml
ic_lucide_contrast.xml
ic_lucide_copy.xml
ic_lucide_cpu.xml
ic_lucide_database.xml
ic_lucide_download.xml
ic_lucide_ellipsis_vertical.xml
ic_lucide_expand.xml
ic_lucide_external_link.xml
ic_lucide_file.xml
ic_lucide_file_code.xml
ic_lucide_file_pen_line.xml
ic_lucide_file_plus.xml
ic_lucide_file_text.xml
ic_lucide_file_up.xml
ic_lucide_flask_conical.xml
ic_lucide_folder.xml
ic_lucide_folder_open.xml
ic_lucide_folder_plus.xml
ic_lucide_git_branch.xml
ic_lucide_git_compare.xml
ic_lucide_globe.xml
ic_lucide_image.xml
ic_lucide_loader.xml
ic_lucide_menu.xml
ic_lucide_message_circle.xml
ic_lucide_message_square.xml
ic_lucide_message_square_text.xml
ic_lucide_monitor.xml
ic_lucide_moon.xml
ic_lucide_music.xml
ic_lucide_package.xml
ic_lucide_paintbrush.xml
ic_lucide_palette.xml
ic_lucide_play.xml
ic_lucide_plus.xml
ic_lucide_power.xml
ic_lucide_quote.xml
ic_lucide_refresh_cw.xml
ic_lucide_rotate_ccw.xml
ic_lucide_save.xml
ic_lucide_scroll_text.xml
ic_lucide_search.xml
ic_lucide_server.xml
ic_lucide_settings.xml
ic_lucide_share_2.xml
ic_lucide_shield.xml
ic_lucide_shield_check.xml
ic_lucide_sliders_horizontal.xml
ic_lucide_smartphone.xml
ic_lucide_smile.xml
ic_lucide_sparkles.xml
ic_lucide_square.xml
ic_lucide_square_function.xml
ic_lucide_sun.xml
ic_lucide_terminal.xml
ic_lucide_text_cursor.xml
ic_lucide_trash_2.xml
ic_lucide_upload.xml
ic_lucide_user.xml
ic_lucide_wrench.xml
ic_lucide_x.xml
ic_lucide_zap.xml
```

Phone Control 删除后，`ic_lucide_smartphone.xml` 是否仍被非 Phone UI 使用须由最终引用扫描决定；不可因为名字相似就误删。反之，目标只应打包可达语义 icon，不要求把 84 个全部无条件复制。

### 8.3 `ui-theme` locale

- `ui-theme/src/main/res/{values,values-zh,values-ru}/strings.xml`，每套 2 个 string；03 §11.3 已覆盖。无 layout/menu/anim 或额外 palette XML。

## 9. 01/02/03 覆盖勘误与仍需验收的差异

| 项目 | 既有文档 | 交叉审计结论 |
|---|---|---|
| 主壳/聊天/工具 UI | 01 | View tree 完整；新增验收 startup placeholder、协议竞态、派生 model/project label、Activity 生命周期 |
| 设置与详情 | 02 | 页面/route 基本完整；新增 controller post-action、dynamic desc、模型测试结果；Keep Alive 通知补 §4 |
| 弹层/picker/次级 UI | 03 | 组件与系统反馈基本完整；新增 project mode 分支、share fallback、Activity result cancel；纠正 scaffold/action-row 两处数值 |
| 资源 | 03 §11 | 数量结论正确；本文给出 app 24 文件与 ui-theme 84 drawable 精确全集，并标注 orphan/delete/platform-only |
| Android/Windows | 02/03 | Windows 必隐藏 Keep Alive、Termux、Terminal Provider、Android storage/notification/battery UI；Accessibility 两端删除；其余系统 picker/open/share 用平台 port 保留功能 |

最终 1:1 验收还必须覆盖：

1. 冷启动第一帧与主题同色、无白闪；首次协议弹窗同时覆盖快/慢依赖初始化。
2. edge-to-edge + IME resize；light/dark/custom BG 时系统栏图标阈值；返回先消费 overlay/screen 再关闭窗口。
3. Home/多任务期间 streaming 不被取消；finish/destroy/Stop 才 teardown。
4. More sheet 顺序、project sheet 在 local/SSH/Termux-SSH 三种模式下的项目数、额外项、divider/空白完全一致。
5. picker cancel、export/import 失败、模型测试空响应/异常、workspace open fallback 均有反馈且不会投递到 detached view。
6. Android generation 自动出现低优先级 ongoing 通知；Windows 无 Keep Alive 行、页面、通知图标或空占位。
7. 三 locale 下检查硬编码中文/英文泄漏；允许作为旧版现状记录，但现代化实现应资源化且保持同一几何布局。

## 10. HuxerUI/C++ 平台边界建议

- `AppShell` 只拥有 root loading/content、typed navigation、overlay host 和 window appearance；业务 controller 仅依赖 `IRenderPort`、`IDialogPort`、`IPickerPort`、`ITransientFeedbackPort`、`IPlatformCapability` 等窄接口。对应旧 `MainContract` 的职责拆分，符合 SRP/DIP。
- route 使用 enum/variant payload，`NavigationStack` 负责 push/pop/restore；不要把 `"extension:mcp"`、`"project:delete:"` 等字符串继续散布到 UI。
- Android adapter 独占 SAF、permissions、Intent/FileProvider、notification/FGS/wake-lock/battery/Termux/terminal-provider；Windows adapter 独占 native picker/open/share。portable domain 不 include Android header，也不暴露 `Uri`/requestCode。
- task 使用 HuxerUI lifecycle-aware task/executor、weak lifetime token 和主线程 dispatcher；禁止照搬 `new Thread` 后持有 raw view/activity。
- theme token、dp/sp 和 semantic icon 是共享 C++ UI contract；系统栏、安全区、桌面窗口 chrome 是平台策略。目标可改善内部结构和本地化，但不能借架构改造改变既有 View tree、间距、显隐和动作顺序。

## 11. 审计读取清单

### 11.1 `app/src/main` 非 UI Java 全量筛选范围

已对 `cn/lineai/ui` 外 128 个 Java 做文件名/引用/表现 effect 扫描，并完整或按可见调用链阅读高相关文件。包级全集如下，未出现 Kotlin：

- 根：`MainActivity.java`。
- `ai/prompt`：`MemoryPromptBuilder`、`SkillPromptBuilder`、`ToolPromptRenderer`、`ToolPromptService`。
- `data/importer`：`LineCodeArchiveService`、`LineCodeImportService`。
- `data/repository`：`ChatModeRepository`、`CommandPermissionRepository`、`ExtensionRepository`、`LearningContextRepository`、`PhoneControlRepository`、`ProjectRepository`、`SkillRepository`、`SshFileTreeRepository`、`ThemeSettingsRepository`、`ToolSettingsRepository`。
- `data/service`：`ContextResourceProvider`、`GitHubSkillInstaller`、`SkillFileManager`、`SkillHubClient`、`SkillHubSessionClient`。
- `mvp`：`ActivityGenerationLifecyclePolicy`、`AgentKindDescriptor`、`AiBehaviorSettingsController`、`ArchiveController`、`AttachmentPickerCoordinator`、`BackgroundRunner`、`BackgroundTaskRunner`、`ChatController`、`ChatInteractionController`、`ChatInteractionHost`、`ChatRenderView`、`ChatSessionStore`、`ChatUiStateAssembler`、`ContextCompactionController`、`ContextCompactionHost`、`ConversationPersistenceController`、`ConversationResumeSanitizer`、`DirectoryPickerController`、`ErrorLogController`、`ExecutionModeFileStoreRegistry`、`ExtensionController`、`ExtensionDraftController`、`ExtensionItem`、`ExtensionKindDescriptor`、`ExtensionKindRegistry`、`ExtensionManagementController`、`FileOperationController`、`FileOperationHost`、`FileTreeInteractionController`、`GenerationController`、`GenerationFlowController`、`GenerationFlowHost`、`GenerationLifecycleController`、`HostBase`、`InputSettingsController`、`IpcFileTreeController`、`IpcFileTreeHost`、`IpcProviderController`、`LineCodeArchiveController`、`LinecodeKindDescriptor`、`MainContract`、`MainControllerInitializer`、`MainCoordinator`、`MainDependencies`、`MainThreadDispatcher`、`MainUiController`、`McpKindDescriptor`、`McpSettingsController`、`ModelController`、`ModelInteractionController`、`ModelManagementController`、`ModelPromptController`、`NavigationController`、`OutputSettingsController`、`OverlayActionController`、`OverlayView`、`PermissionModeController`、`PermissionView`、`PhoneControlController`、`PickerView`、`ProjectRuntimeState`、`ProjectSheetController`、`ProjectWorkspaceController`、`ProjectWorkspaceHost`、`QuoteController`、`RemoteFileTreeController`、`ScreenNavigationController`、`ScreenView`、`SettingsController`、`SettingsManagementController`、`ShareController`、`SkillsKindDescriptor`、`SshFileTreeController`、`SshFileTreeHost`、`StorageController`、`StorageMaintenanceController`、`StreamingRenderController`、`ThemeSettingsController`、`ToolConfirmationController`、`ToolExecutionScheduler`、`ToolMessageController`、`ToolReviewController`、`ToolRunController`、`UiDispatcher`、`ViewProxy`、`WorkspaceController`。
- `mvp/agent`：`AgentExecutionController`、`AgentProgressMirror`、`AgentProgressSession`、`AgentPromptBuilder`、`AgentResultRecord`、`AgentResultRegistry`、`AgentRunResult`、`PendingToolExecution`、`PipelineAgent`、`PipelineAgentState`、`PipelineDependencyResolver`、`PipelineProgressSession`、`ToolExecutionBatch`。
- `service`：`KeepAliveService`、`LearningContextService`、`LineCodeAccessibilityService`。
- `workspace`：`SafPathResolver`、`StoragePermissionManager`、`WorkspaceFileProvider`、`WorkspaceShareHelper`。

### 11.2 资源与配置全读/全列范围

- `app/src/main/AndroidManifest.xml` 全文。
- §7.2 所列 `app/src/main/res` 24 个实际文件；所有空候选目录也已检查。
- `app/src/main/assets/{termux_setup.sh,tutorials/simple.md,tutorials/pro.md}`。
- §8.1 所列 `ui-theme` 6 个 Java、§8.2 的 84 个 drawable、§8.3 的三套 strings。
- 交叉读取 `01-shell-chat.md`、`02-settings-screens.md`、`03-secondary-ui.md` 的边界、资源、平台和疑似遗漏章节。

### 11.3 未覆盖引用/下一步静态门禁

- 最终删除 Phone Control 后，再跑全仓 symbol/resource shrink 检查，确认 `smartphone` icon 等资源是否仍可达；本台账不凭命名猜测删除共享资产。
- Android build variant/merged manifest 若在 `app/src/main` 之外注入 provider/resource，需追加审计；当前主源集没有 `file_paths.xml` 的消费者。
- 本文没有把业务错误的每一种后端原文列成视觉 token；验收应以 effect type（dialog/sheet/toast/inline error）、触发条件和 01/02/03 组件规格为准。
- HuxerUI 实作时若 SDK 的 Windows system bar/native picker API 与建议名不同，以当前 SDK 公共头为准，但平台显隐结论不变。

## 12. 目标集成审查（2026-09-06 当前树）

审查对象为 `src/presentation/main_screen.cpp`、`components/chat_screen.{h,cpp}`、`screens/settings_screen.{h,cpp}`，并联查 `components/drawer.*`、`line_theme.cpp`、`platform_features.h`、`src/app.cpp`、`src/app/app_root.cpp`。SDK 权威来自当前构建缓存指向的 `/home/LangLang/.local/share/HuxerUI/lib/cmake/HuxerUI` 及同 prefix 的 `include/huxerui` 公共头。

### 12.1 已确认符合公共 contract

- `AppRoute` 是 copyable/equality-comparable enum，满足 `NavigationRouteValue`；`State<NavigationPath<AppRoute>>`、root factory、resolver 和 `UseNavigation<AppRoute>()` 的签名与公共 `navigation.h:35-36,313-337,546-586` 一致。
- chat 是固定 root，settings/detail 是 path destination，避免把 chat 伪装成可 pop route；`RouteNavigationController` 按值捕获到 mounted click handlers 合法。`Pop()` 返回 false 的场景只在 path 空/已断连，当前 settings header 总是 route page。
- `MainScreen`、`ChatScreen`、`SettingsScreen`、`PendingScreen` 的 composition-bound 定义都位于 `.cpp` 且标了 `[[huxerui::composable]]`。HCG 将其转成独立 `Scope`，因此 `UseState`/`UseNavigation` 在正确的 composition context 中运行，不是构造期越界读取。
- `State` 都按值捕获；draft 保留完整 `TextEditingValue`，`OnChanged` 写回 owner state；message 动态兄弟使用稳定 `message.id` key。`session.Get()` 的引用只用于当次声明，事件 handler 再按值持有 `shared_ptr`，当前未发现悬空引用。
- 固定构造 `DrawerLayout`，而不是只在 open 时插入/删除，避免 drawer subtree/state 因条件结构丢失；`.Open(drawer_open)` 与 `.OnOpenChanged` 构成正确受控状态。
- `LegacyDrawerStyle()` 已通过 `AppRoot` 的完整 `LineCoffeeThemeDefinition` 调 `theme.Set(...)` 注入，符合 `DrawerStyle` typed Environment contract；不是无效的普通 View modifier。
- navigation motion 为 push `0.28s EaseOut`、pop `0.22s EaseIn`；drawer 为 open `0.18s EaseOut`、close `0.15s EaseIn`。公共 `TweenSpec::duration` 单位确为秒，且 `NavigationMotion` 默认位移恰为 entering `(+1,0)` / covered `(-1,0)`，符合旧版 280/220ms 与 180/150ms 方向/时长。使用内建 motion 会继承 Runtime reduced-motion 处理，无需逐帧写 State。
- Android arm64 Debug 当前全量 native target 已重新生成 HCG/resources 并成功链接 `libhuxerui_app.so`；三个被审文件没有现存编译错误。

### 12.2 仍需处理或明确验收的风险

1. **Windows route 过滤已避免显示禁用页，但 path 约束仍不闭合。** `SettingsScreen` 用 `IfFeatureAvailable<keep_alive>` 去掉 row（`settings_screen.cpp:214-232`），resolver 也在非 Android 把 `keep_alive` 回退到 `SettingsScreen`（`main_screen.cpp:48-58`），因此当前 Windows 不会显示 Keep Alive UI。不过 `AppRoute::keep_alive` 仍可被 Push/restore 进 path，形成“路径是 keep_alive、画面却是 settings”的错误 history/动画/状态。应建立单一 `RouteAvailable(AppRoute, HostPlatform)`，同时约束 row、所有 Push、resolver 和 restored path；resolver fallback 只能作为最后防线。
2. **Android Back 的当前 API 组合合理，但旧版完整优先级尚未闭合。** routed `NavigationStack` 自带 path pop，`DrawerLayout` 是内建受控 drawer；不要再在外层绑定平行 `ViewEvents::BackRequested` 抢先消费。设备测试至少验证：root+drawer open 先关闭 drawer、route page 先 pop、root 且无 overlay 才交给 Activity。以后接入 directory/attachment/bottom-sheet 时，还须达到旧版 `screen → directory → attachment → sheet → drawer → Activity`；仅靠当前 stack+drawer 不能证明这一点。
3. **SafeArea 与旧 edge-to-edge/IME 行为不是同一个 contract。** `src/app.cpp:10-15` 选择 `WindowContentMode::SafeArea`，而旧 Android 是 edge-to-edge 后按 system bars/cutout 和 `max(nav,IME)` 手动 inset。HuxerUI 写法本身合法，但必须在 Android 刘海、三键/手势导航、键盘打开时实测 header/composer；尤其 drawer header 已有固定 top `40dip`，要排除 SafeArea 再次增加顶部留白。若要逐像素复刻，应明确选择 SafeArea 版视觉基线，或改成 EdgeToEdge + 精确 `SafeAreaPadding` 所有权，不能两套同时消费。
4. **宽屏 Drawer 会自动转为 inline，可能破坏 1:1。** 公共 `DrawerStyle` 规定宽度足够时优先 persistent inline。当前 style 的 preferred/min-content 都为 `360dip`（`drawer.cpp:429-444`），约在可用宽达到两者之和后可能把内容压缩并去掉 modal corner/shadow；旧版始终是 overlay，宽 `min(window-48,360)`。Windows 宽窗口需截图验收；若严格复刻，应通过公共 drawer 配置/响应式结构保证仍为 modal，而不是接受默认宽屏形变。
5. **`SettingsCardFrame` 是可工作的自定义 Layout，但不是必要扩展边界。** 它只做 16dip gutter、760dip cap 和水平居中（`settings_screen.cpp:25-60`），公共 `Stack`/`Padding`/`Frame(max_width)` 已可表达。当前实现还隐含“最多一个 child”，并在 unbounded width 下让 card 取 natural width。它已通过 Android 编译，不是即时 blocker；建议在行为冻结后换回 built-in 组合，减少无界/极窄约束和未来 SDK layout 语义的维护风险。
6. **typed route 目前只有 enum，后续详情页不得回退到 side-channel。** Model edit、Extension kind、文件详情等需要参数时，应把 route 升为 equality-comparable `std::variant`/payload value；不要继续用 enum 再从全局 mutable state 取 id，否则 path restore、重复 route 与转场 identity 都会出错。
7. **现阶段功能占位是显式缺口。** resolver 只实现 settings hub 和非 Android 的 keep-alive 防御性回退，其余 route 返回 `PendingScreen`（`main_screen.cpp:48-58`），所以 Android 的 Keep Alive 以及 models/llm/mcp 等 route 虽能正确 push/pop，尚未实现目标页面。它不是 HuxerUI 导航 API 错误，但不能据“路由可达”判为功能完成。

### 12.3 建议门禁

- 保留当前 Android arm64 Debug 编译门禁，并补 Windows 原生编译；platform feature 的 `static_assert` 只能证明模板常量，不能证明 resolver 不显示 Android-only route。
- 添加纯 C++ route 测试：每个平台枚举全部 route，断言 visible rows、`RouteAvailable`、resolver 三者一致，并验证不合法 restored path 被拒绝或净化。
- 添加运行交互测试：drawer open/back、settings/back、drawer→route、IME 打开、窄于 360dip、720/792dip 附近和宽桌面；截图核对 motion 方向、drawer overlay/inline、safe-area 留白。
- 保持 navigation/drawer motion 为 typed Theme style；不要另加 `Transition` modifier 或自写定时器形成第二条动画路径。
