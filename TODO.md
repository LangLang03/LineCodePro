# LineCode C++ 迁移清单

最后更新：2026-09-11，分支 `hui-cpp`。

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
