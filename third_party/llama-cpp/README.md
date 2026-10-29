# llama.cpp runtime (vendored)

Vendored arm64 llama.cpp runtime for the local model build (`linecodeEnableLocalModel=true`).
Keeping it in this repository means CI can build the local model APK without a sibling
`LineCodePro-C++` checkout.

Provenance:

- llama.cpp / ggml revision `b11201` (`LINCODE_LLAMA_REVISION` in
  `modules/engine-local/src/main/cpp/CMakeLists.txt`).
- Headers copied from the llama.cpp source tree (`include/`, `ggml/include/`).
- `prebuilt/arm64-v8a/*.so` built with Android NDK `29.0.14206865` in `RelWithDebInfo`,
  `arm64-v8a` only.

Refresh procedure:

1. Build the arm64 llama.cpp runtime in the `LineCodePro-C++` project and bump
   `LINCODE_LLAMA_REVISION` if the revision changes.
2. Replace `include/`, `ggml/include/`, `prebuilt/arm64-v8a/` and `LICENSE` with the new files.
3. Rebuild the local variant: `./gradlew clean :app:assembleRelease -PlinecodeEnableLocalModel=true`.

`-PlinecodeLlamaCppSourceDir=...` and `-PlinecodeLlamaPrebuiltDir=...` still override these
directories for a local build against a separate llama.cpp checkout.
