# HuxerUI UI DSL Style

Load this reference before writing or editing any HuxerUI UI declaration, example, test UI, README snippet, or design snippet.

## Rules

- Put one space between a braced container name and `{`: `Column {`, `Row {`, `Stack {`, `DrawerLayout {`.
- Indent children by two spaces.
- Start a fluent chain on the same line as the closing brace: `}.With(...)`.
- Keep trailing commas where local style permits.
- Keep a short `.With(...)` on one line. Split only when clarity or the 120-column limit requires it.
- In a multiline `.With(...)`, keep modifiers visually distinct and align the closing parenthesis with the call.
- Place component-specific fluent calls vertically when combining them would obscure meaning.
- Close structural nesting one level at a time; do not leave endings such as `}}})))`.
- Use braces for containers and parentheses for leaf components and ordinary function calls.
- Distinguish `Column { ... }` from aggregate initialization such as `Frame{.width = 320.0F}`.
- Keep function signatures and arguments on one line when they remain clear within 120 columns.
- Write `Type* value` and `Type& value`.
- Do not add wrappers, lambdas, temporaries, or component layers merely to hide formatting.
- Chain `.With`, `.On`, component fluent methods, and `.Key` directly on temporary Views. Pass named Views into containers and return local Views by value; do not add routine `std::move` calls to either case.
- Format only the touched UI block. Treat `.clang-format` as a baseline and manually restore readable DSL structure afterward.

## Preferred forms

```cpp
return Column {
  Text("Account", TextRole::Title),
  Row {
    Button("Cancel"),
    Button("Save"),
  }.With(Spacing(8.0F)),
}.With(
    Padding(16.0F),
    CrossAlign(CrossAxisAlignment::Stretch)
);
```

```cpp
return Row {
  Text("Status"),
  Text("Ready"),
}.With(Spacing(8.0F));
```

Avoid redundant View moves:

```cpp
View status = StatusContent();
return Row {
  status,
  Button("Refresh").OnClick(refresh),
};
```

Do not write `std::move(Button("Refresh"))`, move `status` merely to add it to the container, or return a named View through `std::move`.
