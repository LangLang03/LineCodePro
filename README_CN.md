![LineCode Pro](https://socialify.git.ci/LangLang03/LineCodePro/image?description=1&font=KoHo&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2FLangLang03%2FLineCodePro%2Frefs%2Fheads%2Fmain%2F.idea%2Ficon.svg&name=1&pulls=1&stargazers=1&pattern=Circuit%20Board&theme=Auto)

# LineCode Pro

> **一个能塞进口袋的 AI 编程工作台。**
> 在 Android 上接入主流大模型，让它直接读写你的项目文件、跑 Shell、用 SSH 连远程主机，或者接入一个可插拔的 IPC Provider —— 一切都在手机本地完成。

[English](README.md) · [中文](README_CN.md)

[![许可证: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84.svg)](app/build.gradle.kts)
[![当前版本: 1.2.8-max](https://img.shields.io/badge/version-1.2.8--max-success.svg)](app/build.gradle.kts)
[![Java 11 only](https://img.shields.io/badge/code-Java%2011-orange.svg)](#项目结构)

---

## 目录

1. [LineCode Pro 是什么？](#linecode-pro-是什么)
2. [它能做什么](#它能做什么)
3. [功能亮点](#功能亮点)
4. [v1.2.8-max 更新内容](#v128-max-更新内容)
5. [项目结构](#项目结构)
6. [安装](#安装)
7. [上手指南](#上手指南)
8. [执行模式](#执行模式)
9. [支持的模型协议](#支持的模型协议)
10. [工具系统](#工具系统)
11. [扩展 LineCode](#扩展-linecode)
12. [从源码构建](#从源码构建)
13. [隐私与安全](#隐私与安全)
14. [参与贡献](#参与贡献)
15. [许可证](#许可证)

---

## LineCode Pro 是什么？

**LineCode Pro** 是一款面向 Android 8.0+ 的本地化 AI 编程助理。它围绕"一个 Activity + 一段流式聊天 + 一套真正可执行的工具循环"组织代码。你只要指定一个项目目录（本地、SSH 远程，或第三方 IPC Provider），接好一个大模型，模型就能读取、编辑、glob、新建、删除文件，能跑 Shell、能抓取和搜索网页、能理解和生成图片，还能分派子任务。

LineCode 不是一个轻量聊天客户端，它是一个**完整的编程工作台**：系统提示、工具注册表、上下文管理、Diff 存档、文件树、项目选择器、SSH / IPC 管道、导入导出归档、扩展框架、安全策略……全部跑在 App 内部。除非你自己接到远程模型，否则项目文件不会离开你的手机。

应用包名是 `cn.lineai`，仓库采用 14 个 Gradle 模块：`:core-model`、`:core-api`、`:core-security`、`:ui-theme`、`:markdown`、`:data`、`:feature-tool`、`:feature-model`、`:feature-ssh`、`:feature-share`、`:tool-ui`、`:app`、可复用库 `:ipc` 与示例 Provider `:terminal-provider`；`:build-logic` 作为 composite build 引入。

---

## 它能做什么

### 对话

- 同一个聊天界面下支持**多种模型协议**：OpenAI 兼容 HTTP API、Anthropic Messages、OpenAI Codex Responses、本地 GGUF 推理。
- 推理块（`<think>…</think>`）会被 `ThinkTagParser` 单独抽出来，和最终回答分块渲染。
- 每轮助手消息都按发生顺序渲染为可展开的处理时间线：思考、工具分组、重试、错误和 Agent 进度集中展示，最终答复保持独立；处理耗时会随消息持久化。
- 流中的工具调用文本由 `ToolCallTextParser` 解析并派发；模型请求做的每件事，在真正执行前都会先展示给你看。
- 系统提示由 `feature-model/src/main/assets/prompts/*.txt` 中的模板拼装：语气（聊天 / 编程）变体、上下文压缩、工作目录、学习上下文、模型身份。你可以在设置里覆盖语气、工作目录、身份块、提示模板。
- 长对话由 `ContextCompactionService` 后台用**当前模型本身**做**动态压缩**（50% 软触发 + 80% 硬触发）；通过 `memory_update` 工具或记忆管理界面保存的长期知识，由 `LearningContextRepository` 在下一次会话中喂回上下文。
- **图片附件** - 在输入框右侧点图片按钮打开系统选择器；选中的图片经压缩后 base64 编码，按协议格式输出（OpenAI `image_url`、Anthropic `image.source.base64`、Codex `input_image`）。

### 工具执行

模型可以调用 `ToolRegistry` 里的全部工具，并支持自动、确认和只读执行模式：

| 分类 | 内置工具 |
| ---- | -------- |
| 文件系统 | `file_read`、`file_write`、`file_edit`、`file_delete`、`glob`、`list_directory` |
| Shell | `shell_execute`（经 Termux 或 IPC Provider） |
| 网络 | `web_search`、`web_fetch` |
| 媒体 | `image_understanding`、`image_generation` |
| 子任务 | `agent`、`agent_pipeline`、`agent_output`（分派给另一个 LLM 循环） |
| 效率 | `todo_update` |

每个会触碰文件的工具都走 `FileToolPathPolicy` 路径校验，模型只能动你授权的目录里的内容。Shell 调用走 Termux 或 IPC Provider —— **永远不在 App 自身进程里跑命令**。确认模式支持单次允许和永久精确匹配授权，范围包含执行模式、工具、完整命令和工作目录。

### 上下文保护

- **动态压缩** - `ContextCompactionService` 在上下文窗口占用 50% 时触发软压缩（最早 70% 被总结、最近 30% 原文保留），80% 时硬压缩兜底；转录文本按 256KB 分段拼接防 OOM。
- **工具结果截断** - `ToolResult.truncateContent()` 强制 50KB 上限：超过 50KB 中间截断（前 25KB + 截断提示 + 后 25KB）。Shell 输出同样截断。
- **大文件防护** - `FileReadTool` 使用 KB 参数（`start_kb` / `end_kb`，50KB 上限）；超过 1MB 的文件直接拒绝读取。
- **模型上下文大小** - 支持 `k` / `m` 单位输入（如 `128K`、`1m`），由 `ContextSizeParser` 解析；旧 `{id}[{size}]` 格式通过 `ModelContextParser` 兼容。

### 工作区与文件

- **本地** —— 通过系统 Storage Access Framework 选择器选一个目录，App 会在重启后记住。授权 `MANAGE_EXTERNAL_STORAGE` 后，模型可以对真实项目目录做 glob。
- **SSH** —— 通过 `jsch` 远程浏览、读写、执行；凭据加密存在设置仓库里。
- **IPC Provider（可插拔）** —— 绑定到任何对外暴露 `cn.lineai.action.IPC_TERMINAL_PROVIDER` 动作和 `ITerminalProviderService` AIDL 接口的第三方 App。仓库自带的 `terminal-provider` 模块就是参考实现，把命令和文件操作跑在独立进程里。
- **自定义 Agent / MCP** —— 在 **扩展** 页面注册 `agentx_` / `mcpx_` 工具，模型可以像调用内置工具一样调用它们。

### Diff 与审查

- 每次成功的 `file_write` / `file_edit` 都会在 `DiffRepository` 里生成一条 `DiffRecord`；聊天流里能看到内联变更，**存储 → Diff 历史** 页面可以回放。
- Shell 跑在自定义的 `ToolCallShellView` 中流式输出；Agent 跑在 `ToolCallAgentView` / `ToolCallAgentPipelineView` 卡片中实时显示进度。

### 导入导出

- 一个 `.linecode` 归档里包含整库（对话、消息、记忆、设置、扩展）和工作区文件。**敏感信息 —— 模型 API key、SSH 凭据、网页搜索 key、敏感 MCP 请求头 —— 在写入前由 `ArchiveSecretRedactor` 自动剥离**。
- 导入流程需要显式确认，因为它会覆盖本地数据。

### 存储

- 所有持久化都走 SQLite（`LineCodeDatabase` 单例，schema 在 `LineCodeSchema`）和"一个关注点对应一个仓库"的设计。控制器不直接操作数据库。

---

## 功能亮点

- **一个聊天，多家后端。** OpenAI 兼容、Anthropic Messages、Codex Responses、本地 GGUF 在同一 UI 内任意切换。
- **真正能干活的工具循环。** 模型能读、改、glob、新建、删除文件，能跑 Shell，能抓取和搜索网页，能看图，能生图，能递归调子任务 —— 全部受自动、确认或只读模式控制。
- **可在任意目录工作。** 本地（SAF + 可选 `MANAGE_EXTERNAL_STORAGE`）、远程（jsch SSH）、或第三方 IPC Provider。
- **支持自定义扩展。** 自定义 Agent（`agentx_*`）和 MCP-HTTP 工具（`mcpx_*`）即配即用。
- **可插拔 IPC Provider。** 把 Shell 和文件操作放到独立进程里以做安全隔离。可以把 Provider 当成普通 Android App 上架，详见 [`ipc/README.md`](ipc/README.md)。
- **记忆与上下文。** 动态压缩（50% 软触发 + 80% 硬触发）+ 分段拼接转录。长期知识会在下一次会话注入。
- **上下文溢出防护。** 工具结果 50KB 中间截断；文件读取使用 KB 参数；超过 1MB 的文件直接拒绝。
- **免费网页搜索。** 内置 Bing RSS 搜索 Provider，无需任何 API key。
- **默认隐私优先。** URL 白名单、严格 `network_security_config.xml`、导出文件去敏、内置浏览器默认关闭 JavaScript。
- **纯 Java 写在骨子里。** 无 Kotlin 运行时、无 XML 布局 —— App 全部由 Java 11 写成，便于审计。

## v1.2.8-max 更新内容

当前版本重点更新了更紧凑的 Android 原生界面和更清晰的模型工作流：

- **原生 UI 重写。** 聊天、设置、抽屉、底部弹窗、工具卡片、主题、Markdown、宽屏布局、大字体和窄屏操作统一使用共享的自适应 View 组件。
- **处理时间线。** 思考、工具组、Agent 卡片、重试、错误、工作状态和最终答复按实际发生顺序展示，过程默认紧凑折叠，处理耗时会持久化。
- **更安全的执行授权。** 自动、确认、只读模式在主流程与 Agent 内统一生效；永久授权精确匹配完整的执行范围、工具、命令和工作目录。
- **Skill Hub。** 可在应用内搜索和浏览社区 Skills，查看文件、版本、评论、评测、预览和安全提示，并完成登录、安装、发布与管理。
- **提示词与上下文稳定性。** 固定系统前缀、稳定的工具 / 扩展排序、规范化 JSON、附件归属原消息，以及压缩后的用量基线修复，改善缓存复用并避免重复压缩。
- **发版验证。** Robolectric 4.16 原生 View 测试覆盖布局、时间线、权限、提示词、Skill Hub 流程和 Diff；v1.2.8-max 更新日志记录相关模块单元测试 **528 项通过**，主应用 Debug / DebugUserCert 构建和 Lint 也已通过。

---

## 项目结构

```
LineCode/
├── build-logic/               # composite build，linecode.convention 约定插件
├── core-model/                # :core-model — 纯数据类 / DTO / 枚举
│   └── src/main/java/cn/lineai/model/    # ModelConfig、ThemePalette、OutputSettings、
│                                          # ContextSizeParser、ModelContextParser、ChatMessage、ToolResult…
├── core-api/                  # :core-api — 接口抽象
│   └── src/main/java/cn/lineai/           # ToolInfo、ToolNames、ToolCategory、ToolDisplayCategory、
│                                          # ModelServiceProvider、WebSearchProvider
├── core-security/             # :core-security — UrlPolicy、SimpleHttpClient
├── ui-theme/                  # :ui-theme — LineTheme 及共享 UI 基础设施
│   └── src/main/java/cn/lineai/ui/theme/ # LineTheme（被约 80 个文件引用）
├── markdown/                  # :markdown — Markdown 渲染（commonmark + GFM tables）
│   └── src/main/java/cn/lineai/ui/markdown/
├── data/                      # :data — 仓库 / 数据库 / 导入导出 / 错误日志
│   └── src/main/java/cn/lineai/
│       ├── data/db/                      # LineCodeDatabase、LineCodeSchema、迁移
│       ├── data/repository/              # 所有仓库（对话、模型、Diff、扩展…）
│       ├── data/importer/                # ArchiveSecretRedactor、LineCodeDatabaseArchive
│       ├── log/                          # ErrorLog、ErrorLogRedactor
│       └── workspace/                    # WorkspacePaths
├── feature-tool/              # :feature-tool — BaseTool、ToolRegistry、全部内置工具
│   └── src/main/java/cn/lineai/tool/
│       ├── BaseTool.java、ToolRegistry.java、ToolContext.java、ToolArgsCleaner.java
│       └── builtin/                     # FileReadTool、FileWriteTool、FileEditTool、ShellExecuteTool、
│                                          # WebSearchTool、WebFetchTool、AgentTool、AgentPipelineTool…
├── feature-model/             # :feature-model — 协议、ModelClient、ContextManager、提示词模板
│   └── src/main/
│       ├── assets/prompts/               # 全部 .txt 提示词模板（系统、压缩、Agent…）
│       └── java/cn/lineai/
│           ├── ai/protocol/              # ModelProtocol、OpenAiCompatibleProtocol、AnthropicMessagesProtocol…
│           ├── ai/prompt/                # SystemPromptProvider、StringTemplate
│           ├── context/                  # ContextManager、ContextCompactionService
│           └── ai/message/               # SystemModelMessage、UserModelMessage 等
├── feature-ssh/               # :feature-ssh — SshService、SshConnectionPool、TermuxHelper
├── feature-share/             # :feature-share — 导出 / 分享 / PDF
├── tool-ui/                   # :tool-ui — 可复用工具调用卡片和 View 注册表
├── app/                       # :app — MainActivity、MainCoordinator、控制器、UI 组件
│   ├── build.gradle.kts
│   ├── lint.xml
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── aidl/                  # IPC AIDL 存根
│       │   ├── java/cn/lineai/
│       │   │   ├── MainActivity.java
│       │   │   ├── mvp/               # MainCoordinator + 各关注点控制器
│       │   │   ├── ui/                # Java 写的视图（不 inflate XML）
│       │   │   └── tool/             # ToolExecutor、ToolExecutionCoordinator（装配）
│       │   └── res/                   # 矢量图、字符串、主题
│       ├── test/                      # JUnit 4 单元测试
│       └── debugUserCert/             # 旁加载 build flavor
├── ipc/                       # :ipc — 可复用 Android 库（cn.lineai.ipc）
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml        # <permission> cn.lineai.permission.IPC_TERMINAL_PROVIDER
│       ├── aidl/                      # IBaseIpcService + terminal 接口
│       └── java/cn/lineai/ipc/        # BaseIpcProvider、IpcProviderManager、注册表、扫描器
├── terminal-provider/         # :terminal-provider — 示例 Provider App（cn.lineai.terminalprovider）
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml        # <service> 上强制 android:permission
│       └── java/cn/lineai/terminalprovider/TerminalProviderService.java
├── docs/                      # 合规审计、威胁模型
├── gradle/libs.versions.toml  # 版本目录
├── settings.gradle.kts
├── build.gradle.kts
├── CLAUDE.md                  # 面向 AI / 评审者的架构圣经
├── README.md / README_CN.md   # ← 你正在看这里
└── LICENSE
```

### 30 秒看架构

* `MainActivity` 是唯一的 Activity，它实例化 `MainCoordinator`（Presenter）和 `MainChatView`（View），View 完全由 Java 代码构建 —— 不 inflate XML；`lint.xml` 故意屏蔽 `ViewConstructor`。
* `MainCoordinator` 实现 `MainUiController`，把行为分派给 `cn.lineai.mvp.*` 下各关注点控制器（聊天、生成、工具执行、模型管理、设置、页面、权限模式、归档、项目面板、SSH 文件树、IPC 文件树、文件操作、Agent 执行、上下文压缩、手机控制、错误日志、存储维护）。UI 状态走 `ChatUiStateAssembler` → `ChatUiState` → `MainContract.View.render(...)` 通路。
* `cn.lineai.ai.protocol.ModelProtocol` 是流式 / Completion 接口，`ModelProtocolFactory` 按 `ModelProtocolType` 分派（`OPENAI_COMPATIBLE`、`CODEX_RESPONSES`、`ANTHROPIC_MESSAGES`、`LOCAL_GGUF`）。每个协议通过 `supportsNativeTools()` / `supportsContextCompaction()` / `supportsImageGeneration()` / `supportsImageUnderstanding()` 声明自身能力。
* `BaseTool`（name、description、category、JSON schema、`execute(JSONObject, ToolContext)`）是工具契约；`ToolRegistry` 注册内置并热加载用户扩展；`ToolExecutionCoordinator` + `ToolExecutor` 执行；`PermissionModeController` + `ToolReviewListener` 控制逐条审批。
* 模块依赖规则：`:app` → `:feature-*` → `:data` → `:core-model` / `:core-api` / `:core-security`。`ai ↔ tool` 循环依赖已打破：`:feature-tool` 不得引用 `cn.lineai.ai`，`:feature-model` 只引用 `:core-api` 中的 `ToolInfo` / `ToolNames` / `ToolCategory`。

完整架构圣经（控制器、工具系统、上下文管理、SQLite schema、安全模型）见 [`CLAUDE.md`](CLAUDE.md)。

---

## 安装

**方式 A —— 下载预编译 APK。** 在 Releases 页面下载最新的 `LineCode Pro <version>.APK`，旁加载安装。`release` 版本用私钥签名（见 `signing.properties`）；`debug` 版本用 debug 证书签名，适合临时测试。

**方式 B —— 从源码构建。** 见 [从源码构建](#从源码构建)。

> **关于存储权限的提示。** LineCode 默认申请 `MANAGE_EXTERNAL_STORAGE`，这样 AI 才能对真实项目目录做 glob 和编辑。如果你不愿授权，也可以只通过系统文档选择器选目录 —— 大部分功能仍然能用，但对超大目录的 glob 会变慢。

---

## 上手指南

1. **授予存储权限。** 首次启动时 LineCode 会请求所需的存储权限，授权后文档选择器才可用。
2. **添加模型。** 抽屉 → **模型** → **新增模型**。挑一个协议：
   - **OpenAI 兼容** —— Base URL、API key、模型名。适用于 OpenAI、DeepSeek、通义千问、Moonshot、智谱、Groq、Ollama、llama.cpp server …
   - **Anthropic Messages** —— Base URL、API key、模型名（Claude 4.x 与 3.x）。
   - **Codex Responses** —— Base URL、API key、模型名。
   - **本地 GGUF** —— 设备本地 llama.cpp 推理。
3. **选一个工作区。** 抽屉 → **项目 → 打开外部目录**（本地）。路径会在重启后记住；也可以在 **设置 → SSH** 加一台远程主机，或在 **设置 → MCP execution mode → Terminal Provider** 绑定一个 IPC Provider。
4. **开始聊天。** 输入问题或任务。模型会发出工具调用 —— 你可以逐条审批，也可以点 **本次会话自动确认** 放手让它跑。
5. **可选集成。**
   - **Termux。** 从 F-Droid 安装 Termux，授权 `RUN_COMMAND`，`shell_execute` 工具就会走它。
   - **SSH。** 在 **设置 → SSH** 添加主机；项目抽屉里能看到远程文件。
   - **IPC Provider。** 安装 `terminal-provider`（或任何第三方 Provider），在 **设置 → MCP execution mode → Terminal Provider** 启用。LineCode 每次冷启动会自动重连。
   - **扩展。** 在 **扩展** 页面配置自定义 Agent / MCP 工具。

### 拿现成模型试一下

如果你手上有 Ollama 或 llama.cpp server，指向它即可：协议选 `OpenAI 兼容`，Base URL 填 `http://10.0.2.2:11434/v1`（模拟器）或 `http://<笔记本 IP>:11434/v1`（真机），API key 填 `ollama`，模型名 `llama3.1:8b`。完全本地，无需账号。

---

## 执行模式

LineCode 在 **设置 → MCP execution mode** 里提供三种 Shell / 文件工具执行模式：

| 模式 | Shell 与文件操作实际跑在哪里 | 适用场景 |
| ---- | -------------------------- | -------- |
| **Local（本地）** | 经 Android SAF 选择器，在 App 自己的上下文里跑 | 设备本地任意目录，无需 Termux |
| **SSH** | 通过 `jsch` 连到配置的 SSH 主机 | 远端开发机 |
| **Terminal Provider（IPC）** | 绑定到一个第三方 Android App，通过 AIDL 调度（仓库自带 `terminal-provider` 作为参考实现） | 独立进程沙箱化 Shell，可插拔 —— 你可以发布自己的 Provider |

三种模式都通过统一的 `IpcFileTreeStore` / `SshFileTreeStore` / `FileTreeStore` 接口暴露，所以文件树、附件选择器、模型看到的是同样的 UI，不管字节实际在哪里。

---

## 支持的模型协议

| 协议 | 实现类 | 已验证可用的模型 / 服务 |
| ---- | ------ | --------------------- |
| OpenAI 兼容 | `OpenAiCompatibleProtocol`（默认） | OpenAI、DeepSeek、通义千问、Moonshot、智谱、Groq、Ollama、llama.cpp server、LM Studio … |
| Anthropic Messages | `AnthropicMessagesProtocol` | Claude 4.x 与 3.x |
| Codex Responses | `CodexResponsesProtocol` | OpenAI Responses API |
| 本地 GGUF | `LocalGgufProtocol` | 设备本地 llama.cpp |

加新模型通常只要填一个 Base URL 和 Key。能力探针 `OpenAiCompatibleCapabilities` 会探测工具支持，模型会被告知它能调用哪些工具。

---

## 工具系统

`BaseTool` 是契约，每个工具都要暴露：

* `name`、`description`、`category`
* 入参的 JSON schema
* `execute(JSONObject args, ToolContext ctx)` 返回 `ToolResult`

内置工具在 `feature-tool/src/main/java/cn/lineai/tool/builtin/`：

```
FileReadTool      FileWriteTool      FileEditTool      FileDeleteTool
GlobTool          ListDirectoryTool  ShellExecuteTool
ImageUnderstandingTool  ImageGenerationTool  WebSearchTool  WebFetchTool
AgentTool  AgentPipelineTool  AgentOutputTool  TodoUpdateTool
```

执行由 `ToolExecutionCoordinator` + `ToolExecutor` 驱动；每次调用都要过 `PermissionModeController` + `ToolReviewListener`，支持自动、确认和只读模式。确认模式提供单次允许或永久精确匹配授权，授权范围包含执行模式、工具、命令和工作目录。

工具结果由 `ToolResult.truncateContent()` 在 50KB 处截断（首尾各 25KB）后进入上下文。文件读取使用 KB 参数，50KB 范围上限；超过 1MB 的文件直接拒绝。Shell 输出超过 50KB 同样中间截断。

聊天流的工具调用由 `app/src/main/java/cn/lineai/ui/component/toolcall/` 下的自定义卡片渲染（`ToolCallReadView`、`ToolCallWriteView`、`ToolCallShellView`、`ToolCallAgentView`、`ToolCallAgentPipelineView`、`ToolCallGenericView`…）。新工具只要在 `ToolCallUtils` 注册，就能拿到自己的卡片。

---

## 扩展 LineCode

三类一等公民的扩展点：

1. **自定义 Agent**（`agentx_*`）—— 实现 `CustomAgentExtensionTool`，模型可以像调用内置工具一样调用它。
2. **自定义 MCP-HTTP 工具**（`mcpx_*`）—— 实现 `CustomMcpHttpTool`，模型可以走 HTTP 调用，per-tool 请求头存在设置仓库。
3. **自定义 IPC Provider** —— 在任意 Android App 里实现 `IBaseIpcService` / `ITerminalProviderService` AIDL，发布为普通 APK，LineCode 就会自动发现、绑定、把 Shell + 文件操作路由过去。完整协议与可运行示例见 [`ipc/README.md`](ipc/README.md)。

以上三类都通过 `ExtensionRepository` 持久化，由 `ToolRegistry.reloadExtensions()` 热加载。

扩展页面内置 **Skill Hub**，可以发现、审查、安装和管理社区 Skills。URI、GitHub 与 Skill Hub 安装共用临时来源清理和文件管理流程。

---

## 从源码构建

环境要求：

* **JDK 11** 或更新
* 已安装 **Android SDK** 平台 36
* 本仓库一份 checkout

所有 Gradle 命令都走 wrapper。`settings.gradle.kts` 强制 `FAIL_ON_PROJECT_REPOS`，并接入了腾讯云 Maven 镜像 —— 新增仓库时请保持。

### 快速构建

```bash
# Debug 构建（debug 证书签名）
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Debug 构建（debug 证书 + 重命名，便于旁加载）
./gradlew :app:assembleDebugUserCert
# → app/build/outputs/apk/debugUserCert/export/LineCode-user-cert-debug.apk

# 单元测试（需要 Android 资源 / View 行为时使用 JUnit 4 + Robolectric 4.16）
./gradlew :app:testDebugUnitTest

# 单个测试类
./gradlew :app:testDebugUnitTest --tests "cn.lineai.tool.ToolRegistryTest"
# 追加 .methodName 跑单个方法

# 静态分析（自定义规则在 app/lint.xml）
./gradlew :app:lintDebug
```

### Release 构建

Release APK 需要在仓库根放一份 `signing.properties`：

```properties
storeFile=/绝对路径/your.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

`validateReleaseSigning` 任务会拒绝用 debug 证书签 release。配置好后：

```bash
./gradlew :app:assembleRelease
# → app/release/LineCode Pro <versionName>.APK
```

Release 流水线刻意加强：

* 8192 项 R8 混淆字典，由 `generateReleaseObfuscationDictionary` 生成（`app/build/generated/r8/obfuscation-dictionary.txt`）
* `exportReleaseApk` 把产物重命名为 `LineCode Pro <versionName>.APK`，放到 `app/release/`
* `purgeReleaseSymbolFiles` 删除 `outputs/mapping/release` 与 `outputs/native-debug-symbols/release`

改版本号要同步更新 `app/build.gradle.kts` 的 `releaseVersionName` 和 `defaultConfig.versionCode`；APK 文件名由 `releaseVersionName` 派生。

### 单独构建 IPC 库与示例 Provider

```bash
./gradlew :ipc:assembleDebug
./gradlew :terminal-provider:assembleDebug
```

`terminal-provider` 模块产出普通 debug APK（`terminal-provider/build/outputs/apk/debug/terminal-provider-debug.apk`），可以与主 App 一起安装来演示 IPC 链路。

### 发版前的强制门禁

按 [`docs/android-compliance-audit.md`](docs/android-compliance-audit.md) 要求，以下命令必须全部通过：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

---

## 隐私与安全

* **导出文件去敏。** 模型 API key、SSH 密码 / 私钥 / passphrase、网页搜索 key、敏感 MCP 请求头都会在 `.linecode` 导出时由 `ArchiveSecretRedactor` 剥离。新增敏感字段时记得同步扩展它。
* **出网请求全部走 `UrlPolicy`。** 内置浏览器、外链打开、模型 HTTP、模型目录、网页抓取 / 搜索、自定义 MCP HTTP 都要过白名单。明文 HTTP 仅放行 `localhost`、`127.0.0.1`、`10.0.2.2`，与 `res/xml/network_security_config.xml`（`cleartextTrafficPermitted="false"` 默认）一致。
* **内置 WebView** 禁用 `file://` / `content://`、禁止混合内容、默认关闭 JavaScript，直到用户主动开启。
* **IPC 权限模型。** Provider 必须在 `<service>` 上声明 `android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER"`；主 App 声明同名 `<permission>` 和 `<uses-permission>`。没有权限的调用方会在 Android 框架层被直接拒掉，Provider 端不必再做校验。
* **绕过路径保护。** 安全设置页提供 `OutputSettings.bypassPathProtection` 开关（默认关闭）；开启后 `FileToolPathPolicy` 跳过工作区边界校验。开启时会显示警告对话框。所有文件类工具自动遵守此标志。
* **Release 加固。** R8 + 8192 项混淆字典，剥离 LineNumberTable、native debug symbols、mapping 文件。Debug 证书签 release 会被 `validateReleaseSigning` 拒绝。

完整威胁模型与已接受风险见 [`docs/android-compliance-audit.md`](docs/android-compliance-audit.md)。

---

## 参与贡献

欢迎提 Issue、想法和 PR。写代码前请注意：

* **纯 Java。** `app/src/main/java` 全部是 Java；Kotlin stdlib 被刻意排除在运行时 classpath 外，引入 Kotlin 传递依赖会在运行期静默失败。`ipc/` 与 `terminal-provider/` 同样。
* **视图用 Java 写，不用 XML。** `lint.xml` 故意屏蔽 `ViewConstructor` 与 `IconDuplicates`。
* **选择最低层模块。** 加新代码时挑能容纳它的最低层模块：DTO → `:core-model`；接口 → `:core-api`；UI 基础设施 → `:ui-theme`；新工具 → `:feature-tool`；新协议 → `:feature-model`。库模块不要回头引用 `:app`。
* **通过控制器扩展。** 新增聊天或工具行为时，扩展 `cn.lineai.mvp.*` 下对应的控制器，状态走 `ChatUiStateAssembler` → `ChatUiState` → `MainContract.View.render(...)`。不要绕过控制器去直接动 View。
* **测试镜像包路径。** `app/src/test/java/cn/lineai/...` 与生产代码同包名。测试使用 JUnit 4、`org.json`，并在需要 Android 资源 / View 行为时使用 Robolectric 4.16（无 Mockito）。仓库或控制器单测优先用 sibling test 里已有的内存 fake。Feature 模块有各自独立的 `feature-*/src/test/java`。
* **发 PR 前跑门禁：**

  ```bash
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
  ```

---

## 许可证

LineCode Pro 是自由软件，按 **GNU 通用公共许可证 v3.0 或更高版本** 发布。

```
Copyright (C) 2026 langlang03 <jiyu03@qq.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

许可证全文见 [`LICENSE`](LICENSE)，在线副本：<https://www.gnu.org/licenses/gpl-3.0.txt>。

随 APK 一起分发的第三方库各自遵循自己的许可证：commonmark（BSD-2）、JSch（BSD 风格）、org.json（JSON License）。
