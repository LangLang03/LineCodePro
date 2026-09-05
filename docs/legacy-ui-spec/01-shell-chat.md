# 旧版 UI 复刻规范 A：主壳、聊天、消息、输入与工具卡片

> 目标：为 LineCode → LineCodePro 的 C++/HuxerUI 迁移固定旧版视觉与交互基线。本文件描述“当前代码实际做什么”，不是重新设计建议。
>
> 旧源码根目录：`/home/LangLang/AndroidStudioProjects/LineCode`。下文的相对路径均相对此目录；`文件:行号`为审计时的旧项目行号。

## 0. 范围、边界与强制删除项

本分区覆盖：聊天主壳、header、抽屉、会话历史、抽屉文件入口、消息列表、用户/助手消息、assistant turn、Markdown、代码块、diff、tool cards、输入栏、slash command popup、主聊天中的工具批准占位以及主壳对各种 overlay 的调度。

不在本分区重复定义的内容：通用 `BottomSheetView`、目录/附件选择器的内部完整视觉、所有通用 dialog/menu/toast/banner/loading/error/empty/file/tool-review 页面。这些由分区 C 的规范作为唯一权威；本文件只固定它们在主壳中的层级、触发点与返回键顺序。

**必须删除且不得复刻：Accessibility / Phone Control。** 旧入口仅列作删除验收：

- header 的 shield/“Permission”入口：`HeaderView.java:56-61` → `MainChatView.java:172-175` → `MainCoordinator.java:330-333`。注意该入口现在打开的是一般工具权限 sheet，不等同于无障碍本身；若产品仍需普通工具授权，应改名/保留普通授权，但不得通向 Phone Control。
- 输入栏上下文菜单中的 Permission：`ComposerView.java:649`。
- `/control` 命令、Control mode 选择项与标签：`SlashCommandCatalog.java:84-85`、`ComposerView.java:1095-1097,1260,1277-1279`。
- 主壳注册：`MainChatView.java:462`；页面工厂：`ScreenFactories.java:668-710`；页面：`PhoneControlScreenView.java`。
- 高级功能入口：`AdvancedFeaturesScreenView.java:38`。
- 工具卡工厂：`tool-ui/.../PhoneControlToolCallViewFactory.java:7-16`；注册：`MainDependencies.java:258`；分类判断：`ToolCallUtils.java:85-87`。
- 服务/仓储/依赖注入：`LineCodeAccessibilityService.java`、`AccessibilityStateProvider.java`、`PhoneControlController.java`、`PhoneControlRepository.java`、`MainDependencies.java:153-161`；资源 `app/src/main/res/xml/accessibility_service_config.xml` 与所有 `screen_phone_control_*`/`tool_group_phone_control_*` 字符串。

本文件出现的 `setContentDescription`、accessibility heading/live-region 等 Android 辅助语义不是迁移要求；仅迁移它们承载的可见文字或交互，不迁移 Accessibility Service/Phone Control 能力。

## 1. 全局视觉基线

### 1.1 默认 dark token

| token | 默认值 | 用途 |
|---|---:|---|
| `BG` | `#000000` | app、header、list、drawer、screen host |
| `SURFACE` | `#0A0A0A` | 次级块 |
| `SURFACE_ELEVATED` | `#141414` | 卡片、选中 tab、底部操作栏 |
| `SURFACE_LIGHT` / `INPUT_BG` | `#1C1C1E` | icon box、输入 panel、chip |
| `ACCENT` | `#30D158` | 主按钮、活动状态、链接、diff add |
| `ACCENT_DIM` | `#1A3A2A` | 深色强调面 |
| `ACCENT_MUTED` | `rgba(48,209,88,.102)` | 多选行背景 |
| `ACCENT_MUTED_2` | `rgba(48,209,88,.149)` | pressed 背景 |
| `USER_BUBBLE` | `#0A84FF` | 用户气泡 |
| `TEXT` | `#FFFFFF` | 主文字 |
| `TEXT_SECONDARY` | `#8E8E93` | 次要文字/图标 |
| `TEXT_TERTIARY` | `#636366` | 弱化信息 |
| `TEXT_ON_COLOR` | `#FFFFFF` | 彩色按钮文字 |
| `BORDER` | `#1C1C1E` | 深边框/分隔线 |
| `BORDER_LIGHT` / `CODE_BORDER` | `#2C2C2E` | 卡片/代码描边 |
| `CODE_BG` | `#151515` | code/diff/tool detail |
| `DANGER` | `#F85149` | 错误/删除 |
| `WARNING` | `#FF9F0A` | context 80%+、排队提示 |
| `SUCCESS` | `#30D158` | 完成/diff add |
| `OVERLAY` | `rgba(0,0,0,.647)` | drawer backdrop |
| `DIFF_ADD_BG` | `rgba(48,209,88,.18)` | 新增行背景 |
| `DIFF_DEL_BG` | `rgba(255,69,58,.18)` | 删除行背景 |
| `DIFF_ADD_TEXT` | `#30D158` | 新增行文字 |
| `DIFF_DEL_TEXT` | `#FF453A` | 删除行文字 |

依据：`ui-theme/.../LineTheme.java:13-42`。运行时必须从 palette 整组刷新 token，而不是把以上颜色散落硬编码；旧版支持 system/light/dark/coffee/vscode/githubDark/gruvbox/highContrast/custom，见 `core-model/.../ThemePalette.java:6-15,159-168,273-365`。

### 1.2 密度、字阶、形状

- 所有标注尺寸为 dp；文字为 sp。间距常量：`XS=4, SM=8, MD=12, LG=16, XL=20, XXL=24`。字阶：`11,13,16,17,20,22,26sp`。依据 `LineTheme.java:44-57`。
- 所有 token 边框都是 `1dp`（至少 1px）；`roundedStroke` 见 `LineTheme.java:103-113`。
- 默认 user bubble 圆角 `18dp`；通用 pressable 正常态 `SURFACE_ELEVATED + 12dp + BORDER_LIGHT`，pressed 为 `ACCENT_MUTED_2 + 12dp`；field focus 为 `INPUT_BG + 12dp + TEXT_SECONDARY` 描边，非 focus 无描边。依据 `LineTheme.java:129-145`。
- `LineTheme.text` 默认去 font padding、额外行距 `2dp`、Android Q+ simple break strategy；medium 为 `sans-serif-medium`。依据 `LineTheme.java:158-177`。
- `IconButtonView.setIconSizeDp(container, glyph)` 不是布局大小；它按实际 view 宽高把 glyph 居中，取 `min(width,height,glyph)`。因此复刻时必须同时遵守外层 LayoutParams 与 glyph 尺寸。依据 `IconButtonView.java:193-245`。

## 2. 主壳 MainChatView

### 2.1 精确 view tree 与层级

```text
MainChatView : FrameLayout [BG]
├─ contentView : LinearLayout(VERTICAL, TOP, max-width 792dp, centered)
│  ├─ HeaderView : MATCH × WRAP
│  ├─ ChatMessageListView : MATCH × 0, weight=1
│  ├─ ComposerView : MATCH × WRAP
│  └─ ToolApprovalView : MATCH × WRAP (默认 GONE；出现时 Composer GONE)
├─ screenHost : FrameLayout [BG, full-screen, 默认 GONE]
├─ DrawerView : full-screen overlay
├─ BottomSheetView : full-screen overlay
├─ DirectoryPickerSheetView : full-screen overlay
└─ AttachmentPickerSheetView : full-screen overlay
```

依据：基础容器 `MainChatViewLayoutBuilder.java:65-84`；header/message/composer/approval `MainChatView.java:147-158,187-190,239-243,330-337`；overlay add 顺序 `MainChatView.java:385,398,417,437`。这是绘制 z-order；后加入的 attachment picker 在最上方，显示单独 screen 时 `screenHost.bringToFront()` 又会升到最顶层（`MainChatView.java:697-701`）。

### 2.2 尺寸、insets、显示状态（纯 UI）

- `contentView` 宽度为 `min(parent width,792dp)`，在 root 中水平居中；高度全屏。`screenHost` 全屏，不做 792dp 限宽。依据 `MainChatViewLayoutBuilder.java:65-84`。
- API 30+：content 和 screenHost 的 padding 同步为 systemBars/displayCutout 的 left/top/right，bottom 为 `max(systemBars.bottom, IME.bottom)`；API <30 此 helper 不处理。依据 `MainChatViewLayoutBuilder.java:99-115`。
- `ToolApproval != null` 时 bind approval、隐藏 composer；否则隐藏 approval、显示 composer。草稿不被销毁。依据 `MainChatView.java:493-503`、`ToolApprovalView.java:19,104-109`。
- 打开 drawer/sheet/picker/screen 前清焦点并收键盘，且同一时刻关闭其他 overlay；全局 hide 还关闭 slash popup。依据 `MainChatView.java:518-531,595-620,656-664`。

### 2.3 导航与动画（纯 UI + 状态机）

- 独立 screen 进入 `280ms`：forward 从 `+full width` 到 0，旧页到 `-full width`；backward 方向相反；两者 `DecelerateInterpolator`。退回聊天为旧页到 `+full width`、`220ms`、`AccelerateInterpolator`。依据 `MainChatView.java:81-83,723-763,786-823`。
- 相同 screen id 或 `animate=false` 不动画；清除其它 child。screen cache 为 access-order `LinkedHashMap`，最多 12 个，超出淘汰最老并 detach。依据 `MainChatView.java:134-136,674-720`。
- 返回键严格优先：screen → directory picker → attachment picker → generic bottom sheet → drawer → Activity。依据 `BackNavigation.java:59-84`。

### 2.4 业务事件（业务逻辑，不属于 view 重绘）

- header：菜单、项目、工具权限、新会话、更多，转发 presenter。`MainChatView.java:160-186`。
- 消息：tool review、查看 shell 命令、打开 Markdown URL、copy、recall、quote、share、select text、进入多选。`MainChatView.java:192-238`。
- 空态：Add model → 模型管理；Open workspace → 项目选择。`MainChatView.java:255-265`。
- composer：文本/附件/图片发送、附件/图片 picker、普通工具权限、项目、设置、更多、mode、stop、快速切模、模型管理、reasoning effort、查询模型数。`MainChatView.java:267-327`。
- 抽屉：新会话、选中/删除会话、移除项目、点击/长按文件、激活/刷新文件树。`MainChatView.java:339-384`。

## 3. Header

```text
HeaderView : LinearLayout(HORIZONTAL, CENTER_VERTICAL, BG, min-height 56)
├─ menu : IconButton 40×48, glyph 19
├─ brand : LinearLayout(CENTER_VERTICAL, min-height 48, weight=1, clickable)
│  ├─ projectText : 16sp medium, TEXT, single-line, END ellipsis
│  └─ chevronDown : 24×32, glyph 14
├─ permissions/shield : 40×48, glyph 19
├─ new/plus : 40×48, glyph 19
└─ more/vertical-ellipsis : 40×48, glyph 19
```

- Header padding `4/2/8/2dp`；背景 `BG`。projectText max width 为 `header width - horizontal padding - 184dp`。依据 `HeaderView.java:26-78`。
- 点击 menu 只开 drawer；点 projectText/chevron 所在 brand 打开项目选择；shield 打开普通工具权限；plus 新会话；more 打开更多。可见图标顺序不可调换。
- Phone Control 删除验收：普通权限若保留，不能出现 Accessibility/Phone Control permission；若产品决定移除顶部普通权限入口，则整颗 40×48 shield 删除并重新核对 `projectText` 的 184dp 预留（否则标题会无故提前省略）。

## 4. Drawer：会话历史与文件入口

### 4.1 外壳与动画

```text
DrawerView : FrameLayout [默认 GONE]
├─ backdrop : full-screen, OVERLAY, click closes
└─ sidebar : LinearLayout(VERTICAL, BG), START, width=min(screen-48dp,360dp)
   ├─ header : HORIZONTAL/CENTER_VERTICAL, padding 24/40/16/24
   │  ├─ title : 17sp bold TEXT, weight=1
   │  └─ actions : horizontal (Files tab 时 refresh 32×32/glyph16/ACCENT)
   ├─ tabs : horizontal, padding 2, margins L/R16 bottom12
   └─ body : vertical, weight=1
```

依据：`DrawerView.java:80-125,226-247,295-303`。

- 打开：首次设 VISIBLE，sidebar 从 `-sidebarWidth` → 0，backdrop alpha 0 → 1，`180ms Decelerate`；关闭相反，`150ms Accelerate`，结束设 GONE 并把 translation/alpha 复位。依据 `DrawerView.java:31-33,163-208`。
- tab：两列等宽；行内容居中，垂直 padding 8；glyph 14，label 13sp；active 使用 `SURFACE_ELEVATED` 6dp 圆角、ACCENT 和 medium，inactive 无背景、TEXT_TERTIARY。Files 首次激活触发加载。依据 `DrawerView.java:210-247,447-466`。

### 4.2 Conversations tab

```text
body
├─ newConversation : horizontal, 52dp, margins L/R16 bottom12,
│  INPUT_BG radius14, padding 16/12/16/12
│  ├─ plus 18×18
│  └─ label 16sp bold, left 8
└─ ScrollView(weight=1)
   └─ list : vertical, padding 12/12/12/32
      ├─ empty : 13sp tertiary, centered, top margin 80 [if empty]
      └─ conversationItem* : bottom margin 8
```

会话项：horizontal/CENTER_VERTICAL，padding `8/16/4/16`；active=`INPUT_BG + radius12 + 1dp ACCENT`，inactive=全局 pressable；内部 iconBox 使用 `SURFACE_LIGHT radius14`，消息 glyph 16（active ACCENT，否则 tertiary）；中间 texts `weight=1, margin L/R8`，标题 16sp（active medium）单行，时间 11sp tertiary 且 top 6；末尾 trash `48×48, glyph16`。源码实际漏给 `iconBox` 加到 item（创建于 `DrawerView.java:502-506`，但 `item.addView(iconBox,...)` 不存在），故当前运行 UI 很可能不显示会话图标；**1:1 基线应以实际行为为准，迁移验收需先与截图确认是否复制此缺陷，默认不要凭设计意图擅自加回。** 依据 `DrawerView.java:305-355,482-533`。

交互：点新会话先回调再 close；点会话先选择再 close；点 trash 只删除，不主动 close。时间格式固定 `Locale.US` 的 `M/d HH:mm`，无时间为空。`DrawerView.java:313-318,491-496,527-533,610-615`。

### 4.3 Files tab（主壳文件入口）

```text
body
├─ projectStrip : vertical, margins L/R16 bottom8, padding8
│  ├─ projectLabel : 13sp bold TEXT
│  └─ projectPath : 11sp tertiary, maxLines=2, END ellipsis, top6
└─ ScrollView(weight=1)
   └─ tree : vertical, padding 8/8/8/0
      ├─ preparing : 13sp tertiary centered, top80 [tree=null]
      └─ fileRow recursively
```

- strip 仅当 projectRemovable 才 clickable/可长按；长按使用系统 long-press timeout、移动超过 touch slop 取消，触发 haptic，展示危险色确认框。依据 `DrawerView.java:357-435,650-708`。
- file row：horizontal/CENTER_VERTICAL，`minH48`；padding left=`16 + min(depth,5)*16`，top/right/bottom=`12/16/12`。目录 glyph 16，expanded 为 folder-open+ACCENT，否则 folder+secondary；普通文件 glyph14。label 13sp TEXT 单行，left8、weight1。root 额外 plus `22×22/glyph14`，点击复用 root long-press action。依据 `DrawerView.java:536-607`。
- 点击任意 row 都回调 `(path,isDirectory)`；长按回调 `(path,name,isDirectory,isRoot)`。展开状态来自 `FileTreeNode`，view 不自行 toggle。代码类 `.java/.kt/.js/.ts/.tsx/.jsx/.xml/.json/.gradle` 用 file-code；XML 为 WARNING，其它代码硬编码 `#F0DB4F`；`.md/.txt/.log` 为 file-text/secondary，其余 tertiary。依据 `DrawerView.java:574-607,617-642`。
- 通用文件 action dialog/picker 的内部像素规范归分区 C；A 区只要求保持触发链与 overlay 行为。

## 5. 消息列表、空态与多选

### 5.1 view tree/滚动

```text
ChatMessageListView : FrameLayout [BG, clipToPadding=false]
├─ ListView : MATCH, padding top/bottom8
├─ scrollToBottom : END|BOTTOM, 44×44, margins16, elevation8
└─ multiSelectBar : BOTTOM, MATCH×WRAP, 默认 GONE, elevation8
```

- ListView：BG，透明 cache/selector，无 divider、fading edge、fast scroll/focus；overscroll-if-content，smooth scrollbar，非 stack-from-bottom，transcript disabled。依据 `ChatMessageListView.java:59-81`。
- 对话 id 变化时启用 follow-tail；若 follow-tail 且有内容则无动画落底。用户 touch scroll/fling、DOWN/MOVE、或子项请求 disallow intercept 时都立即停用 follow-tail。依据 `ChatMessageListView.java:111-139,866-887`。
- scroll-to-bottom：ACCENT 填充+同色 1dp 描边，radius22，glyph20/white；仅“非多选、有行、未到底（2dp tolerance）”显示。点击先启用 follow-tail，定位最后项，剩余 delta 用 `smoothScrollBy(...,180ms)`。依据 `ChatMessageListView.java:83-98,294-353`。

### 5.2 空态与通知行

- 无消息时 list 始终有 1 行。容器 padding `28/96/28/64`，START；title `28sp TEXT`；desc `15sp secondary`、top20、line spacing6。
- 未配置模型：desc 使用 configure 文案，并显示 Add model，minH48/top28。已配置但新聊天：正常空态文案；额外 actions row top20、center，Add model 与 Open workspace 间隔12；按钮 16sp、padding16/8，主按钮 ACCENT radius22，次按钮 SURFACE_LIGHT radius22+BORDER_LIGHT。依据 `ChatMessageListView.java:356-421`。
- model switch notification：horizontal centered，padding16/8；11sp tertiary、单行 END ellipsis。依据 `ChatMessageListView.java:424-437`。

### 5.3 消息过滤与聚合（业务/呈现逻辑）

- SYSTEM、TOOL、`isHidden` 不作为独立行；TOOL 消息先按 toolCallId 组装为对应 assistant tool result。依据 `ChatMessageListView.java:471-496`。
- 普通模式把连续 assistant 消息构造成 timeline：含 tool call/retry/error 的组成为 turn；最终 answer 必须非工具、非 retry/error、处理已结束且正文非空。agent/pipeline tool 单独成 block，其余连续 tools 成组；reasoning/prose 保持处理顺序。依据 `ConversationTimeline.java:44-118,121-143`。
- 多选模式故意绕过 timeline，以原始可见消息逐条显示；普通模式 5 种 row type：configure/empty、user、assistant、notice、assistant turn。依据 `ChatMessageListView.java:540-589`。
- row cache 上限 140，以 `conversationId:role:id` 为键；conversation 变化清 cache 与 disclosure。依据 `ChatMessageListView.java:440-465,519-535,718-765`。

### 5.4 多选

- 入口来自消息 action bar；进入后先清选择，list bottom padding 从8变为72（`8+64`），隐藏 scroll-to-bottom。点消息行 toggle id；选中行背景 `ACCENT_MUTED radius12 + BORDER_LIGHT`，不改原 row padding。依据 `ChatMessageListView.java:101-109,172-218,659-683`。
- bottom bar：`SURFACE_ELEVATED`，padding16/8；count `16sp TEXT`、weight1；export 与 close 均 `44×44/glyph20`，间隔8，export 为 ACCENT radius22。export 传选中消息；close 退出并清选择。依据 `ChatMessageListView.java:235-292`。

## 6. 用户消息、助手消息与操作条

### 6.1 UserMessageView

```text
UserMessageView : vertical, END, padding 16/16/16/32
├─ contentText : WRAP, 16sp, textOn(USER_BUBBLE), radius18,
│  padding 15/10/15/10, line spacing5
├─ attachmentList : vertical END, WRAP, top4
│  └─ attachmentChip* : 11sp medium secondary, maxW220,
│     middle ellipsis, SURFACE_LIGHT radius14+BORDER_LIGHT,
│     padding8/4, bottom4
└─ MessageActionBar(right, recall=true) : WRAP×44, top3, 默认 GONE
```

- 初建 max bubble 为 `(screenWidth-32)*80%`；实际 measure 后覆盖为可用内容宽的 90%。最终以 90% 规则为准。依据 `UserMessageView.java:31-45,107-112`。
- 正文为空且有附件，或正文恰为“attached files”占位且有附件时，正文 GONE，只显示 chips。依据 `UserMessageView.java:132-151`。
- 长按正文 toggle action bar；换 message id 时隐藏。没有进入动画：`ENTRANCE_FADE_MS=220` 是未使用死常量，禁止凭该常量新增 fade。依据 `UserMessageView.java:15,102-105,124-135`。

### 6.2 AssistantMessageView

```text
AssistantMessageView : vertical, START, padding16/0/16/28
├─ ContextCompactBlockView : MATCH, margins top/bottom2
├─ ThinkingBlockView : MATCH, bottom8
├─ MarkdownView : MATCH
├─ WorkingStatusView : MATCH, top2
├─ toolCallsContainer : vertical MATCH, top8
│  └─ ToolCallBlockView* : MATCH, bottom4
└─ MessageActionBar(left, recall=false) : WRAP×44, top3, 默认 GONE
```

依据 `AssistantMessageView.java:46-123,265-302`。

显示状态：

- compact message：只显示 compact block；thinking/content/working/tools/actions 全隐藏并清 tool container。`AssistantMessageView.java:143-160`。
- reasoning 非空显示 ThinkingBlock；正文 trim 空则 Markdown GONE；error 用 monospace/selectable plain text，不做 Markdown；正常用 CommonMark。`AssistantMessageView.java:162-191`。
- streaming 显示 working status；reasoning 非空且正文空时标签为 Thinking，否则 Working。`AssistantMessageView.java:193-230`、`WorkingStatusView.java:212-216`。
- 长按 Markdown 或递归子 TextView toggle action bar，但 streaming 返回 false；新 message/streaming 时 action bar 隐藏。`AssistantMessageView.java:193,205-213`。
- tool results 变化以完整签名决定重建；成功且无 review state 的 image generation tool card 被隐藏，因为图像应在消息正文中呈现。`AssistantMessageView.java:265-344`。
- 同 User 一样，`ENTRANCE_FADE_MS=220` 未使用，无消息入场动画。`AssistantMessageView.java:18`。

### 6.3 MessageActionBarView

- horizontal，用户 END/助手 START，minH44；每个 action 外盒 `40×44`、right4，glyph16/tertiary（调用时传 container44，实际按 40×44 居中）。顺序：copy → quote → share → select text → multi-select →（仅用户）recall。依据 `MessageActionBarView.java:20-60,130-142`。
- `setActionsVisible(false)` 只隐藏 quote/share/select/multi，copy 与 recall 不受影响；不过当前消息 view 是整条 bar 隐藏来处理 streaming。依据 `MessageActionBarView.java:104-110`。
- copy 写 clipboard 并 toast；quote 把正文转为 `> ` 前缀；share 进格式 picker；select text 弹 selectable dialog；recall 以 id 回调。主壳 wiring 见 `MainChatView.java:206-238`。

## 7. Assistant turn、thinking、working、compact

### 7.1 AssistantTurnView

```text
AssistantTurnView : vertical, padding16/0/16/32
├─ processToggle : horizontal CENTER_VERTICAL, minH48
│  ├─ processLabel : 13sp secondary
│  └─ chevron : 28×32, glyph16
├─ processRule : 1dp BORDER, bottom20
├─ process : vertical [展开才显示]
│  └─ reasoning / direct-agent / grouped-tools / markdown blocks, gap8
├─ answer : AssistantMessageView, forced padding0
└─ changes : vertical, top24 [有 diff 且有 answer 才显示]
   ├─ rule : 1dp BORDER
   ├─ summary : horizontal CENTER_VERTICAL, minH64
   │  ├─ file-pen : 26×32, glyph16
   │  ├─ filesLabel : 14sp TEXT, left6, weight1
   │  ├─ Review changes : 14sp, minH48, left padding12
   │  └─ chevron : 24×32, glyph16
   └─ files : vertical [展开], ToolCallBlock gap12
```

依据 `AssistantTurnView.java:52-113,186-221,225-263`。

- process 仅 tool turn 显示；点击 toggle 折叠/展开，状态键 `{firstMessageId}:process`。若设置 processAutoExpand 且无已存状态，首次自动展开。`AssistantTurnView.java:115-150,181-219`。
- process label 为 Pending/Running/Done，可追加格式化耗时；active 时每秒刷新，detach 取消。active = generating 且未 finish 且 started/running/pending。`AssistantTurnView.java:152-178`。
- grouped tools header minH48：terminal `24×32/glyph16`、label14sp secondary left6、chevron28×32；展开后 reasoning step 不重复显示，markdown step 缩放为 `.875`，子项 gap8。`AssistantTurnView.java:267-310`。
- changes 汇总按 unique path 计数，但保留每个 diffId（同文件多次编辑均可 review）；点 summary toggle，点 Review changes 强制展开全部 review card。`AssistantTurnView.java:84-109,223-263`。

### 7.2 ThinkingBlock

- vertical；header horizontal/CENTER，padding `0/8/0/8`、minH48；label13sp secondary；chevron `28×32/glyph16 tertiary`。content top8，13sp tertiary，line spacing4，vertical scrollbar；可滚动时 maxH180，否则不设上限。依据 `ThinkingBlockView.java:36-75,82-93`。
- 点击 header toggle；状态通过静态 map 按 message id 持久。label streaming=`Thinking`，完成=`Thought`。内文只解析 `**bold**`/`*italic*` 等 inline emphasis，不是完整 Markdown。`ThinkingBlockView.java:22-24,58-62,82-93,114-141`。
- `STREAMING_PULSE_MS=900` 与 animator 字段当前没有启动逻辑；`setStreaming` 只清 animator/alpha。不得擅自增加 pulse。`ThinkingBlockView.java:22-24,96-103`。

### 7.3 WorkingStatus

- 自绘最小高24；left/right pad1；3×3 点阵 16，dot radius1.35，文字间 gap8；文字 13sp monospace secondary，超宽 END ellipsis。点为 ACCENT。依据 `WorkingStatusView.java:23-41,61-76,127-135,195-205`。
- 点阵路径 offset `[1,2,5,8,7,6,3,0]`，每90ms 前进；active alpha1、trailing .52、其它 .18。72dp 宽 highlight 在文字上 1500ms 线性无限扫过。全局 animator scale=0 时停在首帧；仅 attached 且 working 时持有 animator。依据 `WorkingStatusView.java:24-31,145-180,218-252`。

### 7.4 ContextCompactBlock

- horizontal/CENTER，minH48，padding28/12。archive 外盒18/glyph14；label 13sp tertiary left6；spacer；running 时 18×18 indeterminate spinner，done/error 时 18×18 glyph13 check/x。error 把 icon、label、status 全变 DANGER。依据 `ContextCompactBlockView.java:21-67`。

## 8. Composer 输入栏

### 8.1 主 tree 与尺寸

```text
ComposerView : vertical, BG, padding20/14/20/20, custom top border paint
├─ quotePreviewLayout : horizontal, 默认 GONE, bottom8,
│  SURFACE_ELEVATED radius14+BORDER_LIGHT, padding8
├─ attachmentScroll : horizontal, no scrollbar, 默认 GONE, bottom8
│  └─ attachmentList : horizontal
├─ imagePreviewLayout : horizontal, 默认 GONE, bottom8,
│  SURFACE_ELEVATED radius14+BORDER_LIGHT, padding8
└─ panel : vertical, minH56, INPUT_BG radius20
   ├─ metaRow : horizontal CENTER, height34, padding16/0, 当前代码恒 GONE
   │  ├─ modelSelector + spacer + contextPercent
   ├─ divider : 1px BORDER_LIGHT, 当前代码恒 GONE
   ├─ quoteBlock : 旧的内部 quote 行，默认 GONE（与外部 quotePreview 重复）
   ├─ pendingContainer : vertical, 默认 GONE
   ├─ inputRow : horizontal, BOTTOM, padding8/6
   │  ├─ attach/plus : 44×44, glyph20
   │  ├─ EditText : weight1, 14sp, 1..3 lines, minH44, padding3/10
   │  └─ send/stop : 44×44, glyph20/17
   └─ modeRow : horizontal, 默认 GONE（image/mode 控件已构造但不显示）
```

依据 `ComposerView.java:126-230,232-364`。特别注意：旧源码确实构建了 metaRow、divider、modeRow，却从未在 render 中设 VISIBLE；1:1 不能把这些隐藏控件误当成现有 UI。当前可见入口集中在左侧 `+` 的 context menu 与 slash popup。

### 8.2 preview/chips

- 外部 quote preview：左 accent rail `3dp` radius2/right8；文字13sp secondary、max2行 END；close `28×28/glyph16`、left8；点击清 UI 并通知 QuoteController。依据 `ComposerView.java:393-428,512-527`。
- image preview：缩略图 `56×56 CENTER_CROP`、SURFACE_LIGHT；label13sp secondary、middle ellipsis、maxW220、left8、weight1；close `28×28/glyph16`、left8。选择失败清 drawable，但仍可显示 label。依据 `ComposerView.java:431-505`。
- attachment chips：横向滚动；每个高34，`INPUT_BG radius17+BORDER`，padding12/0/8；name13sp medium secondary、middle ellipsis maxW170；close `18×18/glyph12` left8；chip right8。依据 `ComposerView.java:135-143,853-895`。

### 8.3 输入、发送、streaming 队列（业务 + 状态）

- `canSend = trim(text)非空 || attachments非空 || image非空`。ENTER_SEND 时 IME send 或无 modifier Enter 发送；否则插入换行。EditText 即使 streaming 也 enabled，可继续输入。依据 `ComposerView.java:254-305,581-618,674-730,840-845`。
- send button：有可发送内容或 streaming 时 ACCENT radius22/white；空且 idle 时透明/secondary且 disabled。streaming+空显示 STOP glyph17；streaming+有内容仍显示 ARROW_UP glyph20，并将当前输入排队而不停止；空才 stop。所有点击先 `KEYBOARD_TAP` haptic。依据 `ComposerView.java:307-322,620-631`。
- 两侧按钮的真实对齐基线都是 `44×44dp` 触控框。attach 常态 glyph20，四边 padding12；send 构造时短暂设为 container40/glyph22，但构造末与每次 render 都被 `updateSendButton()` 覆盖为 container44/glyph20（stop 为17），在实际 44×44 view 中重新居中。`inputRow` 高度由 `44 + top/bottom 6 = 56dp` 决定。旧 `plus` 与 `arrow_up` 都是 24×24 viewport、stroke2/round cap+join，含笔触边界均为 x/y=4..20，SVG 没有不对称留白；arrow 的视觉重心偏上来自箭头造型而非 viewport。依据 `ComposerView.java:226-235,303-308,362-364,620-626`、`IconButtonView.java:225-245`、`ic_lucide_plus.xml:2-20`、`ic_lucide_arrow_up.xml:2-20`。
- quote 发送时变成每行 `> ` 的 Markdown quote，后加空行；slash 命令只切 mode/model/reasoning，不发消息；正常发送按是否有图片走不同 callback，之后清输入/附件/图片。依据 `ComposerView.java:680-730`。
- streaming 排队：每条保存 text+attachments（当前实现**不保存 pending image**），最多可见4条；每行 INPUT_BG、padding12/4/8/4，WARNING 竖条 `3×20`，预览11sp硬编码 `#FFFFAA33`，30字符截断，close `20×20/glyph12`。超过4条显示剩余数，11sp italic、`#FFCC8800`。streaming 从 true→false 时 post 自动发队首，一次一条。依据 `ComposerView.java:581-597,757-837`。
- streaming 时 attach/image/model/mode disabled且 alpha .62，输入仍可写；同时关闭 mode/model/context/slash overlays。context percent ≥80 使用 WARNING，否则 tertiary。依据 `ComposerView.java:581-617,1179-1192`。

### 8.4 `+` 上下文菜单与模型/mode sheet

- `+` 触发逻辑最终打开 bottom sheet（通用 sheet 样式以分区 C 为准）；内容顶圆角24 BG，padding28/24/28/28，标题22sp medium、高52；每行15sp TEXT、minH52。顺序：Files、Image、当前 model、当前 mode、Workspace、Permission、Settings、More。依据 `ComposerView.java:635-663`。
- 模型选择：panel padding24，标题22sp；按 provider 分组，组标题14sp medium secondary、top24 bottom8；model option 选中态由 `OptionRowView`；每组末尾 query row minH48。空模型时直接进入模型管理。依据 `ComposerView.java:1196-1235`。
- mode sheet 原顺序 Chat/Plan/Agent/Control；迁移必须删除 Control 后保留 Chat/Plan/Agent 顺序。`ComposerView.java:1254-1280`。

## 9. Slash command popup

```text
PopupWindow [outsideTouchable=true, focusable=false, transparent window bg]
└─ ScrollView [INPUT_BG radius14, clip outline]
   └─ content : vertical, INPUT_BG radius14+BORDER_LIGHT, padding8
      ├─ title : 13sp medium, height22, margins L/R12 top1 [可选]
      └─ row* : vertical, minH52, padding16/14, CENTER_VERTICAL
         ├─ row1 : horizontal
         │  ├─ label : 13sp medium, weight1, END ellipsis
         │  └─ selected dot : 7×7, ACCENT radius4, left8 [可选]
         └─ description : 11sp tertiary, single-line END, top1 [可选]
```

- popup 宽=`composer width - 32dp`，x=`composer.left+16`；在 composer 上方8dp；高为内容高与可用上方空间的较小值，但至少按52dp空间约束。依据 `SlashCommandPopup.java:55-68,92-124,142-220`。
- label 若以 `/` 开头，仅首 token 变 ACCENT+bold。点击先 dismiss，再在 main looper post action。`SlashCommandPopup.java:212-234`。
- 仅输入第一个字符为 `/` 且非 streaming 时出现；状态为 main/model id/reasoning。`/model` 的 modelId 合法后进入 reasoning 列表；列表实时按 query 过滤，当前 mode/model 用 selected dot。依据 `ComposerView.java:898-1044`。
- 必须删除 `/control` 项及描述资源；保留 `/chat`、`/plan`、`/agent`、`/model`。依据 `SlashCommandCatalog.java:66-88`。

## 10. Markdown 与代码块

### 10.1 MarkdownView 行为

- vertical、clipToPadding=false；CommonMark parser + GFM tables。支持 Heading、Paragraph、fenced/indented code、quote、table、ordered/bullet list、thematic break、HTML block、inline emphasis/strong/code/link/image/line-break/HTML literal。依据 `MarkdownView.java:40-46,122-170`、`MarkdownRenderer.java:40-53`、`MarkdownInlineRenderer.java:65-116`。
- 正常正文：16sp TEXT；heading medium，H1/H2/H3/H4+=28/24/20/16sp。heading line spacing5，普通正文7。依据 `MarkdownRenderer.java:87-96,254-265`、`MarkdownTextBlockView.java:13-24`。
- block margins：顶层 heading 8/20；顶层 paragraph/image 2/18；嵌套 paragraph 0/8、heading4/10；code 4/8；quote3/8；table4/8；hr8/8；list1/7。依据 `MarkdownRenderer.java:87-170,205-233`。
- pinch zoom 能力范围 `.5x..1.6x`，但默认 `pinchZoomEnabled=false`，只有显式启用才拦截双指；A 区当前没有启用调用。程序可 `setTextScale`，tool group 使用 `.875x`。依据 `MarkdownView.java:20-38,65-98,173-190`。
- error plain mode 创建裸 `TextView`，monospace/selectable，但没有显式 text color/size；它会继承平台默认。这是旧版可见差异，迁移不要误套 Markdown 样式。依据 `MarkdownView.java:132-151`。

### 10.2 inline/list/quote/table/image

- inline code：monospace、TEXT、CODE_BG；emphasis italic，strong bold；link ACCENT，使用 handler 或系统 URLSpan；soft/hard break 都变 `\n`；inline HTML 原样显示；混排 image 变本地化“Image: alt”文字。依据 `MarkdownInlineRenderer.java:65-138`。
- list：每 item horizontal/TOP，bottom3；marker 16sp secondary、右对齐、right8；marker width=`18 + min(depth,4)*4`；bullet 随 depth 循环 `•/-/+`，有序列表尊重起始序号。依据 `MarkdownListBlockView.java:13-36`、`MarkdownRenderer.java:143-160,267-275`。
- quote：horizontal，左 rail `3dp BORDER_LIGHT radius2`，right12；右侧 vertical content。依据 `MarkdownQuoteBlockView.java:11-24`。
- table：horizontal scrolling，透明 radius12；cell minW84/minH38，padding12/8，0-radius+BORDER_LIGHT；header 使用 SURFACE_LIGHT+medium，body CODE_BG+normal；文字13sp、maxW196、多行，对齐遵从 GFM cell alignment。依据 `MarkdownTableView.java:32-46,68-118`。
- image：MATCH width/FIT_CENTER、maxH520；caption 最长120字符，11sp tertiary/top4；不支持 URL 或解析失败时 fallback 为13sp tertiary。只接受 `data:image/...;base64,`；payload≤5MiB 字符，解码长边≤2048px并采样。依据 `MarkdownImageView.java:15-105`。

### 10.3 code block

```text
MarkdownCodeBlockView : vertical, CODE_BG radius12, padding16/8/16/16
├─ header : horizontal CENTER, bottom8
│  ├─ language : 11sp tertiary, single-line, weight1
│  └─ copy : 48×48, glyph约18, transparent
└─ code : 13sp TEXT monospace selectable, line spacing6
   ├─ direct MATCH width [wrap=true]
   └─ HorizontalScrollView(no scrollbar) [wrap=false]
```

依据 `MarkdownCodeBlockView.java:20-70`。copy 写入 plain text clipboard 并 short toast；无语法高亮、无行号。`MarkdownCodeBlockView.java:72-79`。

## 11. Tool cards 与 diff

### 11.1 分发与公共规则

- `ToolCallBlockView` 本身只是 vertical wrapper。结构签名（project/path/name/args/result meta）变更才换/重 bind child；仅 result content 变更走 `updateContent`。factory 优先按 tool 声明 view class，再按 display category，最后 GENERIC。依据 `ToolCallBlockView.java:29-70,98-124`、`ToolCallViewFactoryRegistry.java:13-63`。
- 类别映射：READ/image-generation→Read；SHELL→Shell；WRITE→Write；DELETE→Delete；TODO→Todo；AGENT→Agent；AGENT_PIPELINE→Pipeline；其它→Generic MCP。Phone Control factory 必须完全删除。依据 `tool-ui/.../factory/*.java`。
- 普通 Read/Shell/Write/Delete/Generic 卡没有外层卡片背景；Agent/Pipeline 才有 `SURFACE_ELEVATED radius8+BORDER_LIGHT`。`BaseToolCallView.java:41-42`、`ToolCallAgentView.java:40`、`ToolCallAgentPipelineView.java:40`。

### 11.2 Read / Shell / Delete / Generic

- Read：header minH48/CENTER；icon `24×32/glyph16 secondary`；label14sp secondary、weight1、MIDDLE ellipsis；错误下接 ToolError。无展开箭头。`ToolCallReadView.java:23-43`。
- Shell：header minH48；terminal `24×32/glyph16`；label14sp secondary left6、END ellipsis；chevron `24×32/glyph14`。整 header toggle。detail 为 maxH240 vertical scroll，`CODE_BG radius12+CODE_BORDER`；output 13sp monospace secondary/selectable、padding14/12、line spacing6。`ToolCallShellView.java:29-78`。
- Delete：header 同上，trash glyph16；label为状态/数量；chevron glyph14。detail maxH200，`CODE_BG radius12+CODE_BORDER`，13sp monospace secondary/selectable，padding14/12、line spacing7。`ToolCallDeleteView.java:28-70`。
- Generic MCP：header minH48，MCP icon `24×32/glyph16`；title 14sp、END、left6、weight1，状态决定色；chevron `24×32/glyph14`。detail maxH240、CODE card；Input/Output sections padding14/12，heading11sp medium tertiary，body13sp monospace secondary/selectable、top6，section间 1px CODE_BORDER。`ToolCallGenericView.java:47-151`。
- ToolError：仅 result.error 显示；`CODE_BG radius12+CODE_BORDER`，maxH240；13sp DANGER monospace/selectable、line spacing6、padding14/12。`ToolErrorView.java:14-36`。

### 11.3 Write 与 review actions

- header minH48：file-pen `24×32/glyph16`；label14sp secondary、MIDDLE、left6、weight1；chevron `24×32/glyph14`。`ToolCallWriteView.java:56-71`。
- detail：`CODE_BG radius12+CODE_BORDER`。file header padding left12；filename13sp secondary、MIDDLE、weight1；copy `44×44/glyph16`。diff 外包 maxH224 vertical scroll；error 13sp danger、padding14/10。actions END/CENTER、padding8/6；Revert/Accept 均 minH48、horizontal padding14。依据 `ToolCallWriteView.java:72-99,175-181`。
- actions 仅 `result.diffId` 非空且 review state 不是 accepted/rejected 时显示；提交分别为 rejected/accepted。diff 异步加载时先 loading，失败 unavailable；展开状态由 disclosure map 保存。`ToolCallWriteView.java:101-173`、`DiffLoader.java`。

### 11.4 DiffView

- `HorizontalScrollView(fillViewport=true, scrollbar=true)` → vertical content wrap-width。只显示每处 change 前后各3行上下文；省略段显示 `⋯`。最多200个显示行，之后以12sp secondary truncation 文案结束。依据 `DiffView.java:12-45`。
- 每行 minH26；change 行用 add/del translucent bg；左侧 status rail `3dp`（SUCCESS/DANGER），行号列42dp、right aligned、padding2/3/10/3；代码 padding4/3/14/3，13sp monospace single-line。单行内容最多2000字符后 `…`。无尾换行时加12sp note、padding14/4。依据 `DiffView.java:47-66`。

### 11.5 Todo / Agent / Pipeline

- Todo：vertical list padding top/bottom12；row minH44、horizontal/CENTER、vertical padding4。indicator `14×14`、stroke2：completed 为 SUCCESS circle-check 并把文本 tertiary+strike-through；in-progress 为 ACCENT 1.5dp dash/gap 圆；pending 为 tertiary 实线空圆。正文13sp、多行、left8、weight1。空态11sp tertiary、padding12/8/12/12。错误时整卡换 ToolError。依据 `ToolCallTodoView.java:25-148`。
- Agent：外卡 `SURFACE_ELEVATED radius8+BORDER_LIGHT`。header padding12/8；type icon `28×28/glyph14`，透明圆+type color 描边；title block left8，标题14sp medium 单行，meta/pills 11/10sp；右侧 running spinner18 或 status glyph18/13，再放状态文字与 chevron16/12。展开由1px CODE_BORDER分隔；内容 padding12/8/12/12，BoundedScroll maxH400；thinking、running/markdown result、empty，以及 nested tool calls。依据 `ToolCallAgentView.java:40,84-190,228-306`。
- Pipeline：外卡同 Agent；header padding12/8，branch `30×30/glyph15`、标题/meta/summary chips、spinner/status18、chevron16。展开 list padding8；agent row 为 `CODE_BG radius8+CODE_BORDER`、padding8，header icon24/glyph12、状态16、chevron14；展开内容 maxH280，内部 reasoning/markdown；末尾可有 summary divider+Markdown。依据 `ToolCallAgentPipelineView.java:40,65-179,219-359`。

## 12. Tool approval（占据 composer 槽位）

```text
ToolApprovalView : vertical, padding16/10/16/16, 默认 GONE
└─ panel : vertical, padding16/12, BG radius20+BORDER, elevation2
   ├─ heading : horizontal CENTER
   │  ├─ terminal/wrench : 24×28, glyph16
   │  └─ title : 12sp secondary, left4
   ├─ BoundedScroll(maxH156)
   │  └─ details : vertical, right padding4
   │     ├─ reason : 15sp TEXT, top6, line spacing5
   │     └─ horizontalScroll : top16 bottom10
   │        └─ command/path : 13sp monospace secondary, selectable, no-wrap
   └─ buttons : horizontal END or vertical, top10, gap6
      ├─ Deny
      ├─ Allow once [primary]
      └─ Always [条件显示]
```

- 每按钮 minH44，13sp，padding8；primary ACCENT radius18，secondary BG radius18+BORDER。宽度容纳不下文案时整个 buttons 从三列等分切到 vertical/MATCH，间隔6。依据 `ToolApprovalView.java:33-100,142-148`。
- shell 显示 terminal/title；delete/其它显示相应标题+wrench。reason 从 input.reason/description，否则默认；command 显示 cwd 换行 action；多路径 delete 合并 paths/file_path/path。Always 只在 `canAllowPermanently` 时显示。提交后按 reviewId 锁三按钮，防重复。依据 `ToolApprovalView.java:104-140`。
- 其更完整的 review 生命周期/通用弹层规范归分区 C；本区必须保证它替换 composer 而不是覆盖在 composer 上。

## 13. 资源复用清单

### 13.1 字符串

- app：`app/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-ru/strings.xml`。A 区至少保留 `header_*`、`drawer_*`、`chat_*`、`composer_*`、`message_*`、`empty_state_*`、`slash_command_*`、`context_compact_*`、`export_*`；删除 `slash_command_control_*`、`screen_phone_control_*` 和只服务无障碍的资源。
- Markdown：`markdown/src/main/res/values/strings.xml`、`values-zh/strings.xml`，含 code copy、image label。
- tool cards：`tool-ui/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-ru/strings.xml`，保留 agent/pipeline/read/write/delete/shell/todo/diff/generic；删除 `tool_call_phone_*`。
- theme：`ui-theme/src/main/res/values*/strings.xml`，含 thinking 标签与 icon descriptions。

不要把旧版 `modeLabel()` 中硬编码的 `Chat/Plan/Agent/控制` 继续硬编码到 C++；视觉文字必须相同，但迁移时统一走资源表，且 Control 删除。依据 `ComposerView.java:1270-1280`。

### 13.2 A 区实际使用图标

迁移目标应复用旧 Android vector path（24×24 viewport，stroke linecap/join 不变，颜色由 token/currentColor 驱动）：

`menu, shield, plus, ellipsis_vertical, chevron_down/right, refresh_cw, message_square, folder, folder_open, file, file_code, file_text, trash_2, copy, quote, share_2, text_cursor, check_square, rotate_ccw, download, arrow_up, square(stop), image, archive, check, x, terminal, wrench, file_pen_line, mcp(ic_lineai_mcp), circle_check, git_branch, bot, circle_x`。

源：`ui-theme/src/main/res/drawable/*.xml`；Markdown 代码块另有 `markdown/src/main/res/drawable/ic_lucide_copy.xml`。图标 id → drawable 映射在 `IconButtonView.java:93-182`。

## 14. 容易遗漏、必须写进验收用例的行为

1. 792dp 只限 chat content，不限 screenHost/overlay；宽屏上聊天居中，抽屉仍贴窗口左边。
2. IME bottom inset 与 system nav 取 max，不相加。
3. overlay 打开前关闭其它 overlay并隐藏键盘；返回键按固定优先级消费。
4. 用户手动滚动后 streaming 不应抢回底部；只有换会话或点悬浮按钮恢复 follow-tail。
5. 普通模式将 assistant 工具过程聚合成 turn；多选模式拆回原消息。
6. SYSTEM/TOOL/hidden 永不独立显示；tool result 回填对应 call。
7. 用户附件占位文案应被隐藏，避免“attached files”与 chip 重复。
8. 只有非 streaming assistant Markdown 长按才出现 action bar；用户始终可长按。
9. 成功 image-generation tool card 会隐藏，图像由 Markdown/data URI 显示。
10. quote 发出时按每行加 `> `，不是只显示 preview。
11. streaming+有输入为排队，不是 stop；streaming+空才 stop；队列不保存图片是旧行为。
12. Composer 的 model meta row、divider、mode row 当前恒隐藏；不要按构造代码误显示。
13. Markdown pinch zoom 默认关闭；代码 wrap 由 setting 控制；错误正文绕过 Markdown。
14. Thinking 的 pulse 和两处 message fade 常量均未实际使用；不要擅自添加动画。
15. diff 只显示 change 上下3行、最多200行、单行最多2000字符。
16. Write review actions 只有未决 diff 显示；ToolApproval 提交后必须立即锁按钮防双击。
17. Drawer project strip 的长按处理递归挂到子 view，移动越界会取消；需保留 haptic。
18. Drawer 会话 `iconBox` 旧代码创建但未 attach；截图验收前不要“修好”成不同 UI。
19. file tree 的展开状态是 presenter/model 驱动，不是 view 内部本地 toggle。
20. `/control`、Control mode、Phone Control tool card/page/service/string/icon入口全部是删除项；不要用“桌面不显示”替代删除。

## 15. 已读文件清单

以下为本分区通过 `rg --files`/import/reference closure 建清单后逐文件阅读的生产源码（测试仅用于交叉检查，不作为视觉权威）。

### 主壳与聊天组件

- `app/src/main/java/cn/lineai/ui/MainChatView.java`
- `app/src/main/java/cn/lineai/ui/component/MainChatViewLayoutBuilder.java`
- `app/src/main/java/cn/lineai/ui/component/BackNavigation.java`
- `app/src/main/java/cn/lineai/ui/component/OverlayManager.java`
- `app/src/main/java/cn/lineai/ui/component/HeaderView.java`
- `app/src/main/java/cn/lineai/ui/component/DrawerView.java`
- `app/src/main/java/cn/lineai/ui/component/ChatMessageListView.java`
- `app/src/main/java/cn/lineai/ui/component/UserMessageView.java`
- `app/src/main/java/cn/lineai/ui/component/AssistantMessageView.java`
- `app/src/main/java/cn/lineai/ui/component/AssistantTurnView.java`
- `app/src/main/java/cn/lineai/ui/component/MessageActionBarView.java`
- `app/src/main/java/cn/lineai/ui/component/WorkingStatusView.java`
- `app/src/main/java/cn/lineai/ui/component/ContextCompactBlockView.java`
- `app/src/main/java/cn/lineai/ui/component/ToolApprovalView.java`
- `app/src/main/java/cn/lineai/ui/component/ComposerView.java`
- `app/src/main/java/cn/lineai/ui/component/SlashCommandPopup.java`
- `app/src/main/java/cn/lineai/ui/component/FileActionRow.java`
- `app/src/main/java/cn/lineai/ui/component/BottomSheetView.java`
- `app/src/main/java/cn/lineai/ui/component/DirectoryPickerSheetView.java`
- `app/src/main/java/cn/lineai/ui/component/AttachmentPickerSheetView.java`
- `app/src/main/java/cn/lineai/ui/component/DialogBuilder.java`
- `app/src/main/java/cn/lineai/ui/component/DialogDimensions.java`
- `app/src/main/java/cn/lineai/ui/component/DialogManager.java`
- `app/src/main/java/cn/lineai/ui/component/LineAlertDialog.java`
- `app/src/main/java/cn/lineai/ui/component/TextSelectionDialog.java`
- `app/src/main/java/cn/lineai/ui/model/ConversationTimeline.java`
- `app/src/main/java/cn/lineai/ui/model/ProcessingDuration.java`
- `app/src/main/java/cn/lineai/ui/util/SlashCommandCatalog.java`
- `app/src/main/java/cn/lineai/ui/util/KeyboardController.java`

### Markdown

- `markdown/src/main/java/cn/lineai/ui/markdown/MarkdownCodeBlockView.java`
- `MarkdownImageView.java`, `MarkdownInlineRenderer.java`, `MarkdownLinkHandler.java`
- `MarkdownLinkSpan.java`, `MarkdownLinks.java`, `MarkdownListBlockView.java`
- `MarkdownQuoteBlockView.java`, `MarkdownRenderer.java`, `MarkdownTableView.java`
- `MarkdownTextBlockView.java`, `MarkdownThematicBreakView.java`, `MarkdownView.java`

### tool-ui

- `tool-ui/src/main/java/cn/lineai/tool/ui/DiffLines.java`
- `ToolCallBlockView.java`, `ToolCallExpansion.java`, `ToolCallViewFactory.java`
- `ToolCallViewFactoryRegistry.java`, `ToolInfoResolver.java`, `ToolInfoResolverProvider.java`
- `factory/AgentPipelineToolCallViewFactory.java`, `AgentToolCallViewFactory.java`
- `factory/DeleteToolCallViewFactory.java`, `GenericToolCallViewFactory.java`
- `factory/ImageGenerationToolCallViewFactory.java`, `ReadToolCallViewFactory.java`
- `factory/ShellToolCallViewFactory.java`, `TodoToolCallViewFactory.java`, `WriteToolCallViewFactory.java`
- `factory/PhoneControlToolCallViewFactory.java`（只为删除核验，不迁移）
- `util/AgentPipelineSummaryParser.java`, `AgentToolResultDisplay.java`, `NestedToolCallParser.java`
- `util/ToolCallInputParser.java`, `ToolCallJsonFormatter.java`, `ToolCallPathDisplay.java`, `ToolCallUtils.java`
- `view/BaseToolCallView.java`, `DiffLoader.java`, `DiffView.java`
- `view/ToolCallAgentPipelineView.java`, `ToolCallAgentView.java`, `ToolCallDeleteView.java`
- `view/ToolCallGenericView.java`, `ToolCallReadView.java`, `ToolCallShellView.java`
- `view/ToolCallTodoView.java`, `ToolCallWriteView.java`, `ToolErrorView.java`

### theme、模型与资源

- `ui-theme/src/main/java/cn/lineai/ui/theme/LineTheme.java`
- `IconButtonView.java`, `ThinkingBlockView.java`, `BoundedScrollView.java`
- `FlowLayoutView.java`, `InlineEmphasisParser.java`
- `core-model/src/main/java/cn/lineai/model/ThemePalette.java`
- `app/src/main/res/values*/strings.xml`
- `markdown/src/main/res/values*/strings.xml` 与 `drawable/ic_lucide_copy.xml`
- `tool-ui/src/main/res/values*/strings.xml`
- `ui-theme/src/main/res/values*/strings.xml` 与 `ui-theme/src/main/res/drawable/*.xml`

## 16. 疑似未覆盖引用 / 迁移时需要联查

这些不是 A 区漏读的核心 view，而是跨分区或运行时注入点；实现时必须引用相邻规范或做截图校验：

- `OptionRowView`：Composer model/mode sheet 行样式，本质属于通用 bottom-sheet row，归分区 C。
- `ShareController`、`ExportFormatResolver`：copy/share/export 的格式与系统分享 intent，不在本 UI 文件定义。
- `QuoteController`：主壳和 Composer 都有 quote compose 路径，旧代码存在双重防线；迁移需保证只 prepend 一次。
- `ScreenFactories` / `ScreenRegistry`：A 只定义 screenHost 与转场，不定义 40+ 独立设置页。
- `ToolInfoResolverProvider` / `ToolDisplayCategory`：tool 名称到 category/view class 在运行时注入；新增 tool 必须命中 Generic fallback。
- `DiffLoader` 的 diff 内容源、review 持久化以及 `ToolReviewListener` 的实际 side effect：属于业务/数据层。
- `ThemePalette` 的 custom color override 与 system 模式解析：本文件给 token 消费规则，主题设置页归其它分区。
- `DrawerView` 会话 iconBox 未 attach、Composer 隐藏 rows、Thinking/message 未使用动画常量均为代码与设计意图不一致点，必须以旧 APK 截图/交互录屏做最终裁决。
- API <30 insets、Windows 的 IME/安全区等价处理需由 HuxerUI 平台层设计；不能直接把旧版 no-op 当成桌面规范。

## 17. 1:1 验收切片

建议按以下最小状态截图/录屏逐项对比，而不是只看默认空页：

1. 无模型空态、已配置模型的新会话空态。
2. 短/长用户消息、仅附件、含多附件、用户 action bar。
3. 普通 assistant Markdown：H1-H4、段落、嵌套列表、quote、table、link、inline code、wrap/no-wrap code、data image、错误 plain text。
4. streaming reasoning-only、streaming answer、完成 thinking 折叠/展开、context compact running/done/error。
5. read/shell/write/delete/todo/generic/agent/pipeline 卡的 running/success/error/pending review 与展开态。
6. diff 无改动、大 diff 截断、无末尾换行、同文件多次 diff。
7. streaming 输入排队 1/4/5 条、删除队列、停止后自动发送。
8. drawer 两 tab、空会话、active 会话、深度>5文件、root plus、项目长按取消/确认。
9. 手动滚离底部后 streaming、scroll-to-bottom 180ms、多选0/1/N与 export。
10. drawer/sheet/picker/screen 同时触发时的互斥、返回键优先级、键盘出现时的 bottom inset。
11. Accessibility/Phone Control 删除核验：无 `/control`、无 Control mode、无页面/卡片/服务/资源入口；普通工具权限若保留不得出现手机控制项。
