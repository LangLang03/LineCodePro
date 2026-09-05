# Canvas, Paint, and Images

## Colors and fills

`Color` stores normalized red, green, blue, and alpha channels. Prefer `Color::Rgb(...)`, `Color::Transparent()`, `Color::Black()`, and `Color::White()` instead of mixing byte and normalized channel conventions.

`Brush` is the shared source-paint value for `Color`, `LinearGradient`, and `RadialGradient`. It intentionally excludes geometry, `StrokeStyle`, opacity, blending, filters, images, and platform drawing objects. `VisualFill` accepts a `Brush` or `ImageFill`, with direct Color and gradient construction for concise declarations. Gradient start, end, center, and radius values are normalized to the painted bounds; stops use offsets from `0.0F` to `1.0F`. Set the gradient's `transform` to rotate, scale, skew, or translate that normalized sampling space without moving the painted geometry; leave its identity default when no transform is needed. `ImageFill` adds fit, alignment, sampling, optional tint, and opacity to an `ImageVariant`. The same fill vocabulary is used by `Background` and interaction indication layers.

## Image sources

`ImageVariant` accepts the image forms exposed by the active SDK, including resource-backed and resolved assets. `Image` provides fit, alignment, sampling, and tint. Use `VectorAsset` for public vector data and `ExternalTexture` for frames produced outside HuxerUI.

## Canvas

`Canvas` receives a `PaintContext` and assigned `Size`. Give it explicit or parent-derived constraints. Draw in local coordinates and do not use Canvas to arbitrarily place `PlatformView` children.

`PaintContext` emits platform-neutral commands for rectangles, text, images, circles, lines, arcs, borders, shadows, paths, clips, and transforms. `DrawRect()`, `FillPath()`, and `StrokePath()` accept a `Brush`, so Color and gradient sources use the same geometry API. Balance every pushed clip or transform with a pop on every path. Call only public drawing methods; `PaintCommand`, `RenderScene`, and renderer integration are framework boundaries rather than application extension points.
Gradient geometry and its transform are normalized to exact Path bounds unless explicit Brush bounds are supplied; those bounds define coordinates and do not clip the fill or stroke. Use the explicit form when separate Paths must share one continuous gradient. Color Brushes ignore the coordinate bounds.

## Paths and text

Use `Path` and its public builder operations for filled or stroked geometry. `StrokeStyle` is the single stroke configuration accepted by `DrawLine()`, `DrawArc()`, `DrawBorder()`, and `StrokePath()`; do not pass width, cap, or join as parallel arguments. Dash entries alternate painted and skipped lengths in local logical units, an odd entry count repeats to form an even cycle, and each Path contour restarts at `dash_offset`.

`Path::ArcTo()` continues the active contour with an endpoint-based elliptical arc. Supply local x/y radii, x-axis rotation in radians, `ArcSize`, `ArcDirection`, and the endpoint; clockwise follows HuxerUI's downward-Y logical coordinates. Undersized radii scale to reach the endpoint, either zero radius produces a line, and a coincident endpoint adds no segment. Use two endpoint arcs for a complete ellipse. Use `PaintContext::DrawArc()` instead for an independent stroked circular arc that does not join a Path contour.

Use `Path::Contains(point, fill_rule)` to test a local point against the same filled geometry used by custom Canvas interaction. Match the rule passed to `FillPath()` or `PushPathClip()`; open contours close implicitly and their boundaries are included. This query does not test stroke width.

```cpp
paint.DrawLine({0.0F, 12.0F}, {120.0F, 12.0F}, Color::Black(),
               StrokeStyle{.width = 2.0F, .cap = StrokeCap::Round, .dash_pattern = {8.0F, 4.0F}});
paint.StrokePath(path, LinearGradient{.stops = {{0.0F, Color::Black()}, {1.0F, Color::White()}}},
                 StrokeStyle{.width = 3.0F, .join = StrokeJoin::Round});
```

Respect fill rules when filling, clipping, or querying paths. Text painting uses `TextStyle`, shaping options, and layout options. `UseTextMeasurer()` provides the active platform text measurer during composition; do not invent glyph metrics. Prefer `Text` for ordinary UI text because it owns layout and semantics.

Canvas text with an empty shaping locale inherits the node's Locale, while a TextMeasurer needs explicit locale input. Keep custom measurement and painting consistent using the [locale and shaping contract](resources-files-network.md#locale-and-text-shaping).

## External textures

`ExternalTexture` is the shared base identity of a retained frame producer displayed through `Image(texture)`. Include the matching `<huxerui/<platform>/external_texture.h>` explicitly and select the concrete type from the producer source:

| Source | Concrete texture |
| --- | --- |
| CPU RGBA/BGRA rows | `windows::PixelTexture` or `linux::PixelTexture` with explicit PixelFrame dimensions and stride |
| Windows D3D11 texture | `windows::D3D11Texture` |
| Linux GL_TEXTURE_2D in a current GdkGLContext | `linux::GlTexture` |
| Linux immutable GDK texture, including DMA-BUF imports | `linux::GdkTexture` |
| Android Bitmap, GL_TEXTURE_2D, or producer Surface | `android::BitmapTexture`, `android::GlTexture`, or `android::SurfaceStreamTexture` respectively |
| Apple CVPixelBuffer or Metal texture | `ios::PixelBufferTexture` / `macos::PixelBufferTexture`, or their `MetalTexture` counterparts |
| Browser WebCodecs VideoFrame | `web::VideoFrameTexture` with `emscripten::val` |

Retain one concrete texture through `std::shared_ptr` and pass it directly to Image. Its intrinsic DIP size is independent of frame pixel dimensions. `Finish()` ends production while preserving the last frame. Use `PlatformView` instead when a native control needs to own input, IME, or accessibility.

PixelTexture copies its rows; Android Bitmap and Apple pixel-buffer publication retain objects whose contents must remain immutable while HuxerUI may render them. GPU publication has source-specific synchronization and snapshot costs, not a universal zero-copy guarantee.

### Windows GPU input

`D3D11Texture::Publish` synchronously copies into an immutable shared snapshot, allowing source reuse or release afterward. Submit source writes first and externally serialize its device and immediate context. The source must be single-sampled, one-mip, one-slice BGRA8 UNORM with D3D11_USAGE_DEFAULT and no CPU access; row zero is the top edge and alpha is opaque or premultiplied. Producer and renderer must use the same graphics adapter. Rendering consumes the shared snapshot without a second GPU copy or implicit CPU fallback. This type is unavailable in Windows 7 compatibility builds; device removal requires publishing from a valid replacement device.

### Linux GPU input

Use `linux::GlTexture::PublishCurrent` for mutable GL_TEXTURE_2D content with level-zero GL_RGBA8 storage. Submit producer writes before publishing with its GdkGLContext current; the context must support OpenGL 3.2 or OpenGL ES 3.1. Publication synchronously copies an immutable GPU snapshot, allowing source reuse or deletion after return. HuxerUI owns GDK construction, synchronization, and snapshot cleanup. Serialize publications for one GlTexture and keep subsequent producer contexts in the same GL share group. Respect GdkGLContext thread affinity, including final GlTexture release, and supply the correct Origin and Alpha metadata.

Use `linux::GdkTexture::Publish` only when the producer already owns a complete immutable `::GdkTexture`, such as a GdkDmabufTexture. Publication retains the native texture without copying or downloading it; the producer owns synchronization, immutable contents, and backing-resource lifetime. Publish may run on a producer thread, and replacing a frame may release its native texture on that thread, so thread-affine destroy callbacks must dispatch their resource work. The active GSK renderer and driver determine direct import or conversion; do not promise end-to-end zero-copy.

### Android GPU input

Call `GlTexture::PublishCurrent` with the producer EGL context current and without overlapping publications on the same texture. It copies GL_TEXTURE_2D content synchronously, so the source may be reused afterward. The optional acquire-fence fd is borrowed and duplicated; the caller still owns it. Without a fence, publication completes outstanding producer GL work. Supply the source's origin and alpha convention explicitly when they differ from the defaults.

Use `SurfaceStreamTexture::Create` for Camera or MediaCodec producers and obtain their Surface through `Surface(environment)`, which returns a JNI local reference. `SetDefaultBufferSize` changes requested physical dimensions, not intrinsic DIP size. Finish closes the producer Surface and consumer while preserving the last latched frame. GPU-backed Android textures require hardware acceleration and do not silently read back to CPU memory.

### Apple GPU input

Use `ios::MetalTexture` or `macos::MetalTexture`; native C++ publication is available in Objective-C++ where `id<MTLTexture>` can be expressed. Finish producer command buffers before publishing a non-framebuffer-only 2D BGRA8 or RGBA8 texture. Publish copies level zero synchronously; choose the correct Origin and Alpha, then reuse or release the source after return. This GPU snapshot is not a zero-copy handoff. Objective-C and Swift expose PixelBufferTexture and MetalTexture through `HuxerUIPlatform`; these objects can travel in PlatformPayload without a texture-ID registry.

### Web GPU producers

`VideoFrameTexture::Publish` clones an open VideoFrame, allowing the caller to close its original afterward. Construction, publication, finish, and destruction stay on the browser main thread. Canvas2D and WebGL2 producers can create `new VideoFrame(canvas, {timestamp})`; capture WebGL content in the drawing callback before its drawing buffer can be discarded. Raw WebGLTexture and GPUTexture objects are not accepted. Check browser VideoFrame support, and do not promise end-to-end zero-copy merely because application code avoids pixel readback.
