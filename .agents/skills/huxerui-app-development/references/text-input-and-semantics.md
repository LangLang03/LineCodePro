# Text Input and Semantics

## Controlled editing

Store the complete `TextEditingValue`, not only `text`. It carries `TextSelection` and optional composition. Text offsets and platform input units are not interchangeable; let HuxerUI's text-input contract translate UTF-8, UTF-16, scalar, and grapheme boundaries.

```cpp
auto value = UseState(TextEditingValue::FromText(""));

return TextField(value)
    .Label("Email")
    .InputConfiguration({.type = TextInputType::Email, .action = TextInputAction::Next})
    .OnChanged([value](TextEditingValue next) {
      value = std::move(next);
    });
```

`TextField` is controlled: the callback requests the next value and the owner supplies it on recomposition. Use `TextFieldLineLimits` for single/multiline behavior, `MaxLength` for the component contract, and `ValidationResult` for application-owned validation state. `Required` and `EmailAddress` are reusable rules, and `Validate(value, rules...)` stops at the first invalid result. Validation reports domain state; it is not an edit filter.

`Secure()` configures secure entry. Avoid logging, retaining, or echoing secure editing values outside the necessary owner.

`TrailingIcon(icon)` is decorative. Use `TrailingIcon(icon, semantic_label)` with `OnTrailingIconClick(...)` when the icon is an independent action such as revealing a password, and keep its accessible label synchronized with the current action. This does not change the complete controlled editing value or make ComboBox's decorative dropdown icon interactive.

## Input configuration

Choose `TextInputType`, capitalization, action, multiline, secure, autocorrect, and read-only behavior through `TextInputConfiguration`. Handle `.OnSubmitted` for semantic completion, not every raw key.

TextField's `.Shaping(TextShapingOptions)` controls direction and locale separately from input configuration and resource lookup; see [locale and text shaping](resources-files-network.md#locale-and-text-shaping).

## Semantics

Use the public `Semantics` modifier and typed semantic actions. Supply accessible labels for icon-only controls and meaningful roles/state for custom controls. Built-in controls already provide their normal semantics; add metadata only when application meaning is missing.

For collections, expose stable collection/item metadata when building a custom virtual layout. Disabled, checked, selected, expanded, value, range, and text-editing semantics should match the controlled UI value.

Custom `NodeExtension` semantics call `InvalidateSemantics()` after retained semantic state changes and handle only the local actions they declare.

In `BuildSemantics`, use `SemanticBuilder::AddChild(local_id, local_bounds, semantics, enabled, parent_local_id)` for virtual parts of a self-drawn control. IDs are stable, nonzero, and unique within the extension; bounds are owner-local DIPs. Zero selects the mounted owner as the parent, and a nonzero parent must already have been declared. Add standard or custom actions with `AddAction` or `AddCustomAction`.

Use `SetActiveChild(local_id)` when one virtual child represents the focused part of the mounted owner; zero clears it. Use `AdoptChild(local_id, child_index)` when an already mounted direct child owns interactive semantics that belong beneath a virtual logical item. Do not duplicate that child's actions or invent hidden Views solely to create an accessible hierarchy.

Child availability is combined with the mounted owner's Enabled state and every virtual ancestor. Disabled children remain discoverable but expose no executable actions. Reuse the real input availability predicate and invalidate semantics when it changes; semantic declarations do not disable pointer or keyboard handlers themselves.

Windows, macOS, Android, and iOS map the shared semantic tree to their platform accessibility systems. Linux and Web do not provide mappings for HuxerUI-rendered content and those bridges are not planned. Native DOM PlatformViews on Web retain their browser-provided accessibility independently.

## Review checklist

- full editing value preserved;
- focus and IME action make sense for the field order;
- validation message is perceivable and linked to the field meaning;
- icon-only actions have labels;
- keyboard and semantic activation match pointer activation;
- selection, clipboard, secure data, and read-only behavior are not conflated.
