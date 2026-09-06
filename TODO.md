# LineCode C++ 迁移清单

最后更新：2026-09-06，分支 `hui-cpp`。

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

- [x] 原生测试：13/13 通过。
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

## 尚未接线的现有路由

以下路由目前仍会落入 `PendingScreen`，不能视为已迁移：

- [ ] `tutorial`
- [ ] `mcp`
- [ ] `tool_settings`
- [ ] `extensions`
- [ ] `memory`
- [ ] `data`

`data` 已有可编译的页面、ZIP、JSON 和 SQLite 归档基础代码，但尚未接入 `AppRoot/MainScreen`，也尚未通过归档兼容与破坏性导入测试。

## 数据管理与 `.linecode`

- [ ] 在 `AppRoot` 构造 `SqliteArchiveDatabase` 和 `HuxDataArchiveService`，通过接口注入页面。
- [ ] 将 `AppRoute::data` 接到 `DataSettingsScreen`。
- [ ] 导出前持久化当前会话；导入确认后先停止生成和 Android 保活；成功后才重载应用状态。
- [ ] 严格校验 `manifest.json`：`format=linecode`、版本、容器、数据库标志和 roots。
- [ ] 完整支持旧版 `async-storage.json` 与 `conversations/*.json` 兼容归档；当前 async-storage-only 导入仍明确不支持。
- [ ] 导入必须先完整校验并暂存，再执行覆盖；数据库与工作区恢复要具备原子性或可验证回滚，任何失败不得留下半导入状态。
- [ ] REPLACE 模式正确清理 `home/project/skills`，同时支持旧 `.linecode/{root}` 路径。
- [ ] 拒绝 zip-slip、绝对路径、重复条目、CRC 错误、越界 conversation 文件、缺失 tables 和更高 schema 版本。
- [ ] 为压缩包总大小、解压后大小、条目数、单文件大小和递归深度设置明确上限。
- [ ] 导出脱敏模型 `api_key`、SSH/Web Search secret、敏感 setting key、MCP headers/raw JSON secrets，并增加反向测试证明秘密不在归档中。
- [ ] 增加 ZIP codec、JSON typed cell、SQLite 事务、导入失败不破坏原数据、旧版 fixture、文件选择取消和确认框状态测试。
- [ ] 用旧版和新版实际互导 `.linecode`，逐项核对会话、模型、设置和工作区文件。
- [ ] 按旧版 60dp header、68dp 行、16/12dp padding、12dp 圆角完成同机像素截图。

## 教程

- [ ] 迁移并净化 `tutorial_simple.md` 与 `tutorial_pro.md`，删除控制模式、手机控制和无障碍相关段落，再连续重编号。
- [ ] 实现 C++23 Markdown 文档模型与解析器，覆盖当前教程使用的标题、段落、粗斜体、行内代码、代码块、嵌套列表、引用、分隔线、GFM 表格和裸 URL。
- [ ] 实现模式选择卡、横向章节 chips 和章节跳转；简单模式默认且页面内状态不持久化。
- [ ] 复刻旧版 Markdown 几何：标题 28/24/20sp、正文 16sp、代码 13sp、表格 13sp，以及原边距/圆角/颜色。
- [ ] HuxerUI 目前只公开即时 `ScrollTo/ScrollToItem`；先保证跳转位置准确，再验证是否可在公开 API 内复刻旧版平滑动画。
- [ ] 增加解析、章节映射、代码围栏伪标题、链接和“无障碍关键词不存在”测试。

## 旧版页面/功能完整性审计

这些旧版目的地尚需逐一证明“已等价折叠到现有页面”或单独迁移；不得因 C++ 中没有路由就遗漏：

- [ ] 高级功能（保留非无障碍部分）、SSH、Termux 集成。
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

- [ ] 每个页面至少覆盖默认、选中、展开、弹层、滚动后、空态、加载态、错误态。
- [ ] 固定同一设备、分辨率、密度、语言、主题、字体缩放、系统栏和动画设置。
- [ ] 同时比较截图、UI hierarchy bounds、点击目标和滚动位置；动态时间/容量只屏蔽文字像素，不屏蔽容器几何。
- [ ] 逐项清零用户已反馈的问题：标题/按钮文字居中、输入字垂直居中、设置卡间距/圆角、模型选择抽屉顶部、侧栏两页高度、文件名横向偏移、本地模型 CPU/NPU/自动、测试/保存按钮、许可列表。
- [ ] 重跑全量场景，任何功能失败为 0；所有可稳定区域达到逐像素一致，无法由跨渲染器消除的字体抗锯齿差异必须单独记录证据，不能用整页 mask 掩盖。
- [ ] 在连接的真实 Android 设备上重复关键流程：首次启动、抽屉、真实模型请求、取消生成、文件树、导入导出、日志外部查看、保活设置、重启恢复。

## 最终交付门槛

- [ ] C++/Java/资源格式化与 `git diff --check` 通过。
- [ ] Native、协议、SQLite、归档和 UI 自动化测试全部通过。
- [ ] Android Release 双 ABI、Lint、签名、升级安装验证通过。
- [ ] Windows 构建和非 Android 专属入口验证通过。
- [ ] 全仓搜索确认没有 Accessibility/Phone Control 残留入口或资源文案。
- [ ] 所有旧版页面/功能都有“已迁移、明确排除、或有测试证明被等价合并”的归档记录。
- [ ] 仅在以上清单全部满足后，才能宣布迁移完成。
