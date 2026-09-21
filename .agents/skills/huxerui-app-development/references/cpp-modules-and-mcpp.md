# C++20/23 Modules and mcpp

Use this reference when the project is an **mcpp** project — created by
`huxerui create app <name> --build mcpp`, or by
`mcpp new <name> --template huxerui.huxerui`. The public API is the same one
the CMake path uses; what differs is how the project is described, built and
consumed.

What it feels like to work in: `import huxerui;` in place of the umbrella
header, `import std;` in place of the standard ones, and **no `#include`
anywhere in the project**. A page is a module unit; units reach each other by
`import`, so there is no header to keep in step with a source and no list of
sources in the manifest. `[[huxerui::composable]]` means exactly what it means
under CMake.

Recognise it by `mcpp.toml` beside a `build.mcpp`, or by `import huxerui;` in a
source. Such a project has no `CMakeLists.txt` and no `platform/` shell, and
`huxerui build` / `run` / `package` do not drive it — `mcpp build` and
`mcpp run` do.

## What the project looks like

```
mcpp.toml            the package: name, standard, one dependency
build.mcpp           the build program: one call
src/main.cpp         the entry, and it instantiates nothing
src/app.cppm         a module interface unit: the page and its composables
resources/           compiled by hrc, reached as StringResource / ImageResource
```

```toml
[package]
standard = "c++23"

[dependencies]
huxerui.huxerui = "0.3.0"

[build]
sources = []
```

```cpp
import mcpp;
import huxerui.rules;

int main() { return huxerui::rules::configure({ .resources = "resources" }) ? 0 : 1; }
```

`sources = []` is deliberate. `huxerui.rules` selects the sources, runs the
composable transform over them and submits the result; a build program can
*add* a source but not replace one, so a manifest that also globbed
`src/**/*.cpp` would link both the original and the transformed copy of every
composable.

## Writing the code

```cpp
export module app;

import std;
import huxerui;

using namespace huxerui;

[[huxerui::composable]]
View Counter() {
  auto count = UseState(0);
  return Row { Button("Count").OnClick([count] { count += 1; }), Text(count) };
}
```

- **No headers.** `import huxerui;` and `#include <huxerui/huxerui.h>` name the
  same entities; an mcpp project uses the first and contains no `#include`.
- **`import std;` is load-bearing, and it is why the package pins c++23.**
  `UseState()`, `View` and `Layout` instantiate `typeid` in their caller, GCC
  checks that per translation unit, and a global module fragment's includes do
  not reach an importer — so `import huxerui;` cannot supply `<typeinfo>`.
  The c++23 pin is a workaround, not a requirement: every implementation this
  project builds with offers the std module at C++20, and mcpp's
  clang-on-Windows path hardcodes a c++23 floor instead of probing the STL it
  found ([mcpp#603](https://github.com/mcpp-community/mcpp/issues/603)).
- **Keep the entry empty.** `src/main.cpp` calls `RunApplication()` and nothing
  else, so it instantiates nothing and needs no imports beyond `huxerui` and
  the app module. It is also the one file `huxerui.rules` never transforms.
- **`[[huxerui::composable]]` means exactly what it means under CMake.** The
  same `hcg` transforms it.
- **Macros do not cross a module boundary.** `HUXERUI_SCOPE(...)` is not
  reachable through `import`. It is not needed either: it expands to
  `return Scope([=]() -> View { … });`, and `Scope` and `View` are exported.

## Building

```bash
mcpp build            # the whole graph, including HuxerUI from source
mcpp run              # build, then run the bin target
mcpp test             # every tests/**/*.cpp, each its own program
mcpp build --workspace
```

A toolchain is selected once rather than per command — `mcpp test` and
`mcpp pack` take no `--toolchain` flag:

```bash
mcpp toolchain install llvm 22.1.8
mcpp toolchain default llvm@22.1.8
```

## Reading further

The vocabulary in `mcpp.toml` and `build.mcpp` is mcpp's, not HuxerUI's. When a
key or directive is in question, the upstream documentation is the authority:

- Repository: <https://github.com/mcpp-community/mcpp>
- Documentation index: <https://github.com/mcpp-community/mcpp/tree/main/docs>
- [`04-mcpp-toml.md`](https://github.com/mcpp-community/mcpp/blob/main/docs/04-mcpp-toml.md) — every manifest key
- [`05-dependencies.md`](https://github.com/mcpp-community/mcpp/blob/main/docs/05-dependencies.md) — package selectors, path/git/index dependencies
- [`08-testing.md`](https://github.com/mcpp-community/mcpp/blob/main/docs/08-testing.md) — what `mcpp test` considers a test
- [`30-build-mcpp.md`](https://github.com/mcpp-community/mcpp/blob/main/docs/30-build-mcpp.md) — build programs and their directives
- [`31-authoring-a-rule-package.md`](https://github.com/mcpp-community/mcpp/blob/main/docs/31-authoring-a-rule-package.md) — how `huxerui.rules` is built
- [`23-the-project-environment.md`](https://github.com/mcpp-community/mcpp/blob/main/docs/23-the-project-environment.md) — `[xlings.workspace]` and payloads
- xlings, which provisions the toolchains and payloads: <https://github.com/d2learn/xlings>

On the HuxerUI side, [`docs/design/mcpp-build-system.md`](../../../docs/design/mcpp-build-system.md)
is the design, [`mcpp/examples/`](../../../mcpp/examples) holds three worked
applications, and [`modules/README.md`](../../../modules/README.md) covers what
the module guarantees against the headers.

Do not infer mcpp behaviour from CMake behaviour, or from this file when the
installed mcpp disagrees with it: run `mcpp --version` and read that version's
documentation.
