cmake_minimum_required(VERSION 3.22)
project(linecode_local_model LANGUAGES CXX)

set(LINCODE_LLAMA_CPP_DIR "" CACHE PATH "llama.cpp source checkout (headers)")
set(LINCODE_LLAMA_PREBUILT_DIR "" CACHE PATH "directory containing libllama.so and libggml*.so")

if (NOT EXISTS "${LINCODE_LLAMA_CPP_DIR}/include/llama.h")
  message(FATAL_ERROR "LINCODE_LLAMA_CPP_DIR is not a llama.cpp checkout: ${LINCODE_LLAMA_CPP_DIR}")
endif ()
if (NOT EXISTS "${LINCODE_LLAMA_PREBUILT_DIR}/libllama.so")
  message(FATAL_ERROR "LINCODE_LLAMA_PREBUILT_DIR has no libllama.so: ${LINCODE_LLAMA_PREBUILT_DIR}")
endif ()

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION "${LINCODE_LLAMA_PREBUILT_DIR}/libllama.so")
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION "${LINCODE_LLAMA_PREBUILT_DIR}/libggml.so")
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION "${LINCODE_LLAMA_PREBUILT_DIR}/libggml-base.so")
add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION "${LINCODE_LLAMA_PREBUILT_DIR}/libggml-cpu.so")

add_library(linecode_local_model SHARED llama_jni.cpp)
target_include_directories(linecode_local_model PRIVATE
    "${LINCODE_LLAMA_CPP_DIR}/include"
    "${LINCODE_LLAMA_CPP_DIR}/ggml/include")
target_compile_features(linecode_local_model PRIVATE cxx_std_17)
target_compile_definitions(linecode_local_model PRIVATE LINCODE_LLAMA_REVISION="b11201")
target_link_libraries(linecode_local_model PRIVATE llama ggml ggml-base ggml-cpu android log)

# Package the prebuilt llama.cpp runtime next to the JNI library so AGP collects
# it from the native build output for this ABI.
add_custom_command(TARGET linecode_local_model POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${LINCODE_LLAMA_PREBUILT_DIR}/libllama.so"
        "${LINCODE_LLAMA_PREBUILT_DIR}/libggml.so"
        "${LINCODE_LLAMA_PREBUILT_DIR}/libggml-base.so"
        "${LINCODE_LLAMA_PREBUILT_DIR}/libggml-cpu.so"
        "$<TARGET_FILE_DIR:linecode_local_model>")
