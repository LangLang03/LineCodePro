# Theme, Animation, and Presentation

## Theme

`ThemeSpec` groups color, typography, shape, spacing, elevation, motion, interaction, and focus-ring schemes. Start with the built-in visual system that matches the application:

```cpp
return MaterialTheme {
  AppContent(),
};
```

The complete built-in boundaries are `FlatTheme`, `FlatDarkTheme`, `MaterialTheme`, and `MaterialDarkTheme`. Do not recreate one with `ThemeDefinition{ThemeSpec}`. That constructor establishes a new complete theme boundary but does not install the Flat or Material component-style mappings.

Use this order:

- Use a built-in theme directly when its defaults fit.
- Modify a built-in `ThemeSpec` for brand colors, typography, shapes, spacing, elevation, motion, or interaction tokens.
- Override a complete typed component style only when tokens cannot express the component-specific change.
- Define a new typed style for an app-side component only when the component owns genuinely new visual policy.
- Build a new theme system only when the application intentionally does not use Flat or Material.

`UseTheme()` reads the current `ThemeSpec`. Do not hardcode a Material visual into app components that must work under Flat or custom themes.

## Customize built-in tokens

Use `FlatLightThemeSpec`, `FlatDarkThemeSpec`, `MaterialLightThemeSpec`, or `MaterialDarkThemeSpec` as the mutable starting point. Pass the result to `FlatTheme` or `MaterialTheme`; the selected theme system then rebuilds its component styles from those tokens.

```cpp
ThemeSpec theme = MaterialLightThemeSpec();
theme.colors.primary = Color::Rgb(32, 96, 180);
theme.shapes.medium = 10.0F;

return MaterialTheme(theme, AppContent());
```

For a branded dark Material theme, customize `MaterialDarkThemeSpec()` and still pass it to `MaterialTheme`. The `MaterialDarkTheme` convenience is for the unmodified built-in dark definition. Flat follows the same pattern.

`primary_container`/`on_primary_container` and `tertiary_container`/`on_tertiary_container` are tonal surface/foreground pairs, distinct from the stronger primary and tertiary pairs. Customize both sides together to preserve contrast in light and dark themes.

## Typed style catalog

Component styles are typed Environment values. Verify their current fields in the active SDK headers before constructing a replacement.

| Area | Typed styles | Public header |
| --- | --- | --- |
| Text and controls | `TextStyle`, `ButtonStyle`, `IconButtonStyle`, `ChipStyle`, `DividerStyle`, `SegmentedButtonStyle`, `TabsStyle`, `SelectStyle`, `TextFieldStyle`, `ComboBoxStyle`, `CheckboxStyle`, `RadioButtonStyle`, `SwitchStyle`, `ProgressCircleStyle`, `ProgressBarStyle`, `SliderStyle` | `text.h`, `theme.h` |
| Date and time | `DatePickerStyle`, `TimePickerStyle` | `theme.h` |
| Scrolling | `ScrollBarStyle` | `modifier.h` |
| Navigation | `NavigationStyle`, `TopAppBarStyle`, `NavigationBarStyle`, `NavigationPaneStyle`, `DrawerStyle` | `navigation.h` |
| Presentation | `ToastStyle`, `TooltipStyle`, `DialogStyle`, `BottomSheetStyle`, `MenuStyle` | `presentation.h` |
| System bars | `SystemBarsAppearance` | `window.h` |

These values own component-specific geometry, color, typography, indication, disabled state, and motion as appropriate. Prefer them to copying a built-in component or hardcoding a parallel visual implementation.

`TextFieldVariantStyle` is nested configuration inside `TextFieldStyle`; it is not a separate Theme override key.

Flat pickers use compact outlined geometry and rounded-square date selections; Material pickers use larger tonal surfaces and circular date selections. TimePicker distinguishes active-field, AM/PM-period, and dial selection colors. Preserve these separate roles when overriding its style instead of applying one selection background everywhere.

## Override component styles

`ThemeDefinition::Set(value)` replaces the complete value for that type; it is not a field-level patch. Initialize every field whose behavior matters. A style's `Default()` is the generic fallback baseline, not a copy of the current Flat or Material style, so using it and changing one field does not preserve the rest of that built-in style.

For an application-wide override, start from the selected built-in definition so all other Flat or Material values remain installed:

```cpp
ThemeSpec tokens = MaterialLightThemeSpec();
ThemeDefinition definition = MaterialThemeDefinition(tokens);
definition.Set(DividerStyle{
    .color = tokens.colors.outline,
    .thickness = 2.0F,
});

return Theme(definition, AppContent());
```

Use `FlatThemeDefinition(tokens)` for the equivalent Flat boundary. Do not use `ThemeDefinition{tokens}` as a shortcut for either built-in system.

For a local subtree override, use an empty definition inside an existing theme. It contributes only the typed values you set and inherits the outer `ThemeSpec` and all unspecified styles:

```cpp
ThemeDefinition overrides;
overrides.Set(DividerStyle{
    .color = Color::Rgb(180, 180, 180),
    .thickness = 1.0F,
});

return Theme(overrides, SettingsContent());
```

Do not put a bare `ThemeDefinition{}` at the application root merely to choose colors. Use a built-in theme and tokens instead. A bare definition is appropriate for local typed overrides or intentionally app-defined style values that inherit an outer theme.

## Indication

`Indication` can define focus, hover, press, and ripple behavior. Each `IndicationLayer` can carry a `VisualFill`, border, corner radii, placement, and enter/exit animation. Ripple is color-based and can paint behind or above content. `IndicationGeometry` controls optional layer size and clip radii.

Use component defaults unless a custom interaction treatment is part of the app design. Keep focus visible, do not rely on hover alone, and preserve disabled behavior.

## Animation

Animation specs are `SnapSpec`, `TweenSpec`, `SpringSpec`, or `KeyframeSpec`. `AnimationPlayback` controls delay, iteration count, and restart/reverse behavior.

Use `AnimateTo` with `Opacity`, `Offset`, `Scale`, `Rotation`, or `Transition` for declarative presentation animation. These modifiers do not replace layout measurement. Use `MotionController` inside retained behavior when a custom progression must advance from `FrameInfo`.

Honor `FrameInfo::reduced_motion` or the resolved environment motion policy. Do not drive animation through state writes on every frame.

`UseSceneTransition()` provides fade and circular-reveal transitions around an application mutation. Attach its anchor modifier and use `Run` when a circular reveal belongs to stable View geometry, or use `RunAt` with an explicit window-logical point.
Inside a synchronous Click, component event, Menu action, keyboard activation, or accessibility activation, `RunFromCurrentInteraction` uses the exact pointer position or the activated View center without changing semantic event signatures:

```cpp
Button("Next").OnClick([transition, page] {
  transition.RunFromCurrentInteraction(CircularRevealSceneTransition{}, [page] { page += 1; });
});
```

The implicit origin ends when the interaction callback returns and the method throws `std::logic_error` outside that scope.
Do not store a global last-pointer position, manually record raw Pointer Down, or add Point to OnClick/OnChanged solely to start a transition; asynchronous work retains geometry and calls `RunAt` explicitly.
Reduced motion is handled by the resolved handle; still keep the mutation correct without the visual effect.

## Presentation services

- `UseToast()` shows timed messages.
- `UseDialog()` shows standard or custom modal content and returns a layer ID.
- `UseBottomSheet()` shows bottom-attached modal content.
- `UsePopup()` anchors arbitrary content to a node or point.
- `UseMenu()` anchors menu entries with enabled, checked, nested, icon, and icon-tint state.
- `Tooltip` is a modifier for hover/long-press help.

Layer content is outside the ordinary application subtree but captures its environment. Keep modal dismissal, focus trapping, and owner state explicit. Do not retain a presentation context after its layer is dismissed.
Set `PopupOptions::retain_anchor_focus` only when pointer interaction with non-focusable popup content must keep an editor or other keyboard session on its mounted anchor; focusable popup descendants still receive focus.

With `popup.Anchor()` attached to a View, `Show` follows that View's full bounds, while `ShowAtAnchor(local_bounds, ...)` follows a node-local rectangle through layout, scrolling, and presentation transforms. Use `UpdateAnchor(id, local_bounds)` to move the latter without replacing its layer or content; false means a stale id or another anchor mode. `ShowAt(window_point, ...)` remains a fixed window-DIP position. Choose the anchor mode directly instead of manually tracking transforms or dismissing and recreating a popup to reposition it.

Prefer theme style overrides (`ToastStyle`, `DialogStyle`, `BottomSheetStyle`, `MenuStyle`, `TooltipStyle`) over copying built-in presentation implementations.
