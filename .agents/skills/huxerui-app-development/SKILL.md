---
name: huxerui-app-development
description: "Develop HuxerUI SDK apps: create/open projects, build UI and app-side components, configure CMake/CLI, review, and troubleshoot. Use for find_package(HuxerUI), HuxerUI::huxerui, or public huxerui headers; not framework internals or generic C++."
---

# HuxerUI App Development

Use the installed HuxerUI SDK to create, modify, review, or troubleshoot applications and app-side reusable components. Do not use this skill to change HuxerUI framework internals, renderers, platform adapters, SDK packaging, or the framework's own build.

## Recognize the project

Treat the task as HuxerUI SDK application work when the user names HuxerUI or its CLI, or when the project contains one of these signals:

- `find_package(HuxerUI CONFIG REQUIRED)`
- `HuxerUI::huxerui` or `HuxerUI::huxerui_static`
- an include under `<huxerui/...>`

Opening a matching project does not authorize changes or commands by itself.

## Establish the SDK truth

Before relying on an API, identify the SDK used by the project:

1. Read `HuxerUI_DIR` from the active build directory's `CMakeCache.txt`, then derive the SDK prefix from `lib/cmake/HuxerUI`.
2. Otherwise use `HUXERUI_HOME` when it identifies an installed SDK.
3. Otherwise locate `huxerui` on `PATH` and run `huxerui doctor`.
4. If none is available, ask for the SDK location before relying on exact API or build behavior.

Prefer that SDK's `include/huxerui` and `lib/cmake/HuxerUI` over this Skill's guidance. Do not scan for a source checkout or read its `src`, tests, examples, or design documents to fill gaps. Follow the installed public contract without compatibility aliases.

## Work from public contracts

- Components are ordinary C++ functions returning `View`.
- `View` is a lightweight copy-on-write declaration value. Pass and return it by value; do not add `std::move` to temporary chains, container children, or return statements as a routine optimization.
- Mark a reusable function `[[huxerui::composable]]` when it directly calls a composition-bound `UseXxx()` API or needs its own recomposition lifetime. Do not annotate the application root.
- Keep controlled values authoritative in app state and write emitted changes back for the next composition.
- Use stable semantic keys for dynamic stateful siblings that insert, remove, or reorder.
- Preserve complete `TextEditingValue` state for `TextField`.
- Use only public headers, public CMake targets, generated project conventions, and platform-specific public headers.
- Compose built-in layouts and their modifiers before deriving `Layout` or `VirtualLayout`. A custom layout is for a genuinely new measurement or placement algorithm, not ordinary alignment, spacing, growth, wrapping, overlay, paging, scrolling, or responsiveness.
- Start application visuals from `FlatTheme`, `FlatDarkTheme`, `MaterialTheme`, or `MaterialDarkTheme`. Customize their tokens or typed styles instead of recreating a built-in theme with a bare `ThemeDefinition`.
- Choose the narrowest extension mechanism after exhausting its built-in equivalent. Prefer an ordinary component, existing event or modifier, Environment/Theme value, presentation service, or root service. Use a custom `Layout`, retained modifier with `NodeExtension`, `PlatformModule`, `PlatformView`, or `ExternalTexture` only when its distinct geometry, mounted lifetime, or platform boundary is actually required.
- Do not invent APIs, legacy names, private includes, or source-checkout paths.

## Route the task

Read only the references needed for the request:

- Creating, opening, configuring, building, running, or diagnosing a project: [project-workflow.md](references/project-workflow.md)
- Writing or editing any UI declaration or snippet: [dsl-style.md](references/dsl-style.md)
- Selecting and configuring built-in controls, date/time pickers, or their controlled values: [components.md](references/components.md)
- Arranging UI, scrolling, virtualization, responsiveness, app shells, mounted coordinate conversion, or any proposed custom layout: [layout-and-ui.md](references/layout-and-ui.md)
- Composition, state, keyboard routing and shortcuts, events, environment, lifecycle, and tasks: [fundamentals.md](references/fundamentals.md)
- `.With(...)`, events, keys, and modifier ownership: [modifiers.md](references/modifiers.md)
- Pointer buttons, hover, pointer cursors, context-menu requests, raw pointer input, pointer interception, repeated tap, long press, drag, multi-pointer transform, typed in-process drag-and-drop, and external file reception: [gestures-and-drag-drop.md](references/gestures-and-drag-drop.md)
- Custom retained behavior, after confirming that events, gestures, animation, lifecycle, or other built-ins do not fit: [node-extensions.md](references/node-extensions.md)
- Built-in themes, token or component-style customization, any proposed `ThemeDefinition`, indication, animation, layers, dialogs, menus, and scene transitions: [theme-animation-presentation.md](references/theme-animation-presentation.md)
- Text input, validation, selection, and accessibility: [text-input-and-semantics.md](references/text-input-and-semantics.md)
- Resources, localization and shaping, file/directory references, path access, pickers, copying, HTTP, and async work: [resources-files-network.md](references/resources-files-network.md)
- Navigation, browser routes, safe areas, window chrome, and system tray behavior: [navigation-and-window.md](references/navigation-and-window.md)
- Canvas, paint, images, vectors, external textures, and GPU frame publication: [canvas-paint-and-images.md](references/canvas-paint-and-images.md)
- Non-visual platform services: [platform-modules.md](references/platform-modules.md)
- Embedded platform controls: [platform-views.md](references/platform-views.md)
- Fast public API and header lookup: [api-index.md](references/api-index.md)

## Apply changes safely

Inspect the project's existing CMake target, entry point, theme, state ownership, component patterns, and platform configuration before editing. Preserve its generated structure and current generator/compiler. Do not add platforms, compatibility layers, or a new architectural abstraction unless requested.

After editing, check the items applicable to the current change:

- layout under bounded, unbounded, narrow, and expanded constraints relevant to the task;
- controlled values, stable keys, focus, keyboard, hover, pressed, disabled, and semantics;
- reduced-motion behavior for animation;
- platform-specific public contracts and unavailable-platform limits;
- HuxerUI DSL formatting from `dsl-style.md`;
- build and run results only for the current host and affected platforms that are actually available.

When an API is uncertain, inspect the active SDK header, CMake package, or CLI help instead of guessing.
