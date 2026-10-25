# Build parameters for the in-process llama.cpp backend.
#
# The default configuration builds without llama.cpp at all: nothing includes
# `llama.h`, nothing links the runtime, and the application compiles its UI
# without any local-model entry point. That is what keeps the shipped artifact
# small. Turning `LINCODE_ENABLE_LOCAL_MODEL` on adds the backend, and
# `LINCODE_LOCAL_MODEL_BUNDLE` decides whether the resulting native libraries
# are packaged into the artifact.
#
# Usage:
#   cmake -S . -B build -DLINCODE_ENABLE_LOCAL_MODEL=ON -DLINCODE_LOCAL_MODEL_BUNDLE=ON
#
# Runtime resolution order for the llama.cpp sources:
#   1. `LINCODE_LLAMA_CPP_SOURCE_DIR`, when a checkout already exists locally.
#   2. A prebuilt package found through `find_package(llama CONFIG)`.
#   3. `FetchContent` from `LINCODE_LLAMA_CPP_GIT_REPOSITORY` at `LINCODE_LLAMA_CPP_GIT_TAG`.
#
# The order matters for offline builds: a caller who cannot reach the pinned
# repository points `LINCODE_LLAMA_CPP_SOURCE_DIR` at a local checkout and the
# remaining steps are skipped.

option(LINCODE_ENABLE_LOCAL_MODEL
        "Compile the in-process llama.cpp backend and show its UI entry points"
        OFF
)
option(LINCODE_LOCAL_MODEL_BUNDLE
        "Package the llama.cpp and ggml native libraries into the artifact"
        OFF
)

# Accelerators. Each option is requested, then narrowed to what the resolved
# llama.cpp tree or package actually provides: a backend the tree does not
# implement is reported as unavailable instead of being claimed.
option(LINCODE_LOCAL_MODEL_GPU_VULKAN
        "Request the ggml Vulkan GPU backend (Android and desktop GPUs)"
        ON
)
option(LINCODE_LOCAL_MODEL_GPU_OPENCL
        "Request the ggml OpenCL GPU backend (Adreno GPUs)"
        ON
)
# NPU acceleration is not supported yet.
#
# Upstream llama.cpp's only NPU backend is `GGML_HEXAGON`. Building it needs the
# Qualcomm Hexagon SDK: its HTP skels are compiled with the Hexagon toolchain and
# the host side compiles against SDK headers that cannot be redistributed with
# this project. Requiring a vendor account to build a release is not a trade this
# project makes, so the option is absent rather than present-and-broken. GPU
# acceleration through Vulkan and OpenCL does not depend on any vendor SDK.

# The GPU backends compile against vendor SDKs, and the Android NDK does not
# provide either one: llama.cpp's Vulkan backend needs a Vulkan setup that
# satisfies FindVulkan, and its OpenCL backend needs OpenCL headers plus a
# loader. Requesting them unconditionally therefore broke every Android build
# with "Could NOT find Vulkan". Each request is probed and dropped when the
# prerequisite is absent, which is what lets a plain build succeed CPU-only
# while a developer with the SDK installed still gets the backend. The backend
# macros are read back from the built targets afterwards, so the UI never
# advertises one that was skipped.
set(linecode_effective_vulkan ${LINCODE_LOCAL_MODEL_GPU_VULKAN})
if (linecode_effective_vulkan)
  find_package(Vulkan QUIET)
  if (NOT Vulkan_FOUND)
    message(STATUS
            "LineCode local model: Vulkan requested but no Vulkan SDK is "
            "available (set VULKAN_SDK, or pass -DVulkan_LIBRARY and "
            "-DVulkan_INCLUDE_DIR); building without it"
    )
    set(linecode_effective_vulkan OFF)
  endif ()
endif ()

set(linecode_effective_opencl ${LINCODE_LOCAL_MODEL_GPU_OPENCL})
if (linecode_effective_opencl)
  find_package(OpenCL QUIET)
  if (NOT OpenCL_FOUND)
    message(STATUS
            "LineCode local model: OpenCL requested but no OpenCL headers and "
            "loader are available for this target; building without it"
    )
    set(linecode_effective_opencl OFF)
  endif ()
endif ()

set(LINCODE_LLAMA_CPP_SOURCE_DIR ""
        CACHE PATH
        "Existing llama.cpp source checkout; skips prebuilt lookup and FetchContent"
)
set(LINCODE_LLAMA_CPP_GIT_REPOSITORY
        "https://github.com/ggml-org/llama.cpp.git"
        CACHE STRING "llama.cpp repository fetched when no local source is given"
)
# Pinned to the release this integration was written and verified against: tag
# `b11201` is commit 2145525a4081d66ff1a87cf43ef809f95a85ac0c. The llama.cpp C
# API is not stable across releases, so an arbitrary newer commit is not a
# supported substitute. A tag rather than a bare commit is deliberate -- it is
# what makes the shallow clone below possible, and cloning the full history
# costs hundreds of megabytes on every configured build.
set(LINCODE_LLAMA_CPP_GIT_TAG
        "b11201"
        CACHE STRING "llama.cpp revision fetched when no local source is given"
)

# Backend name in the ggml build mapped to the macro this project reads. Kept as
# one list so the source-built and prebuilt paths derive the macros the same way.
set(LINCODE_LOCAL_MODEL_BACKEND_MACROS
        "ggml-vulkan|LINCODE_LOCAL_BACKEND_VULKAN"
        "ggml-opencl|LINCODE_LOCAL_BACKEND_OPENCL"
        "ggml-cuda|LINCODE_LOCAL_BACKEND_CUDA"
        "ggml-metal|LINCODE_LOCAL_BACKEND_METAL"
)

function(linecode_local_model_validate_source source_dir)
  if (NOT EXISTS "${source_dir}/include/llama.h")
    message(FATAL_ERROR
            "LINCODE_LLAMA_CPP_SOURCE_DIR does not look like a llama.cpp "
            "checkout: ${source_dir}/include/llama.h is missing"
    )
  endif ()
  if (NOT EXISTS "${source_dir}/ggml/CMakeLists.txt")
    message(FATAL_ERROR
            "LINCODE_LLAMA_CPP_SOURCE_DIR has no ggml subproject: "
            "${source_dir}/ggml/CMakeLists.txt is missing"
    )
  endif ()
endfunction()

# Applies one macro per backend from the ggml backends that are really linked.
function(linecode_local_model_apply_backends target available_backends)
  foreach (_pair IN LISTS LINCODE_LOCAL_MODEL_BACKEND_MACROS)
    string(REPLACE "|" ";" _parts "${_pair}")
    list(GET _parts 0 _backend)
    list(GET _parts 1 _macro)
    set(_enabled 0)
    if (_backend IN_LIST available_backends)
      set(_enabled 1)
    endif ()
    target_compile_definitions(${target} PRIVATE "${_macro}=${_enabled}")
  endforeach ()
endfunction()

# Collects the ggml backend targets the build produced. Target existence is the
# only honest source here: passing `-DGGML_VULKAN=ON` to a tree without a Vulkan
# backend is a silent no-op, so the request alone proves nothing. Names are
# normalized to the `ggml-<backend>` form because a source build produces
# `ggml-vulkan` while an installed package produces `ggml::vulkan`.
function(linecode_local_model_collect_backend_targets out_var)
  set(_backends "")
  foreach (_suffix IN ITEMS vulkan opencl cuda metal)
    if (TARGET "ggml-${_suffix}" OR TARGET "ggml::${_suffix}")
      list(APPEND _backends "ggml-${_suffix}")
    endif ()
  endforeach ()
  set(${out_var} "${_backends}" PARENT_SCOPE)
endfunction()

# Locates an installed llama.cpp without going through its CMake package.
#
# `llama-config.cmake` marks the imported target with `INTERFACE_COMPILE_FEATURES
# c_std_90`. CMake only resolves a C compile feature when the C language is
# enabled, so consuming that package from this C++-only project fails
# configuration with "No known features for C compiler". Locating the library and
# headers directly avoids that coupling and keeps the prebuilt path usable from
# both the application and the test project.
function(linecode_local_model_find_prebuilt out_found)
  set(${out_found} FALSE PARENT_SCOPE)
  # A cross-compile must never pick up a host llama.cpp, so the lookup only runs
  # for a native build; Android always builds the runtime from source.
  if (ANDROID OR CMAKE_CROSSCOMPILING)
    return()
  endif ()

  find_path(LINCODE_LLAMA_INCLUDE_DIR NAMES llama.h)
  find_library(LINCODE_LLAMA_LIBRARY NAMES llama)
  if (NOT LINCODE_LLAMA_INCLUDE_DIR OR NOT LINCODE_LLAMA_LIBRARY)
    return()
  endif ()
  set(LINCODE_LLAMA_INCLUDE_DIR "${LINCODE_LLAMA_INCLUDE_DIR}" PARENT_SCOPE)
  set(LINCODE_LLAMA_LIBRARY "${LINCODE_LLAMA_LIBRARY}" PARENT_SCOPE)

  # The runtime is useless without the ggml core libraries that carry the tensor
  # and backend-registry code.
  set(_core "")
  foreach (_name IN ITEMS ggml ggml-base)
    find_library(LINCODE_GGML_${_name}_LIBRARY NAMES ${_name})
    if (LINCODE_GGML_${_name}_LIBRARY)
      list(APPEND _core "${LINCODE_GGML_${_name}_LIBRARY}")
      set(LINCODE_GGML_${_name}_LIBRARY
              "${LINCODE_GGML_${_name}_LIBRARY}" PARENT_SCOPE)
    endif ()
  endforeach ()

  # Which accelerators the installed runtime carries is answered by the backend
  # libraries next to it, which is a fact about the package rather than a
  # request this project can still influence.
  get_filename_component(_directory "${LINCODE_LLAMA_LIBRARY}" DIRECTORY)
  set(_backends "")
  foreach (_suffix IN ITEMS vulkan opencl cuda metal)
    find_library(LINCODE_GGML_${_suffix}_LIBRARY
            NAMES ggml-${_suffix}
            HINTS "${_directory}"
            NO_DEFAULT_PATH
    )
    if (LINCODE_GGML_${_suffix}_LIBRARY)
      list(APPEND _backends "ggml-${_suffix}")
      set(LINCODE_GGML_${_suffix}_LIBRARY
              "${LINCODE_GGML_${_suffix}_LIBRARY}" PARENT_SCOPE)
    endif ()
  endforeach ()

  set(LINCODE_LLAMA_PREBUILT_BACKENDS "${_backends}" PARENT_SCOPE)
  set(LINCODE_LLAMA_PREBUILT_CORE "${_core}" PARENT_SCOPE)
  set(${out_found} TRUE PARENT_SCOPE)
endfunction()

function(linecode_configure_local_model target)
  if (NOT TARGET ${target})
    message(FATAL_ERROR
            "linecode_configure_local_model() target does not exist: ${target}"
    )
  endif ()

  if (NOT LINCODE_ENABLE_LOCAL_MODEL)
    # Stated explicitly so a stale cache entry cannot leave a half-enabled build
    # and so the off state is visible in the compile command line.
    target_compile_definitions(${target} PRIVATE
            LINCODE_LOCAL_MODEL_ENABLED=0
            LINCODE_LOCAL_MODEL_PACKAGED=0
            LINCODE_LOCAL_BACKEND_VULKAN=0
            LINCODE_LOCAL_BACKEND_OPENCL=0
            LINCODE_LOCAL_BACKEND_CUDA=0
            LINCODE_LOCAL_BACKEND_METAL=0
    )
    return()
  endif ()

  if (LINCODE_LOCAL_MODEL_BUNDLE)
    target_compile_definitions(${target} PRIVATE LINCODE_LOCAL_MODEL_PACKAGED=1)
  else ()
    target_compile_definitions(${target} PRIVATE LINCODE_LOCAL_MODEL_PACKAGED=0)
  endif ()
  target_compile_definitions(${target} PRIVATE LINCODE_LOCAL_MODEL_ENABLED=1)

  # llama.cpp's own build options. Unknown cache entries are ignored by a tree
  # that no longer defines them, which is what lets one flag set serve several
  # llama.cpp revisions; the backend macros are derived from the built targets
  # afterwards rather than from these requests.
  # llama.cpp's libraries are built shared on purpose. A static build compiles
  # the runtime straight into the application library, where LINCODE_LOCAL_MODEL_BUNDLE
  # can no longer exclude it from the package — the switch would silently stop
  # meaning anything. Shared libraries keep the two knobs independent: the
  # backend is linked either way, and packaging decides whether the .so files
  # ship. The previous value is restored so this does not leak into any other
  # subproject.
  set(_linecode_build_shared_previous ${BUILD_SHARED_LIBS})
  set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
  set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
  if (linecode_effective_vulkan)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
  endif ()
  if (linecode_effective_opencl)
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
  endif ()
  set(available_backends "")
  set(prebuilt_linked FALSE)


  if (LINCODE_LLAMA_CPP_SOURCE_DIR)
    message(STATUS "LineCode local model: llama.cpp sources at "
            "${LINCODE_LLAMA_CPP_SOURCE_DIR}"
    )
    linecode_local_model_validate_source("${LINCODE_LLAMA_CPP_SOURCE_DIR}")
    add_subdirectory("${LINCODE_LLAMA_CPP_SOURCE_DIR}"
            "${CMAKE_BINARY_DIR}/linecode-llama-cpp"
            EXCLUDE_FROM_ALL
    )
  else ()
    linecode_local_model_find_prebuilt(found_prebuilt)
    if (found_prebuilt)
      message(STATUS
              "LineCode local model: prebuilt llama.cpp at "
              "${LINCODE_LLAMA_LIBRARY}"
      )
      target_include_directories(${target} PRIVATE
              "${LINCODE_LLAMA_INCLUDE_DIR}")
      target_link_libraries(${target} PRIVATE
              "${LINCODE_LLAMA_LIBRARY}"
              ${LINCODE_LLAMA_PREBUILT_CORE}
      )
      set(prebuilt_linked TRUE)
      set(available_backends "${LINCODE_LLAMA_PREBUILT_BACKENDS}")
    else ()
      message(STATUS
              "LineCode local model: fetching llama.cpp "
              "${LINCODE_LLAMA_CPP_GIT_TAG} from "
              "${LINCODE_LLAMA_CPP_GIT_REPOSITORY}"
      )
      include(FetchContent)
      # A shallow clone is what keeps the configure step affordable. It only
      # works when the pin names a tag or a branch, so a caller who overrides
      # the pin with a bare commit gets a full clone instead of a fetch error.
      if (LINCODE_LLAMA_CPP_GIT_TAG MATCHES "^[0-9a-fA-F]{7,40}$")
        set(_linecode_llama_shallow OFF)
      else ()
        set(_linecode_llama_shallow ON)
      endif ()
      FetchContent_Declare(linecode_llama_cpp
              GIT_REPOSITORY "${LINCODE_LLAMA_CPP_GIT_REPOSITORY}"
              GIT_TAG "${LINCODE_LLAMA_CPP_GIT_TAG}"
              GIT_SHALLOW ${_linecode_llama_shallow}
      )
      unset(_linecode_llama_shallow)
      FetchContent_MakeAvailable(linecode_llama_cpp)
    endif ()
  endif ()

  # Restored after the runtime has been added: BUILD_SHARED_LIBS only has to be
  # set while llama.cpp configures its own targets.
  if (DEFINED _linecode_build_shared_previous)
    set(BUILD_SHARED_LIBS ${_linecode_build_shared_previous} CACHE BOOL "" FORCE)
    unset(_linecode_build_shared_previous)
  else ()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
  endif ()

  if (NOT TARGET llama AND NOT prebuilt_linked)
    message(FATAL_ERROR
            "LineCode: LINCODE_ENABLE_LOCAL_MODEL is ON but no llama.cpp "
            "runtime was resolved. Provide one of:\n"
            "  - -DLINCODE_LLAMA_CPP_SOURCE_DIR=/path/to/llama.cpp\n"
            "  - an installed llama.cpp reachable through find_path/find_library\n"
            "  - network access to ${LINCODE_LLAMA_CPP_GIT_REPOSITORY}\n"
            "Or configure with -DLINCODE_ENABLE_LOCAL_MODEL=OFF to build without "
            "the local model backend."
    )
  endif ()

  if (TARGET llama)
    target_link_libraries(${target} PRIVATE llama)
  endif ()

  if (available_backends STREQUAL "")
    linecode_local_model_collect_backend_targets(available_backends)
  endif ()
  linecode_local_model_apply_backends(${target} "${available_backends}")
  message(STATUS "LineCode local model: linked ggml backends: "
          "${available_backends}"
  )

  # Android loads every packaged shared library from the app's native library
  # directory, so the ggml backend libraries have to be packaged alongside.
  if (ANDROID)
    target_link_libraries(${target} PRIVATE android log)
  endif ()
endfunction()
