# Public API Index

This is a navigation index, not a replacement for the active SDK's public headers.

| Area | Primary public names | Main reference | Authority |
| --- | --- | --- | --- |
| App and lifecycle | `Application`, `AppOptions`, `RunApplication`, `UseApplication`, `ApplicationHandle`, `ApplicationActivation`, `ApplicationLifecycleState`, `Lifecycle` | [fundamentals.md](fundamentals.md) | `app.h`, `lifecycle.h` |
| Permissions | `Permission`, `PermissionStatus`, `ApplicationHandle::CheckPermissionAsync`, `ApplicationHandle::RequestPermissionAsync`, `ApplicationHandle::OpenPermissionSettingsAsync` | [fundamentals.md](fundamentals.md) | `app.h` |
| System tray | `SystemTrayHandle`, `SystemTrayOptions`, `MenuEntry`, `ApplicationHandle::SystemTray`, `ApplicationHandle::Quit` | [navigation-and-window.md](navigation-and-window.md) | `app.h`, `presentation.h` |
| Tasks | `Task`, `TaskScope`, `UseTaskScope`, `Delay`, `RunWorker`, `WorkerSequence`, `TaskScope::Post` | [fundamentals.md](fundamentals.md) | `task.h` |
| View and composition | `View`, `Scope`, `[[huxerui::composable]]`, `ForEach` | [fundamentals.md](fundamentals.md) | `view.h` |
| State | `State`, `StateList`, `UseState`, `UseStateList` | [fundamentals.md](fundamentals.md) | `state.h` |
| Environment | `Environment`, `ProvideEnvironment`, `UseEnvironment`, `UseViewportClass` | [fundamentals.md](fundamentals.md) | `environment.h` |
| Events | `Event<Result(Arguments...)>`, `On`, `OnClick`, `UseEvents`, `EventEmitter::Emit` | [fundamentals.md](fundamentals.md) | `event.h` |
| Containers | `Column`, `Row`, `Flow`, `Stack`, `IndexedPages`, `Spacer` | [layout-and-ui.md](layout-and-ui.md) | `view.h` |
| Controls | `Button`, `IconButton`, toggles, `Select`, `ComboBox`, `TextField`, tabs, progress, slider | [components.md](components.md) | `view.h` |
| Date and time | `DatePicker`, `TimePicker`, `DatePickerStyle`, `TimePickerStyle`, chrono controlled values | [components.md](components.md#datepicker-and-timepicker) | `view.h`, `theme.h`, `event.h` |
| Layout | Built-in layout selection; custom `Layout`, `LayoutContext`, `LayoutResult`, `ViewNode` only when needed | [layout-and-ui.md](layout-and-ui.md) | `view.h`, `layout.h` |
| Mounted geometry | `Bounds`, `ContentBounds`, `LocalToWindow`, `WindowToLocal`, `LocalToWindowBounds` | [layout-and-ui.md](layout-and-ui.md#mounted-coordinate-spaces) | `layout.h` |
| Virtual layout | `VirtualLayout`, `VirtualLayoutContext`, `VirtualList`, `VirtualGrid` | [layout-and-ui.md](layout-and-ui.md) | `virtual_layout.h`, `view.h` |
| Scrolling | `ScrollView`, `ScrollController`, `UseScrollController`, `ScrollPhysics`, `ScrollBar` | [layout-and-ui.md](layout-and-ui.md) | `scroll.h`, `modifier.h`, `view.h` |
| Gestures | `PointerButton`, `PointerEvent`, `HoverEvent`, `PointerCursor`, `PointerCursorKind`, raw `ViewEvents::Pointer`, `ContextMenuRequested`, `PointerIntercept`, multi-tap, long press, drag, transform, typed drag-and-drop | [gestures-and-drag-drop.md](gestures-and-drag-drop.md) | `event.h`, `gesture.h`, `geometry.h`, `modifier.h` |
| External file reception | `FileDropTarget`, `FileDropOptions`, `FileDropOffer`, `FileDropEvent`, `FileDropEvents` | [gestures-and-drag-drop.md](gestures-and-drag-drop.md#external-file-reception) | `file_drop.h`, `file.h` |
| Modifiers | `ViewModifier`, built-in property modifiers, retained modifiers | [modifiers.md](modifiers.md) | `modifier.h` |
| Retained behavior | `NodeExtension`, `EmitEvent`, `PrepareGeometry`, `FrameInfo`, invalidation and input hooks | [node-extensions.md](node-extensions.md) | `modifier.h` |
| Theme | Flat/Material boundaries, `ThemeSpec`, typed style overrides, `ThemeDefinition` | [theme-animation-presentation.md](theme-animation-presentation.md) | `theme.h`, `navigation.h`, `presentation.h` |
| Interaction visual | `Indication`, `IndicationLayer`, `RippleEffect`, `FocusRing` | [theme-animation-presentation.md](theme-animation-presentation.md) | `indication.h` |
| Animation | specs, `MotionController`, animated modifiers, `SceneTransitionHandle`, `RunFromCurrentInteraction` | [theme-animation-presentation.md](theme-animation-presentation.md) | `animation.h` |
| Presentation | toast, dialog, bottom sheet, popup, menu, tooltip; `PopupHandle::ShowAtAnchor`, `UpdateAnchor` | [theme-animation-presentation.md](theme-animation-presentation.md) | `presentation.h`, `layer.h` |
| Text input | `TextEditingValue`, configuration, field limits, `ValidationResult`, `Validate` | [text-input-and-semantics.md](text-input-and-semantics.md) | `text_input.h`, `view.h`, `validation.h` |
| Semantics | `Semantics`, `SemanticBuilder::AddChild`, virtual-child availability, semantic roles/actions | [text-input-and-semantics.md](text-input-and-semantics.md) | `semantics.h` |
| Resources | string/image/raw resources, `UseString`, `UseImage`, `UseVectorImage`, `UseRawResource` | [resources-files-network.md](resources-files-network.md) | `resource.h` |
| Locale and shaping | `Locale`, `TextShapingOptions`, `Text::Shaping`, `TextField::Shaping` | [resources-files-network.md](resources-files-network.md#locale-and-text-shaping) | `resource.h`, `text.h`, `view.h` |
| Files and network | `Bytes`, `Uri`, `File`, `FileReference::AsFile`, `FilePicker::OpenDirectoryAsync`, `CopyDirectoryContentsToAsync`, `DirectoryCopySummary`, `FileSystem`, `HttpClient` | [resources-files-network.md](resources-files-network.md) | `data.h`, `file.h`, `http.h` |
| Navigation | `NavigationPath`, routed `NavigationStack`, controllers, browser codec | [navigation-and-window.md](navigation-and-window.md) | `navigation.h`, `web/navigation.h` |
| Window | `WindowContentMode`, `WindowChromeMode`, safe area, system bars, title bar, `UseWindow`, `WindowHandle` | [navigation-and-window.md](navigation-and-window.md) | `window.h` |
| Paint | `Color`, `Brush`, `VisualFill`, `Canvas`, `PaintContext`, `StrokeStyle`, `DrawLine`, `UseTextMeasurer`, `Path`, `Path::ArcTo`, `Path::Contains`, `ArcSize`, `ArcDirection`, gradients, images, vectors | [canvas-paint-and-images.md](canvas-paint-and-images.md) | `color.h`, `paint.h`, `text.h`, `vector.h` |
| Platform integration | host adapter, typed platform values, payloads, modules, views, events, root registration, Android Java, Apple Objective-C/Swift, and Web JavaScript factories | [platform-modules.md](platform-modules.md), [platform-views.md](platform-views.md) | `platform_adapter.h`, `platform_registry.h`, platform registry headers |
| External frames | `ExternalTexture`, `PixelTexture`, `D3D11Texture`, `BitmapTexture`, `android::GlTexture`, `linux::GlTexture`, `linux::GdkTexture`, `SurfaceStreamTexture`, `PixelBufferTexture`, `MetalTexture`, `VideoFrameTexture` | [canvas-paint-and-images.md](canvas-paint-and-images.md#external-textures) | `external_texture.h`, platform texture headers |
| CMake/CLI | package targets, `huxerui_add_app`, codegen, resources, CLI | [project-workflow.md](project-workflow.md) | installed package and CLI |
