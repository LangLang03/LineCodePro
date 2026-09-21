# Resources, Clipboard, Files, Network, and Async Work

## Resources

Generated applications use this resource root:

```text
resources/
  images/
    logo.png
    logo@2x.png
    mark.svg
  raw/
    config.json
  strings/
    default.properties
    zh.properties
```

The CLI initially creates empty `images` and `raw` directories and writes `strings/default.properties`. Do not add another namespace directory below `resources`; the generated application CMake registers `RESOURCES resources` with `RESOURCE_NAMESPACE app`. The build generates typed identifiers in `<app_resources.h>` and packages framework and application resources together.

- `StringResource` and `StringVariant` support localized strings and formatted values.
- `ImageResource`, `ImageVariant`, and `ImageAsset` separate declarative identity from resolved image data.
- `RawResource` addresses packaged raw data.
- `ResourceConfiguration` and locale/DPI changes are platform-owned; use public resource APIs rather than constructing package paths.

Use generated identifiers such as `app::images::logo`, `app::raw::config_json`, and `app::strings::welcome`. Raw identifiers include the sanitized filename extension, while raster density variants such as `logo.png` and `logo@2x.png` share one image identifier. `default.properties` supplies the fallback strings, while locale files such as `zh.properties` provide matching overrides.

Keep static in-UI icons, illustrations, and logos under `resources/images`. Prefer SVG for scalable vector artwork, preserve an existing supplied SVG instead of converting it to C++ drawing code, and use `currentColor` when an icon should accept the component's tint. Use raster density variants when the source is inherently raster. Platform application and system-tray icons follow their separate packaging contracts.

SVG files compile ahead of time to platform-neutral vector resources. The supported static subset includes paths and basic shapes, groups, file-local `defs`/`use`, one-path `clipPath` geometry in `userSpaceOnUse`, solid or linear/radial gradient fills and strokes, transforms, root `preserveAspectRatio`, `currentColor`, static visibility, absolute CSS lengths, and complete stroke configuration including dash arrays. Gradients support local inheritance, object-bounds or user-space coordinates, `gradientTransform`, stop and paint opacity, and pad extension. Keep references inside the same file and give every referenced element a unique ID. Browser-dependent text, scripts, external styles or references, non-finite or singular gradient transforms, repeat or reflect gradient extension, masks, filters, embedded images, animation, font-relative units, fractional group opacity, and clip unions are rejected rather than approximated. Check the active SDK documentation when consuming an older installed SDK because its compiler may support a smaller subset.

Generated libraries use the same directory categories but register their own target-derived resource namespace and generated header. Read the library's generated `CMakeLists.txt` rather than assuming the application namespace.

Pass resource values directly to components and `VisualFill` when their public overloads accept them. In particular, ordinary `Image` and icon properties should receive the generated `ImageResource` instead of resolving an SVG first. Use `UseString(...)`, `UseImage(...)`, `UseVectorImage(...)`, or `UseRawResource(...)` only when application code needs the resolved value itself. These reads are composition-bound and therefore belong in a composable function.

Keep resource identifiers and namespace consistent with generated CMake. Do not open `resources.bin` directly.

`UseRawResource(app::raw::config_json)` resolves a lazy `RawAsset` without reading its payload. Capture the asset into an event or task, then choose `ReadBytes(cache = false)` or `ReadString(cache = false)` for complete content, or `OpenRead()` / `OpenReadAsync()` for an independent incremental cursor. A true cache argument retains successfully read complete bytes across asset copies; false still reuses an existing cache and does not evict it. Avoid reading large complete payloads during composition; use asynchronous streams or run a blocking complete read with `RunWorker()`.

Uncached raw access needs the Runtime resource service to remain connected; retained cached bytes and already opened streams have independent lifetimes. Raw asset open and complete-read failures use resource exceptions, while operations on the opened stream return `IoResult`. Incremental reads do not fill the complete-content cache.

### Locale and text shaping

Text, built-in labels, TextField, and Canvas text inherit the effective `Locale` when their shaping locale is empty. Use `.Shaping(TextShapingOptions{...})` on Text or TextField for a local direction or locale override; it does not change resource lookup. A `TextMeasurer` does not look up Environment implicitly, so pass the effective locale explicitly when measuring custom text. Picker labels, week starts, and 12/24-hour presentation also follow Locale rather than application-authored locale tables.

## Clipboard

Obtain the current Runtime's plain-text clipboard with `UseApplication().Clipboard()` during composition, then capture the shared handle into a UI-thread event handler. A captured `ApplicationHandle` can also return the same clipboard outside composition. `IsAvailable()` reports whether the Runtime currently has synchronous clipboard access. `ReadText()` returns an optional UTF-8 string, and `WriteText(text)` reports whether valid UTF-8 text was accepted.

Clipboard operations are synchronous and may enter native APIs. Do not call them from `RunWorker()` or another application worker thread. A captured handle remains safe after its Runtime is destroyed but reports unavailable results. Web does not expose this application clipboard service; browser-managed TextField copy, cut, and paste continue through trusted editing events.

## Files and directories

Obtain the runtime-installed services during composition with `UseService<FilePicker>()` and `UseService<HttpClient>()`. These return shared service handles; their public constructors are not application construction APIs. Store the required handle before launching a lifetime-bound task, then capture that handle into the task rather than looking up a composition service after suspension.

`Bytes` from `<huxerui/data.h>` is the canonical owned binary buffer. Use `std::span<const std::byte>` only for borrowed binary input instead of introducing another application byte-container type.

`Uri` from `<huxerui/data.h>` is the immutable absolute RFC 3986 URI value. Use the throwing constructor for trusted caller input and `Uri::Parse()` for external text. Read `Scheme()` and `Path()` directly; `Authority()`, `Query()`, and `Fragment()` are optional so absent and present-empty components stay distinct. Serialization and equality are lexical, raw Unicode IRIs are unsupported, and `Uri` does not own route, query-map, normalization, or HTTP policy.

`File` represents an application-visible path and offers synchronous and asynchronous stat, read, write, append, list, create, delete, copy, and move operations. Byte reads return `IoResult<Bytes>`; synchronous byte writes borrow a span for the call, while asynchronous byte writes take `Bytes` by value so storage remains owned across suspension. `IoResult<T>` aliases `Result<T, IoError>`; handle `IoErrorCode` and check success before accessing a value. Existing boolean-returning operations still report success as `bool`; do not assume every platform permits every path.

Use `File(const Uri&)` and `File::ToUri()` only for supported local `file:` URI conversion. `FileReference` retains access to an external file or directory; do not reconstruct a path from its display name, an Android content URI, or a browser handle.

`UseApplication().Directories()` provides application data, cache, temporary, and optional executable directories. Use those instead of hardcoded OS paths. The getter returns captured AppDirectories values without I/O; copy File paths into tasks. A retained ApplicationHandle can query directories outside composition and after Runtime destruction, while a host without directory support reports std::logic_error. `ApplicationHandle::CurrentDirectory()` instead queries the shared process working directory on every call; it is not an application storage root.

Destructive file operations require the user's intended scope. Prefer non-recursive operations unless recursive deletion is explicitly needed and the exact target is validated.

### Streams

`<huxerui/stream.h>` provides move-only `InputStream`, `OutputStream`, `AsyncInputStream`, and `AsyncOutputStream`, together with `IoError` and `IoResult`. The generic `Result<T, ErrorT>` lives in `<huxerui/data.h>`. Synchronous `Read()` fills a caller-sized writable span and returns a byte count; `Write()` borrows a byte span for the call. Asynchronous `ReadAsync(maximum_bytes)` returns owned `Bytes` up to the caller's limit, and `WriteAsync(Bytes)` owns its input across suspension. A successful zero-byte read marks EOF, not an error.

Use `File::OpenRead()` / `OpenWrite()` for synchronous streams or their `Async` counterparts for asynchronous streams; each open returns the corresponding `IoResult`. `FileReference` exposes asynchronous opens so provider grants and platform coordination stay attached to the stream. Prefer these APIs for large transfers; `ReadBytes` and `ReadString` intentionally materialize complete content. Web local `File` streams operate on the virtual filesystem, whereas browser file references use browser readers and writable handles.

`CopyTo()` and `CopyToAsync()` are input-stream members and do not close the destination. Explicitly finish output with `Close()` or `CloseAsync()` and check its result, including Web persistence failures. Retain stream handles throughout pending operations and allow only one pending operation per endpoint. Cancellation can leave partial output; it is not rollback. Canceling an HTTP body copy also stops its transport while the copy waits for destination output.

### References and local paths

`FileReference::AsFile()` returns an optional local `File` without I/O or import. Path-backed selections support it; Android document URIs and browser handles do not. The result is a stored path, not a fresh permission check or an identity that follows renaming.

The returned `File` does not retain the reference's grant or temporary-file owner, enforce `CanWrite()`, or inherit document coordination. Keep a reference alive throughout path-based use when access depends on those resources; use reference I/O when its grant restrictions or coordination matter. On Windows, ordinary local-path use may outlive the reference, but `AsFile()` does not release native handles: all sharing references and pending operations must release them before their rename restrictions end.

`Type()`, `Size()`, `ContentType()`, and `CanWrite()` are captured metadata, not refreshed storage state. `ListChildrenAsync()` lists direct directory children, whose retained access survives destruction of the parent value without upgrading its grant. Directory references cannot use single-file read, import, or replacement operations.

## File picker

`OpenFileAsync` and `OpenFilesAsync` return `FileReference` values rather than unrestricted paths. Use their read/import/replace operations and preserve the capability boundary on sandboxed and Web platforms. `SaveFileAsync` instead receives an application `File` plus `SaveFileOptions` and reports success as `bool`. Check `CanOpenFiles` and `CanSaveFiles` before presenting unavailable actions.

Use `CanOpenDirectories(writable)` and `OpenDirectoryAsync(writable = false)` to select an existing directory without file filters. The default grant is read-only through reference operations; `true` requests writable access without silently downgrading it. Directory `CanWrite()` describes child creation, not permission to overwrite every child. Picker empty/false results cover cancellation and unsuccessful presentation; filters are platform hints, not content validation.

Web directory selection requires a live browser directory capability. Start each picker from a user action without preceding asynchronous work consuming transient activation; an uploaded directory listing is not a replacement grant.

### Directory copying

Use `CreateDirectoryAsync(name)` and `CopyFileFromAsync(source, name, overwrite)` for named children of a writable directory reference. Names are single path segments, not relative paths.

`CopyDirectoryContentsToAsync(destination, overwrite = false)` copies into an existing local `File` directory or writable directory reference. It copies the contents, not an extra source-root folder; existing directories merge and destination-only entries remain. Existing files require explicit overwrite. Inspect the `IoResult<DirectoryCopySummary>`: failure or cancellation may leave completed output, with no tree transaction or rollback. Links, cycles, overlapping roots, and relationships that cannot be established safely are rejected; do not assume snapshot isolation or atomic exclusion of concurrent provider writes.

For external file reception, use the separate [file-drop contract](gestures-and-drag-drop.md#external-file-reception), then choose which reference operations the application needs.

## HTTP

Create requests with `HttpRequest`, `HttpMethod`, and headers during the owning application flow. `HttpRequest::body` and `HttpResponse::body` are owned `Bytes`; HuxerUI does not implicitly encode request text or decode response bytes.

Use `HttpClient::SendAsync()` for a buffered `HttpResult<HttpResponse>`, or `SendStreamAsync()` for a `HttpResult<HttpResponseStream>` whose body is consumed incrementally. `HttpResult<T>` aliases `Result<T, HttpError>`; check `Succeeded()` before accessing `Value()` or `Error()`. Read the move-only response's `Body()` with `ReadAsync(maximum_bytes)` until a successful empty `Bytes` reports EOF. Only one body read or copy may be pending. An error before response headers is carried by `HttpResult<HttpResponseStream>`, while a later body error is carried by the operation's `IoResult`.

Both request forms accept an optional progress callback receiving `HttpProgress`; the callback runs on the owning Runtime UI thread. Upload and download totals are optional, and HTTP status codes remain responses rather than transport errors. Keep retry, decoding, persistence, and user-visible error policy in application code.

For downloads, check the response status according to application policy, copy `Body()` into an asynchronous output stream, and close the output explicitly after a successful copy. Request uploads still take complete `Bytes`; the response-stream API does not imply streaming request bodies.

## Task lifetime

Launch app-side async flows from `UseTaskScope()` so unmount cancels work. Capture the required service and `State` values before launching the task rather than calling composition-bound facilities after suspension. Avoid detached platform callbacks that capture UI objects indefinitely.

Call File asynchronous methods, `HttpClient::SendAsync()`, `HttpClient::SendStreamAsync()`, and the response body's `ReadAsync()` directly from the owning Task. File Async already owns its platform-appropriate worker or event completion path, while HTTP keeps its platform asynchronous transport; do not wrap these APIs in `RunWorker()`.

After `co_await` on these operations, the continuation is already back on the owning Runtime UI thread and may update captured `State` directly while the task scope remains alive. Do not use `TaskScope::Post()` for that continuation; `Post()` is only for an external thread or callback outside a running HuxerUI Task.

Use `RunWorker()` only for application-owned synchronous CPU-bound or blocking work. Use `TaskScope::Post()` to hand an external callback back to the UI scope. These APIs do not keep a mobile application running in the background; platform background tasks are a separate capability.

The active SDK's services are installed by the application runtime. If a service is unavailable on a platform, report that public capability limit rather than reaching into a private adapter.
