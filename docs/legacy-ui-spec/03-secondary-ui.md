# Legacy UI 规格 03：次级界面、通用弹层与主题基础

> 审计基线：`/home/LangLang/AndroidStudioProjects/LineCode`。本文描述旧 Android View 实现的**实际代码行为**，不是重设计建议。迁移时默认按本文 1:1 复刻；用户明确排除的 Accessibility/手机控制功能只保留删除核验，不进入新 UI。

## 1. 边界、权威性与清单结论

- A 区（主壳、聊天消息、Markdown、代码块/Diff/tool card、Composer/Header/Drawer/会话与主壳文件入口）由 `01-shell-chat.md` 负责；本文只对其共用的 dialogs、bottom sheets、pickers、toast/loading/error/empty、tool approval/review 和 `ui-theme` 基础设施给出权威规格。
- B 区（Settings 与 Model/Extension/MCP/Memory/Theme/Input/Output/Security/Storage/Data/Error/KeepAlive/About/Advanced 等设置和详情）由 `02-settings-screens.md` 负责；本文对这些页面所消费的主题 token、通用行/弹层与资源作权威交叉规格。
- C 区完整覆盖 Tutorial、权限流程、通用弹层、选择器、Slash popup、Toast/状态、工具确认与写入复核、SkillHub/内置浏览器/命令展示等剩余次级界面，以及 `ui-theme` 模块全部 Java View 与 drawable。
- `rg --files` 核验：旧工程 UI 资源中**没有 `res/layout`**；所有 view tree 都由 Java 构造。不要在迁移时误以为存在 XML 布局可直接转换。
- 搜索 `Banner|Snackbar` 后没有自定义 banner 或 Snackbar 实现；瞬时反馈统一是 Android `Toast`。不能凭设计习惯新增顶部 banner。

源定位约定：下文路径均相对旧工程根目录。

## 2. 全局视觉基础（所有 A/B/C 都必须共享）

### 2.1 密度、间距、字体

`LineTheme` 是唯一的公共样式入口（`ui-theme/src/main/java/cn/lineai/ui/theme/LineTheme.java:14-177`）。

| token | 值 |
|---|---:|
| `XS / SM / MD / LG / XL / XXL` | `4 / 8 / 12 / 16 / 20 / 24 dp` |
| `FONT_XS / SM / MD / LG / XL / TITLE / XXL` | `11 / 13 / 16 / 17 / 20 / 22 / 26 sp` |
| 普通文本 | sans-serif，`includeFontPadding=false`，额外行距 `2dp` |
| medium 文本 | `sans-serif-medium` |
| API 29+ | `BREAK_STRATEGY_SIMPLE` |

`LineTheme.text(...).setTextSize(sizeSp)` 使用 Android 默认 SP；不得在 C++ 层把这些数字当物理 px。常用形状：`rounded(fill,r)`；`roundedStroke(fill,r,border)` 固定 `1dp` 边框；`roundedTop` 只圆上方；用户气泡半径 `18dp`；通用 pressable 半径 `12dp`（`LineTheme.java:99-145`）。

### 2.2 语义色板

色板字段完整顺序为：`bg, surface, surfaceElevated, surfaceLight, accent, accentDim, accentMuted, accentMuted2, text, textSecondary, textTertiary, textOnColor, border, borderLight, inputBg, userBubble, aiBubble, danger, warning, success, processing, overlay, codeBg, codeBorder, dangerMuted, dangerMuted2, processingMuted, diffAddBg, diffDelBg, diffAddText, diffDelText`（`core-model/src/main/java/cn/lineai/model/ThemePalette.java:61-156`）。

下表保留最常直接影响复刻的实色；透明衍生色仍须按源构造值实现。

| mode | bg / surface / elevated / light | accent | text / secondary / tertiary | border / light | user / AI | danger / warning / success | code bg / border | diff add / del bg |
|---|---|---|---|---|---|---|---|---|
| dark | `#171819 / #242629 / #292D31 / #34393F` | `#E5E9EE` | `#EDF0F2 / #969DA5 / #969DA5` | `#383D42 / #30353A` | `#242629 / #171819` | `#D7A6AB / #D8B986 / #ADCEB8` | `#1D1F21 / #383D42` | `#22322A / #35272B` |
| light | `#FCFCFD / #F0F1F3 / #EAECF0 / #E1E4E9` | `#333B46` | `#24262A / #6C737D / #6C737D` | `#E1E4E8 / #EAECF0` | `#F0F1F3 / #FCFCFD` | `#9C5058 / #8D6C32 / #35684D` | `#F0F1F3 / #E1E4E8` | `#EDF4EE / #F8EDEF` |
| coffee | `#F4EFE6 / #EEE5D8 / #E7DCCA / #DED0B9` | `#D97757` | `#2B2118 / #6C5A49 / #9B8976` | `#DDD0BF / #E8DDCF` | `#B86F50 / #EFE4D4` | `#B5473F / #B7791F / #6A7F46` | rgba brown `.07 / .14` | rgba green `.14` / red `.12` |
| vscode | `#1E1E1E / #252526 / #333337 / #414145` | `#007ACC` | `#D4D4D4 / #A6A6A6 / #6A6A6A` | `#3C3C3C / #454545` | `#094771 / #252526` | `#F48771 / #CCA700 / #89D185` | `#1E1E1E / #3C3C3C` | green/red `.12` |
| githubDark | `#0D1117 / #010409 / #21262D / #30363D` | `#2F81F7` | `#E6EDF3 / #8B949E / #6E7681` | `#30363D / #21262D` | `#1F6FEB / #161B22` | `#F85149 / #D29922 / #3FB950` | `#0D1117 / #30363D` | green/red `.14` |
| gruvbox | `#282828 / #1D2021 / #3C3836 / #504945` | `#FABD2F` | `#EBDBB2 / #BDAE93 / #928374` | `#504945 / #665C54` | `#458588 / #32302F` | `#FB4934 / #FE8019 / #B8BB26` | `#1D2021 / #504945` | green/red `.13` |
| highContrast | `#000000 / #050505 / #222222 / #333333` | `#64D2FF` | `#FFFFFF / #C7C7CC / #8E8E93` | `#666666 / #3A3A3C` | `#004D80 / #101010` | `#FF453A / #FFD60A / #30D158` | `#000000 / #555555` | green/red `.18` |

精确构造值见 `ThemePalette.java:273-354`。dark 的透明值分别是：accent `.08/.13`、overlay 黑 `.45`、danger `.10/.18`、processing `.10`；diff 文本 `#ADCEB8/#D7A6AB`。`system` 在 `ThemePalette.forMode()` 本身会落到 dark，实际系统明暗必须由上层先解析（`ThemePalette.java:159-185`）。

custom 以 light 为 base，覆盖 `surface/elevated/input=#FFFFFF`、`surfaceLight=#ECECF1`、`userBubble=#0A84FF`、`aiBubble/codeBg=#F2F2F7`、`codeBorder=#D9D9DE`（`ThemePalette.java:357-367`）。只有 19 个 `EDITABLE_KEYS` 可改；accent muted、overlay、danger muted、processing muted、diff 色保持 base 的派生值，不会随自定义 accent/danger 重算（`ThemePalette.java:39-59,399-422`）。这是旧行为，复刻阶段不要“修正”成自动派生。

### 2.3 `ui-theme` 自定义 View/Drawable

#### `IconButtonView`

View tree 是单个 `AppCompatImageView`：`FIT_CENTER`、透明背景、初始零 padding、默认 clickable/focusable；通过 `SRC_IN` 色滤镜着色。`setIconSizeDp(container,icon)` 按当前实际宽高重新居中，避免非方形容器偏移；未知 type 回退 plus（`IconButtonView.java:184-258`）。

84 个 type 与资源一一映射（`IconButtonView.java:11-94,96-182`）：

```text
menu plus shield ellipsis_vertical arrow_up square chevron_down x folder_open folder_plus
archive message_square trash_2 check file_plus copy rotate_ccw folder file file_text file_code
chevron_left chevron_right box monitor brain package palette database book_open battery_charging
flask_conical cpu zap smile expand scroll_text sparkles globe external_link message_circle sun moon
coffee code contrast git_branch paintbrush save refresh_cw upload power settings git_compare bell
music smartphone square_function user bug download boxes sliders_horizontal file_up search server
terminal shield_check clock_3 message_square_text bot circle_check circle_x loader file_pen_line wrench
play circle_alert lineai_mcp share_2 quote text_cursor check_square image
```

所有 84 个 drawable 外框都是 `24dp × 24dp`；其中 83 个 Lucide 图标是 `24×24` viewport、白色 `#FFFFFFFF`、`2` 宽 round stroke、透明 fill；`ic_lineai_mcp.xml` 是 `180×180` viewport、三条白色 `12` 宽 round stroke。运行时白色会被 `IconButtonView` tint 覆盖。源：`ui-theme/src/main/res/drawable/*.xml:1-end`。

旧 View 中 `setClickable` 同时切换 accessibility importance（`IconButtonView.java:187-191`）。迁移仍要保留 visual/click/focus 行为，但不要把旧 Accessibility/手机控制能力带入新工程；普通控件的系统可用性语义与“无障碍控制功能”不是一回事。

#### `BoundedScrollView`

单子节点垂直 ScrollView，构造时接收最大高度 dp；`onMeasure` 把高度 spec 限制到该 cap。仅当内容确实可滚动、且手势方向还有滚动余量时才请求父层不拦截；ACTION_UP/CANCEL 恢复父层处理（`BoundedScrollView.java:19-126`）。用于 tool detail、thinking、dialog body，不能换成无上限滚动。

#### `FlowLayoutView`

水平流式布局，默认水平/垂直间距均 `8dp`；测量时考虑 child margin、GONE 与可用宽度，超宽自动换行；布局从左到右，不含 RTL 分支（`FlowLayoutView.java:19-104`）。1:1 阶段应保留 LTR 行为；若后续改进 RTL，须作为单独行为变更验收。

#### `ThinkingBlockView`

```text
ThinkingBlockView(VERTICAL)
├─ header(HORIZONTAL, minHeight 48dp, padding 0/8/0/8, clickable)
│  ├─ label(weight=1, 13sp, secondary)
│  └─ chevron(container 28×32dp, icon 16dp)
└─ BoundedScrollView(maxHeight=180dp, visible iff expanded)
   └─ content TextView(13sp, tertiary, extra lineSpacing 4dp)
```

expanded 状态按 message id 保存到静态 map；header 在 streaming/done 间切换 label；Markdown 子集仅解析 `*`, `_`, `**`, `__`, `***`, `___` 强调，不处理 code span/escape（`ThinkingBlockView.java:36-130`; `InlineEmphasisParser.java:15-169`）。源中虽然声明 `ObjectAnimator pulseAnimator`，`setStreaming` 只取消并把 alpha 复位，没有启动动画；**实际无 pulse**（`ThinkingBlockView.java:107-116`）。

### 2.4 共享次级页骨架

```text
ScreenScaffoldView(VERTICAL, BG)
├─ ScreenHeaderView
└─ ScrollView(fillViewport=false, weight=1)
   └─ content LinearLayout(VERTICAL, bottomPadding=100dp)
```

- `ScreenHeaderView`：水平、padding `16/12/16/12dp`；返回 action `36×36dp`、icon `22dp`；title 17sp medium 居中占 weight；右 action 同宽，否则插入 `36×36` spacer；底边是**1 physical px**（`ScreenHeaderView.java:19-70`）。
- `ScreenScaffoldView`：默认 content 左右 `16dp`、上 `8dp`、下 `100dp`（`ScreenScaffoldView.java:18-53`）。
- `ScreenSurfaceView`：内容最大宽 `792dp`，大屏通过左右 gutter 居中（`ScreenSurfaceView.java:6-16`）。
- `AdaptiveActionsView`：先无约束测量可见 child，总宽另加每 child `16dp` 预算；超过可用宽则竖排、间距 `8dp`，否则水平；最小高度预算 `48dp`（`AdaptiveActionsView.java:12-32`）。
- `ActionRowView`：水平 row，padding `16/12`；可选 leading icon `36dp` 容器/`20dp` 图标、title 16sp、desc 13sp、末尾 chevron `24/16dp`（`ActionRowView.java:18-59`）。
- `FileActionRow`：文件 action 变体，icon `40/20dp`、title 16sp、desc 13sp、右图标 `32/17dp`，padding `14/10dp`（`FileActionRow.java:20-82`）。
- `SimpleScreenContent` 提供 section title 20sp、body 13sp 与常规 card/row 组合（`SimpleScreenContent.java:17-142`）；B 区会复用。

## 3. Tutorial

入口 route 为 `tutorial` 和 `tutorialFromSettings`，两者创建完全相同的 `TutorialScreenView`，返回行为交给主壳栈（`ScreenFactories.java:825-848`）。

### 3.1 精确 View tree

```text
ScreenScaffoldView("Tutorial")
└─ content(padding 16/12/16/100dp)
   ├─ mode selector(VERTICAL, elevated, radius16 + 1dp light border)
   │  ├─ Simple row(minHeight62, padding16/12)
   │  │  ├─ labels(weight=1): title 16sp medium + desc 11sp tertiary(top2)
   │  │  └─ check(container18, icon16)
   │  └─ Pro row(same)
   ├─ subtitle(13sp secondary, lineSpacing +3dp, top16/bottom12)
   ├─ HorizontalScrollView(no scrollbar)
   │  └─ section chips(HORIZONTAL, gap8)
   │     └─ chip(11sp medium, radius14 + 1dp border, padding12/5)
   └─ MarkdownView(codeWrap=true)
```

选中 mode row 使用 `ACCENT_MUTED` 无描边背景、title/check accent；未选中透明、title text、check 空。section chip 选中为 accentMuted + accent border/text，未选中 surfaceElevated + borderLight/secondary（`TutorialScreenView.java:39-125,226-296`）。

内容从 `app/src/main/assets/tutorials/simple.md`（935 行）与 `pro.md`（568 行）加载；只把 fence 外的 `## ` 标题提取为 chips。点击 chip 后扫描 Markdown 渲染出的 TextView，以精确标题文本匹配，并 `smoothScrollTo(y-8dp)`（`TutorialScreenView.java:128-224,299-319`）。加载异常 log error、短 Toast `screen_tutorial_loading_error`，并显示 fallback 字符串。

资产中的一级/二级结构必须原样迁移：simple 为“LineCode 零基础使用教程”，二级从 `0. 先认识几个词` 到 `29. 出问题怎么排查` 再到 `附：隐私与安全提示`；pro 为“LineCode 专业模式教程”，二级从 `1. 项目与多工作区` 到 `14. 推荐安全基线`（两文件标题行见各自 `:1-935`/`:1-568`）。注意两份旧教程都含 Accessibility/手机控制章节：simple `:660-684`，pro `:453-463`，迁移时应删除这些章节并同步移除对应 chip，而非保留死文档。

## 4. 权限流程；Accessibility 只做删除核验

### 4.1 文件与通知权限

通用权限弹层由 `PermissionModeController.showPermissionSheet()` 填充 `BottomSheetView`（`app/src/main/java/cn/lineai/mvp/PermissionModeController.java:96-166`）：

1. `Auto`：切 agent mode，保存 permission mode `auto`。
2. `Confirm`：切 agent mode，保存 `confirm`。
3. `Read-only`：直接切 chat mode `chat`，不写普通 permission mode。
4. `Manage all files`：只展示授权状态/入口；点击触发系统授权。
5. `Revoke`：仅在已有存储权限时出现；清空授权并短 Toast `chat_permissions_cleared`。

Android 实现（`PermissionUiHelper.java:22-133`; `workspace/StoragePermissionManager.java:11-34`）：

- API 30+：`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` + package Uri；失败退到 `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`，再失败到应用详情页。授权判断 `Environment.isExternalStorageManager()`。
- API <30：请求 `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE`，request code `7002`；两者都 granted 才视为授权。
- API 33+：请求 `POST_NOTIFICATIONS`，permission request code `7003`；更低版本直接视为可通知。
- `onRequestPermissionsResult` 只按对应 code 转发布尔结果，没有自定义拒绝页。

### 4.2 SAF 系统选择器

`SafPickerDelegate` 不绘制 app 内 UI（`SafPickerDelegate.java:31-321`）：

| 请求 | code | intent / 约束 |
|---|---:|---|
| 目录 | 7001 | `ACTION_OPEN_DOCUMENT_TREE`，持久化 read/write Uri grants |
| 文档 | 7003 | `ACTION_OPEN_DOCUMENT`，`CATEGORY_OPENABLE`，调用方 MIME |
| 新建文档 | 7004 | `ACTION_CREATE_DOCUMENT`，`CATEGORY_OPENABLE`，传 title/MIME |
| 图片 API 33+ | 7005 | `MediaStore.ACTION_PICK_IMAGES` |
| 图片旧版 | 7005 | `ACTION_OPEN_DOCUMENT`, `image/*` |

通知 permission code 7003 与 SAF document activity-result code 7003 数值相同，但属于不同回调命名空间；C++/JNI 迁移可避免复用编号，但行为不可串线。

### 4.3 Accessibility/手机控制删除核验（禁止迁移 UI）

不要实现 `PhoneControlScreenView` 的 view tree。删除完成标准是 `rg` 不再命中以下生产接线：

- Manifest 的 `BIND_ACCESSIBILITY_SERVICE` 声明、`LineCodeAccessibilityService` service 与 `@xml/accessibility_service_config`（`app/src/main/AndroidManifest.xml:16,65-73`；该 XML 当前请求 all events、interactive windows、view ids、window content、screenshots）。
- `AdvancedFeaturesScreenView` 的 `phoneControl` 卡片（`:38`）以及 `ScreenFactories` 的 route/系统设置跳转（`:655-714`）。
- `LineCodeAccessibilityService.java`、`PhoneControlRepository.java`、`PhoneControlController.java`、`core-api/.../AccessibilityStateProvider.java`、`feature-tool/.../PhoneControlService.java` 和七个 `Phone*Tool.java`。
- `MainDependencies.java:37,156-161`、`MainCoordinator.java`、`MainUiController.java`、`MainChatView.java` 的注册/状态接线。
- `ToolDisplayCategory.PHONE_CONTROL`、`PhoneControlToolCallViewFactory`、`ToolCallInputParser/ToolCallUtils` 的 phone 分支、preview sample。
- 三语 `screen_phone_control_*`, `tool_group_phone_control_*`, `tool_call_phone_*`, `phone_*` strings，教程的手机控制章节，`app/lint.xml` 与相关测试预期。

这里删除的是远程操控手机的 Accessibility 功能；Android 自带 focus/contentDescription 等普通可用性信息不应被机械删除。

## 5. 通用 Dialog

### 5.1 尺寸和 Window 行为

- `DialogDimensions.insetDialogWidth()` 实际返回 `min(screenWidth-32dp, 560dp)`，最低只钳到 `1px`；注释中的“min 280dp”并未实现（`DialogDimensions.java:14-26`）。复刻以代码为准。
- `DialogBuilder` 内容最大高 `82%` viewport，放入无 scrollbar 的 `BoundedScrollView`；panel 默认 `SURFACE_ELEVATED`, radius `24dp`（`DialogBuilder.java:16-128`）。
- `showInset`：CENTER，inset width；`showBottom`：BOTTOM、`y=16dp`，但仍是同一个 inset width，并非全宽。
- `LineAlertDialog` 按当前 BG 红通道是否大于 128 选择 Android Material light/dark alert theme；window radius `24dp`；message 16sp、额外行距 `6dp`；按钮 14sp、no caps、minHeight 48dp，danger positive 可覆色（`LineAlertDialog.java:12-35`）。

### 5.2 具体树和交互

- `DialogManager.confirm/message/input`（`DialogManager.java:20-162`）：confirm 有 cancel/positive，可 danger；message 只有 positive；input 为单行 EditText、禁 suggestions、select all、左右 `16dp`/上下 `8dp`，show 后强制软键盘。
- `LegalDialog`（`LegalDialog.java:19-102`）：不可取消/不可点外部关闭；panel vertical，radius24、padding24；title 17sp medium；divider **1px**、上下12；ScrollView weight1，body 16sp +4dp 行距；`AdaptiveActionsView` 右对齐，按钮 16sp medium、padding16/12。Window 是 `MATCH_PARENT` 全宽，这一点不同于 inset dialog。
- `UserAgreementDialog` 仅把标题/正文/同意/退出委托给 `LegalDialog`（`UserAgreementDialog.java:10-24`）。
- `TextSelectionDialog`（`TextSelectionDialog.java:13-35`）：`ScrollView > EditText`；只读可选择、15sp、BG、padding24、打开即 selectAll；标题“长按选择文本”，Close。
- `ModelPickerDialog`（`ModelPickerDialog.java:20-105`）：BOTTOM inset panel，`roundedTop(surfaceElevated,16)`；handle `36×4dp`, r2, top8/bottom4；title 17sp medium、padding left/right16 bottom12；1px divider；ScrollView maxHeight `420dp`；rows padding16/14、text16sp、custom 模型 accent；选中项 check 容器18/icon16；panel bottom padding12。
- 文件 action dialog（`MainChatView.java:534-573`; `FileActionRow.java:42-82`）：先关闭除 Drawer 外的 overlay；inset panel `SURFACE_ELEVATED` r16、padding16，title17 medium，optional subtitle11/top4，1px divider top12/bottom4；每 row min52、上下 padding16、pressable 背景，label16，desc11/top6。`id` 以 `file:delete:` 开头时 label danger；点击先 dismiss 再回传 id。
- 导出格式选择（`app/src/main/java/cn/lineai/mvp/ShareController.java:36-75`）是平台原生 `AlertDialog.Builder.setItems`，标题 `dialog_export_format_title`，选中后后台执行 formatter；结果分 share-file / clipboard / share-text。大 clipboard 只短 Toast 警告。它没有自定义 card、icon 或额外确认。

## 6. Bottom sheets 与文件选择器

### 6.1 通用 `BottomSheetView`

```text
FrameLayout MATCH_PARENT (initial GONE)
├─ backdrop(MATCH_PARENT, OVERLAY, click closes)
└─ InsetSheetLayout(bottom|center_horizontal, maxWidth560, margins L/R/B=16)
   └─ panel(VERTICAL, radius24 + 1dp border, maxHeight=viewport-64)
      ├─ handle(36×4dp, radius2, top8 bottom4, centered)
      ├─ title(17sp medium, padding24/12/24/20)
      ├─ divider(1px)
      └─ ScrollView(no scrollbar, bottomPadding16)
         └─ options(VERTICAL)
            └─ row(minHeight52, padding16/14)
               ├─ labels(weight1): label16sp + optional desc11sp(top2)
               └─ optional check(container18, icon16)
```

选中 row 用 `ACCENT_MUTED`；未选透明。普通点击调用 listener，但组件自身不自动关闭，关闭责任在调用方。支持 deleteAction 的 option 可长按：系统 long-press timeout/slop，触发 haptic 后弹 danger confirm；轻触仍走原点击（`BottomSheetView.java:24-297`）。

开：panel 自下方 offset 到 0、backdrop 0→1，`180ms Decelerate`；关：panel 下移、backdrop 1→0，`150ms Accelerate`，结束后 GONE/回调（`:121-160`）。`InsetSheetLayout` 宽为 `min(parent,560dp)`、高受 availableHeight cap（`InsetSheetLayout.java:6-14`）。

### 6.2 Attachment picker

```text
FrameLayout overlay
├─ backdrop
└─ panel(bottom centered, L/R/B 16, radius24 + border; cap min(640,viewport-64))
   ├─ header(padding20/20/20/16)
   │  ├─ title 17sp medium(weight1)
   │  ├─ subtitle 11sp tertiary(singleLine, middleEllipsis, top3)
   │  └─ close 48×48dp/icon18, leftMargin12
   ├─ divider 1px
   └─ body(weight1)
      ├─ status(center, 13sp, padding20) [loading/null/empty]
      └─ ScrollView > tree(padding8/8/8/16)
```

tree row：minHeight52；左 padding `12 + 18×depth dp`，右12、上下8；directory 为 chevron16 + gap8 + folder17；file 用16dp spacer；root name medium 并带 path。file 末端选择 pill `26×26`, r13, icon14，未选 surfaceLight/plus、已选 accentMuted/check accent。空目录 11sp tertiary。状态分别 `Reading files… / No files available / Empty directory`（`AttachmentPickerSheetView.java:22-335`）。

首次 panel 构造宽 560；动态 resize 尝试 cap `max(360,screenHeight-48)`，最终仍经 measure 受 viewport-64 约束。开/关从屏幕右侧水平滑入/滑出，180/150ms，backdrop 同步淡入淡出（`:122-203`）。

`AttachmentPickerCoordinator` 负责 local/SSH 文件树加载、已选 path 集合和回调，不创建新视觉（`app/src/main/java/cn/lineai/mvp/AttachmentPickerCoordinator.java:28-227`）。

四个主壳 overlay（Drawer、generic sheet、directory picker、attachment picker）互斥；打开一个前按对象 identity 关闭其他三个，`closeAll()` 全关（`OverlayManager.java:18-57`）。返回键优先级是 screen host → directory picker → attachment picker → generic sheet → Drawer（`BackNavigation.java:20-84`；叠层顺序由 A 文档权威定义）。

### 6.3 Directory picker

外壳/动画与 Attachment picker 相同；header 右侧不是 close，而是 confirm：`48×48dp`、check icon22、radius14、accent；只有 `selectedPath` 非空才 visible。tree `padding12/8/12/24`（`DirectoryPickerSheetView.java:20-406`）。

- null tree：全区居中 `Directory not accessible`。
- loading：树内 11sp tertiary 单行 end-ellipsis。
- parent row：min52、surfaceLight r8、bottom8；back chevron16；title13 medium；path11。
- child row：min52；selected 为 accentMuted r8，title accent medium，path tertiary；folder17 + right chevron16；文件仍显示但 disabled/tertiary。
- confirm callback 只返回当前 selectedPath；backdrop/系统 back 关闭。

`DirectoryPickerController` 给 local/SSH 提供树数据和返回上级；旧实现有直接中文 loading 文案，迁移应使用现有字符串语义但视觉不变（`DirectoryPickerController.java:29-233`）。

## 7. Popup、Toast 与状态

### 7.1 Slash command popup（与 A 交叉）

`SlashCommandPopup` 是 `PopupWindow > ScrollView > content LinearLayout`：content `INPUT_BG`、radius14 + 1dp border、padding8；popup transparent、outsideTouchable、**not focusable**（`SlashCommandPopup.java:47-68`）。

- title：13sp medium、单行 end ellipsis、高22、左右 margin12、top1。
- row：vertical、minHeight52、padding16/14；主行 label 13sp medium、单行 ellipsis；以 `/` 开头的 command token 加 accent + bold；selected 在右侧显示 `7×7dp` accent 圆点（r4）；desc 11sp tertiary、单行、top1。
- 点击先 dismiss，再主线程 post callback。
- width=`anchorWidth-32dp`；x=`anchorLeft+16dp`；y 在 anchor 上方 `8dp`；高度 `min(contentHeight,max(52dp,anchorScreenY-24dp))`（`:74-242`）。

### 7.2 Toast 清单

没有 banner/Snackbar。按所有 `Toast.makeText` 调用归类：

| 所属 | 反馈 | 时长/源 |
|---|---|---|
| Tutorial | 资产加载失败 | SHORT；`TutorialScreenView.java:315` |
| 权限 | 已撤销文件授权 | SHORT；`PermissionModeController.java:159` |
| Attachment/聊天 | 图片读取中、读取失败；空聊天不可导出；打开链接失败；已复制 | SHORT；`MainChatView.java:636,891,909,1043,1059`（A 交叉） |
| Markdown | 代码已复制 | SHORT；`markdown/.../MarkdownCodeBlockView.java:77`（A 交叉） |
| Workspace share | provider 打开失败 | LONG；`WorkspaceShareHelper.java:41` |
| Skill Store | 登出成功 SHORT；错误 LONG | `SkillStoreScreenView.java:424,432` |
| Skill detail/file | 不支持/加载/复制/prompt复制/仅HTTPS/星标状态 SHORT；网络、评论、安装错误与提交/安装成功多为 LONG | `SkillStoreDetailScreenView.java:469-486,526-535,561-678,744-807,1007-1095,1229-1258` |
| SkillHub login/publish | login/publish 成功 SHORT；publish error LONG | `SkillHubLoginScreenView.java:150`; `SkillHubPublishScreenView.java:182-188` |
| Extension/Model/Settings | 输入校验多 SHORT、异步失败 LONG | B 权威；调用清单见 `AgentExtensionEditScreenView.java:243-286`, `ModelAddScreenView.java:131-556`, `McpExtensionEditScreenView.java:97-238`, `ThemeSettingsScreenView.java:203`, `PromptTemplatesScreenView.java:139-148`, `ErrorLogsScreenView.java:31,64`, `MemorySettingsScreenView.java:349` |

迁移到 Win 时不能直接“显示 Android Toast”；应由平台 feedback port 决定 native transient notification，但文本、触发条件和短/长相对时序保持一致。

### 7.3 Loading / empty / error

| 组件 | Loading | Empty | Error / retry |
|---|---|---|---|
| 通用文件 sheet | 13sp 居中 status | no files / empty dir | inaccessible；directory 可保留树内 loading message |
| Tool card | `result==null` 或 review `running/pending` 显示 ProgressBar；error=FAILED；非空 content=SUCCESS；否则 UNKNOWN | 空 output 仍 UNKNOWN | `BaseToolCallView.java:43-148` |
| Tool write Diff | 文本 `Loading changes…`，diff 后台 2-thread pool | 无 diff=`No diff available` | load exception 静默转 unavailable，操作仍可 review；`ToolCallWriteView.java:117-161` |
| Skill store | 居中 ProgressBar + `Loading page N…`，先清 results | `No matching Skills found` | status 两行 `Load failed…\nmessage`，点击 retry；`SkillStoreScreenView.java:491-527` |
| Skill detail | 初始居中 ProgressBar | 每 tab 使用 tertiary 13sp emptyText | clickable danger error section，点击清空并 reload；`SkillStoreDetailScreenView.java:76-104,1329-1361` |
| Skill publish | ProgressBar 初始 GONE，busy 时显示且所有 field/button disabled | 无可发布 skill 时 selector disabled、publish disabled alpha `.45` | exception LONG Toast；`SkillHubPublishScreenView.java:77-92,164-203` |

## 8. Tool approval 与 post-write review（与 A 的 tool card 交叉）

### 8.1 执行前审批 `ToolApprovalView`

```text
outer LinearLayout(VERTICAL, padding16/10/16/16)
└─ card(VERTICAL, elevated, radius20 + 1dp border, elevation2, padding16/12)
   ├─ heading(HORIZONTAL)
   │  ├─ terminal/wrench icon(container24×28, icon16)
   │  └─ title 12sp secondary(left4)
   ├─ reason 15sp text(top6, lineSpacing+5)
   ├─ BoundedScrollView(maxHeight156, top16,bottom10)
   │  └─ command/details 13sp monospace selectable + horizontal scrolling
   └─ Adaptive button area
      ├─ Reject
      ├─ Always Allow [conditional]
      └─ Allow Once
```

按钮 minHeight44、13sp、padding8；横排时等权 gap6，宽不够竖排、全宽 gap6。Allow Once 为 accent/textOnColor/r18；另两个为 stroke/border/r18。Always 仅 `canAllowPermanently=true` 时显示。点击后全部 disable：一次=`accepted`，永久=`permanent`，拒绝=`rejected`（`ToolApprovalView.java:22-150`）。shell 标题/icon=terminal，正文 command；delete 标题=delete，正文 reason + paths；cwd 会置于 command 前。

`ToolConfirmationController` 管 pending call/scope：scope 变化、取消或无法继续会 reject；永久授权只允许 shell 且 scope 非空。控制器还接受 `session_auto`，但 `ToolApprovalView` 没有产生该 action，属于旧遗留，不应凭空新增按钮（`ToolConfirmationController.java:37-360`）。

### 8.2 写入后的 Diff review

`ToolCallWriteView`（`tool-ui/.../ToolCallWriteView.java:31-182`）：

- header min48：file-pen icon `24/16`，14sp secondary middle-ellipsis label，chevron `24/14`；点击展开。
- detail：`CODE_BG`、radius12 + code border；file header 左 padding12、filename13sp，copy `44×44/icon16`；diff 在 `BoundedScrollView(max224)`。
- error/review message 13sp、padding14/10；actions end aligned、padding8/6；Revert/Accept 各 min48、13sp、horizontal padding14。
- actions 仅 result 有 diffId 且 review state 既非 accepted 也非 rejected 时显示。Accept 发 `accepted`；Revert 发 `rejected`。
- label 状态 running / failed / pending review / reverted / edited(created)，附绿色 `+N` 与 danger `−N`。

`DiffView` 每行 monospace 12sp、padding10/3；add/del 使用 `DIFF_ADD_BG/DIFF_DEL_BG` 和对应文本色，context 为 code bg/textSecondary；行号列固定语义、尾部无换行标记由 strings 提供（`DiffView.java:13-66`）。

`ToolReviewController` 接 accepted/rejected：accepted 只更新状态；rejected 对 diff 后台执行 revert，成功 review message `Reverted change to …` 并刷新文件树，失败写失败 message；非 diff 只记录 review state（`app/src/main/java/cn/lineai/mvp/ToolReviewController.java:34-208`）。

delete card 自身不画确认按钮，明确把 active request 放在底部 composer slot；展开只显示 reason/paths/error 的 `BoundedScrollView(max200)`（`ToolCallDeleteView.java:21-70`）。Tool error 是 `CODE_BG` radius12/border、`BoundedScrollView(max240)`、13sp danger selectable、padding14/12、行距6（`ToolErrorView.java:11-36`）。

## 9. 其余 C 区次级页面

这些 route 全部由 `ScreenFactories.java:1316-1530` 接入。SkillHub 与 Extension 设置存在边界交叉；本文记录其运行时 UI，B 文档记录入口/设置关系。

### 9.1 Skill Store 列表

根为 `ScreenScaffoldView`，content padding `16/16/16/100`（`SkillStoreScreenView.java:57-98`）：

```text
content
├─ intro(HORIZONTAL): copy(weight1, title20 + desc13 top4) + sparkles 44/icon24,r12
├─ search(top16, input r12+border, padding8/0/12/0)
│  ├─ search 36×44/icon18
│  └─ EditText(weight1,height48,16sp, IME search)
├─ filters(HORIZONTAL,top8): 3 equal chips(11sp,padding8,r10+border)
├─ account row(top8,r12,padding12/8): user36/icon19 + title13/desc11 + action28/icon16
├─ feature-center button(top8, initially GONE)
├─ ProgressBar(center,top16)
├─ status(13sp tertiary center,top12)
├─ results(VERTICAL,top12)
│  └─ skill card(HORIZONTAL,r12+border,padding12/12/8/12,bottom8)
└─ pager(center,top12): Previous + Next(gap8)
```

filter 选中 accentMuted/accent border/text；未选 elevated/border/secondary。account 检查失败会改为 retry 并换 refresh icon；未登录打开登录，已登录打开账号 inset dialog。账号 dialog r16+border/pad16：user54/icon27、name17、handle13、connected pill r9、横向 Continue/Logout；logout danger muted/border。card icon48/icon25、title16单行、verified/API/category tags、description13最多2行、stats11、chevron24/16（`:100-488,519-637`）。

远程 icon 先保留 package 占位；`SkillIconLoader` 按 URL tag 防止 recycled target 串图，静态 LRU 32 张，后台下载，decode 以 `192px` 为目标做 2 倍阈值的 power-of-two sample，成功后清 tint；失败静默保留占位（`SkillIconLoader.java:13-69`）。

### 9.2 Skill detail、文件预览、评论与安装

根同 scaffold，content `16/16/16/100`（`SkillStoreDetailScreenView.java:67-104`）。主要树：

- hero r14+border/pad16：package `64/icon32/r14`；title20最多2行；identity13；description16 +4 line spacing；统计/版本/安全 tags r8。
- 四个等权 action：copy/star/share/full，gap8；每个 r10+border/pad8，icon20/16 + label13。这里**没有自适应换行**，窄屏/大字体有溢出风险（`:107-250`）。
- install full-width accent r12，padding16/12，top16。
- tabs：HorizontalScrollView，无 scrollbar；内层 surfaceLight r10/pad4；每 tab 13sp、padding12/8，active elevated r7/accent（`:252-314`）。
- 内容 section：elevated r12+border、padding16/12；header icon28/17 + title16；section 间 top12，内部 item top8（`:880-942`）。
- overview metadata 两列 surfaceLight r8/pad12/8；Markdown reader surfaceLight r10/pad12，code wrap；pinch zoom scale持久化 `skill_store_reading/markdown_text_scale`（`:317-390,1349-1357`）。
- files：row surfaceLight r9、padding12/8，file icon32/18、name13 medium、directory+size11、chevron24/16。仅 md/txt/json/js/ts/java/py/sh/xml/yml/yaml/toml/ini/properties/csv 可 preview（`:393-499`）。
- preview dialog：`LineAlertDialog`；host 左右16、上下8；Markdown 显示 zoom hint、code wrap/pinch scale/link handler；非 Markdown 为13sp monospace selectable；Close/Copy（`:501-535`）。
- comments：post button accent r9/pad12/8；comment card surfaceLight r8/pad12/8，header11、content13、like/reply/all/delete actions11，reply 每深度左缩进16（`:538-741`）。comment inset dialog r16+border/pad16，title17、hint11、EditText 16sp min4/max8行 r10+border，cancel/submit 横向等权 gap8；内容必须 1–500 code points（`:561-679`）。
- versions/evaluation/preview tabs 的 empty 都是13sp tertiary；evaluation score20 accent、最多5条 bullet；test prompt accentMuted r8，answer Markdown（`:813-878`）。
- install inset dialog r16+border/pad16：package42/icon22；title17；facts tags；两项 location row pad12、icon34/19、title13/desc11、右侧30格“✓”，selected accentMuted/accent border；脚本/API key 时 warning card accentMuted r9、warning icon24/16；cancel/install 等权 gap8（`:1098-1327`）。

### 9.3 SkillHub center/web/login/publish

- Center（`SkillHubCenterScreenView.java:17-122`）：scaffold + padding16/16/16/100；notice elevated r12+border/pad12；四组 section title16 top16/bottom4；entry elevated r11、padding12/8、top8，title13、desc11 top2、chevron26/16。分组/目的地：account/social 7 项、creator 2、discover 5、enterprise 5。
- Web（`SkillHubWebScreenView.java:26-170`）：vertical BG；header；官方 notice accentMuted r10、padding12/8、外 margin12/8，shield26/icon16 + 11sp；WebView weight1。固定 `skillhub.cn` 路径白名单，JS/DOM 开、file/content/mixed/third-party cookies/window open 关；离开销毁。
- Login（`SkillHubLoginScreenView.java:28-193`）：header；credential notice accentMuted r10，外 margin16/8，shield28/icon17；status 11sp center；WebView weight1。每1000ms检查 session，成功 status accent、SHORT Toast，150ms 后返回；host 白名单同 Web，离开销毁。
- Publish（`SkillHubPublishScreenView.java:40-210`）：scaffold/padding16/16/16/100；notice elevated r12+border/pad12；skill selector input r12+border/pad12，点击循环下一 skill；slug/display/version 各为 label13 + EditText16/r12+border/pad12/8；publish full width accent r12/pad12，top16；ProgressBar center top12。排除 SSH skill，默认 slug 规范化、version `1.0.0`。

### 9.4 内置浏览器与 shell command

- `InAppBrowserScreenView`（`:28-82`）：`ScreenSurfaceView(VERTICAL,BG)` > header > address 13sp secondary、single/middle ellipsis、padding28/0/28/16 > WebView(weight1,BG)。URL 仅接受 `UrlPolicy.normalizeHttpOrLocalCleartextUrl`；不支持时显示纯文本 `Unsupported URL`。JS 由 Output setting 决定；DOM 开；file/content/file-URL/universal-file-URL/mixed content 禁止。
- `ShellCommandScreenView`（`:22-42`）：`ScreenSurfaceView` > header > ScrollView(weight1) > content(padding28/8/28/48) > command box(13sp monospace selectable、lineSpacing4、CODE_BG r12+border、padding12)。空值使用 `shell_command_empty`。

## 10. 动画、响应式与大字体

### 10.1 动画时间表

| 场景 | enter | exit | 位移/插值 | 源 |
|---|---:|---:|---|---|
| 次级 screen stack | 280ms | 220ms | forward 从右入/旧页左出，back 反向；enter Decelerate，exit Accelerate | `MainChatView.java:82-83,664-818`（A 交叉） |
| 通用 bottom sheet | 180ms | 150ms | Y 轴底部 + backdrop fade | `BottomSheetView.java:121-160` |
| Attachment/Directory picker | 180ms | 150ms | X 轴右侧 + backdrop fade | `AttachmentPickerSheetView.java:163-202`; `DirectoryPickerSheetView.java:173-216` |
| Working indicator | 1500ms loop | 停止即 cancel | linear shimmer；帧路径另为90ms节拍 | `WorkingStatusView.java:21-35,190-246` |

`WorkingStatusView` 是 3×3 dot matrix：画布16dp、dot radius1.35dp、gap8dp、minHeight24、水平 padding1；路径 `[1,2,5,8,7,6,3,0]` 每90ms，非活动 alpha `.18`、trail `.52`；shimmer 宽72dp、1500ms，将颜色向白混合72%。label 13sp monospace，宽按 `scaledDensity` 测量，end ellipsis。仅 attached+working 且系统 animator duration scale 非0时运行（`WorkingStatusView.java:21-252`）。

### 10.2 响应式/large-font 实际行为

- 大屏仅 `ScreenSurfaceView` 把内容 cap 到792dp；sheets cap 560dp；没有横竖屏专用 layout、dimension resource 或 `onConfigurationChanged` 自适应。
- `AdaptiveActionsView` 和 `ToolApprovalView` 会按测得 label 宽度由横转竖，是最明确的大字体保护。
- `LineTheme` 全局用 SP，绝大多数正文允许换行；`ThinkingBlockView`/`WorkingStatusView` 显式处理高度/宽度。
- 风险点必须在 parity 测试覆盖：Tutorial row 固定 min62；Bottom/file rows min52；Skill detail 四等权 action 不换行；Skill list title max1/description max2；header title 单行空间受两侧固定36影响；dialog/picker 固定 cap 可能在 1.5×–2.0× fontScale 下产生更多滚动。
- 旧代码没有明确 RTL 与 window size class 支持。1:1 首版不应借“响应式重构”改变排列；改进另立验收项。

## 11. XML/图片/字符串资源审计

### 11.1 layout、color、style、mipmap

- `layout/`：所有模块均为 0。
- `app/res/values/colors.xml:2`：light `line_bg=#FCFCFD`, `line_accent=#333B46`, `line_light_system_bars=true`；night 对应 `#171819/#E5E9EE/false`。
- `app/res/values/styles.xml:3-14`：`Theme.Material.NoActionBar`，无 title/action bar，sans，window/nav/status 使用 line_bg，status icon 明暗取 bool；API27+ 额外设置 light navigation bar（`values-v27/styles.xml:3-5`）。
- `terminal-provider/res/values/themes.xml:3-5`：独立 `Theme.Material.Light.NoActionBar`，window `#FCFCFD`（B/Extension 交叉）。
- launcher webp：mdpi 48、hdpi72、xhdpi96、xxhdpi144、xxxhdpi192，各有 square/round、带 alpha。属于品牌资产，应直接复用像素源，不用通用 icon 重画。

### 11.2 非 `ui-theme` drawable

- `app/drawable/ic_keepalive_notification.xml:2-9`：24×24/viewport24，白色实心 code brackets；KeepAlive 属 B，本文只登记资源。
- `markdown/drawable/ic_lucide_copy.xml:2-20`：24×24，白色 stroke2、round cap/join、透明 fill；A 交叉。
- `ui-theme` 的 84 个 drawable 已在 §2.3 全量登记；不存在 selector/shape/animation-list XML，所有背景 drawable 都是 Java `GradientDrawable` 动态生成。

### 11.3 strings 全量文件与 locale parity

| module | default | zh | ru | 结论 |
|---|---:|---:|---:|---|
| app | 1377 | 1377 | 1377 | key 集完全一致；C 相关 common/dialog/sheet/permission/toast/tutorial/skillhub 与 B/A 文案都在同一文件 |
| data | 48 | 48 | — | key 一致，prompt 文案 |
| feature-ssh | 14 | 14 | — | key 一致 |
| feature-tool | 197 | 197 | 197 | key 一致；phone 29 keys 必须随 Accessibility 功能删除 |
| markdown | 3 | 3 | — | key 一致：copy/copy success 等 |
| terminal-provider | 7 | 6 | — | zh 缺 `app_name`，其余一致 |
| tool-ui | 66 | 66 | 66 | key 集完全一致；tool status/review/diff 文案 |
| ui-theme | 2 | 2 | 2 | `thinking_label`, `thinking_done_label` |

源文件行数：app default/zh/ru `1501/1502/1499`；data `58/58`；feature-ssh `17/17`；feature-tool `250/250/249`；markdown `6/6`；terminal-provider `10/8`；tool-ui `69/69/69`；ui-theme `5/5/5`。计数按 `<string name>`，而行数差异来自格式。

app default 的关键范围：common `:2-33`；dialog `:86-88,889-902,986-987`；file/sheet `:709-714,904-958,1071`；toast `:966-990,1116-1119`；permission `:1112-1113,1157-1164`；SkillHub errors/UI `:1220-1468`。不要在 C++ 内硬编码英文/中文；三语 key 是迁移输入。

## 12. 已读清单、交叉引用与疑似遗漏

### 12.1 完整阅读/核验过的 C 权威源

```text
app/ui/component:
TutorialScreenView; DialogDimensions; DialogBuilder; LineAlertDialog; DialogManager;
LegalDialog; UserAgreementDialog; TextSelectionDialog; ModelPickerDialog; InsetSheetLayout;
BottomSheetView; AttachmentPickerSheetView; DirectoryPickerSheetView; SafPickerDelegate;
PermissionUiHelper; ToolApprovalView; WorkingStatusView; SlashCommandPopup; OverlayManager;
BackNavigation; SkillIconLoader;
AdaptiveActionsView; ActionRowView; FileActionRow; ScreenScaffoldView; ScreenHeaderView;
ScreenSurfaceView; SimpleScreenContent; SkillStoreScreenView; SkillStoreDetailScreenView;
SkillHubCenterScreenView; SkillHubWebScreenView; SkillHubPublishScreenView;
SkillHubLoginScreenView; InAppBrowserScreenView; ShellCommandScreenView;
ScreenFactory; ScreenRegistry; ScreenFactories.

app/mvp + workspace:
PermissionModeController; PermissionView; AttachmentPickerCoordinator; DirectoryPickerController;
PickerView; ToolConfirmationController; ToolReviewController; StoragePermissionManager.

shared/dialog call paths:
ShareController; MainChatView file-action/input/confirm/picker methods (call-site slice only).

ui-theme + model:
LineTheme; IconButtonView; BoundedScrollView; FlowLayoutView; ThinkingBlockView;
InlineEmphasisParser; ThemePalette; all 84 ui-theme drawable XML; all 3 locale string files.

tool-ui review/state subset:
ToolCallBlockView; BaseToolCallView; DiffView; ToolCallWriteView; ToolCallDeleteView;
ToolErrorView; all 3 locale string files.

resources/assets:
all res files under app/data/feature-ssh/feature-tool/markdown/terminal-provider/tool-ui/ui-theme;
tutorials/simple.md and tutorials/pro.md headings/content structure; AndroidManifest UI/service entries.
```

### 12.2 A/B 交叉但不在本文重复展开

- A：`MainChatView`, layout builder, Header, Drawer, ChatMessageList, User/Assistant/AssistantTurn, MessageActionBar, Composer, Markdown 全模块主体、所有 tool card factory/detail。本文的 BottomSheet/picker/dialog/popup/approval/review/theme 结论覆盖 A 的共用部分。
- B：所有 `*SettingsScreenView`, Model*, Extension*/MCP*, Memory, Theme editor, Storage/Data/Error/KeepAlive/About/Licenses/Advanced/SSH/Termux/TerminalProvider 详情。本文的 theme/scaffold/rows/dialog/resources 是其共享底座；SkillHub 因 route 位于 Extension 分支而允许双方交叉。

### 12.3 疑似遗漏/迁移前必须再确认

1. 旧教程 Markdown 中大量页面名称和手机控制/保活描述可能已与运行时代码漂移；这里只把它视为渲染内容，不把教程文字反推成 UI 真值。Accessibility 章节必须删除。
2. `ThinkingBlockView` 声明 pulse animator 但从未创建；不要误实现动画。若产品希望补 pulse，属于明确改进项。
3. `DialogDimensions` 注释说最小280dp，实际没有；先按 actual，之后可单独修复超窄窗。
4. `FlowLayoutView` 无 RTL；Skill detail 四 action 无 adaptive stacking；这些是可改进项，不是 1:1 默认行为。
5. `terminal-provider` zh 缺 `app_name`；若 Android library 合并时由 app 的同名 key 补齐，C++ 自有资源系统不能默认依赖该合并副作用。
6. Windows 明确不显示 Android KeepAlive UI；对应页面/通知资源由 B 与平台文档决定，本文未把 `ic_keepalive_notification` 作为 Win 可见资产。
