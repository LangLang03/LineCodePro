if (HUXERUI_PROJECT_PLAN_OUTPUT)
    get_filename_component(HUXERUI_PROJECT_PLAN_DIRECTORY
            "${HUXERUI_PROJECT_PLAN_OUTPUT}"
            DIRECTORY
    )
    file(MAKE_DIRECTORY "${HUXERUI_PROJECT_PLAN_DIRECTORY}")
    file(WRITE "${HUXERUI_PROJECT_PLAN_OUTPUT}" [=[
{
  "schema": 1,
  "kind": "app",
  "name": "LineCodePro",
  "id": "com.linecode.linecodepro",
  "target": "linecodepro"
}
]=])
endif ()

if (HUXERUI_PROJECT_PLAN_ONLY)
    return()
endif ()

if (CMAKE_SYSTEM_NAME STREQUAL "Darwin" AND NOT CMAKE_OSX_DEPLOYMENT_TARGET)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "12.0" CACHE STRING "Minimum macOS deployment target" FORCE)
endif ()

if (NOT HUXERUI_LIBRARY_GRAPH_ONLY)
    enable_language(CXX)
endif ()

if (NOT TARGET HuxerUI::huxerui)
    set(HUXERUI_HOME "$ENV{HUXERUI_HOME}" CACHE PATH "HuxerUI SDK or source directory")
    if (HUXERUI_HOME AND EXISTS "${HUXERUI_HOME}/CMakeLists.txt"
            AND EXISTS "${HUXERUI_HOME}/include/huxerui/huxerui.h")
        set(HUXERUI_BUILD_TESTS OFF CACHE BOOL "" FORCE)
        set(HUXERUI_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
        if (ANDROID)
            set(HUXERUI_BUILD_SHARED ON CACHE BOOL "" FORCE)
            set(HUXERUI_BUILD_STATIC OFF CACHE BOOL "" FORCE)
        else ()
            set(HUXERUI_BUILD_SHARED OFF CACHE BOOL "" FORCE)
            set(HUXERUI_BUILD_STATIC ON CACHE BOOL "" FORCE)
        endif ()
        add_subdirectory("${HUXERUI_HOME}" "${CMAKE_BINARY_DIR}/huxerui-sdk" EXCLUDE_FROM_ALL)
    elseif (HUXERUI_HOME)
        find_package(HuxerUI CONFIG REQUIRED
                PATHS "${HUXERUI_HOME}"
                NO_DEFAULT_PATH
                NO_CMAKE_FIND_ROOT_PATH
        )
    else ()
        find_package(HuxerUI CONFIG REQUIRED)
    endif ()
endif ()

if (WIN32)
    include("${CMAKE_CURRENT_SOURCE_DIR}/platform/windows/huxerui.cmake" OPTIONAL)
elseif (APPLE AND NOT IOS)
    include("${CMAKE_CURRENT_SOURCE_DIR}/platform/macos/huxerui.cmake" OPTIONAL)
elseif (CMAKE_SYSTEM_NAME STREQUAL "Linux")
    include("${CMAKE_CURRENT_SOURCE_DIR}/platform/linux/huxerui.cmake" OPTIONAL)
elseif (EMSCRIPTEN)
    include("${CMAKE_CURRENT_SOURCE_DIR}/platform/web/huxerui.cmake" OPTIONAL)
endif ()

set(HUXERUI_LIBRARY_GRAPH_OUTPUT
        "${CMAKE_CURRENT_SOURCE_DIR}/.huxerui/generated/libraries.json"
)

set(HUXERUI_APP_RESOURCE_OUTPUT_ARGUMENTS)
if (ANDROID AND HUXERUI_ANDROID_RESOURCE_OUTPUT_ROOT)
    list(APPEND HUXERUI_APP_RESOURCE_OUTPUT_ARGUMENTS
            RESOURCE_OUTPUT_DIRECTORY
            "${HUXERUI_ANDROID_RESOURCE_OUTPUT_ROOT}/${ANDROID_ABI}"
    )
endif ()

function(huxerui_configure_project_app target_name)
    if (NOT TARGET ${target_name})
        message(FATAL_ERROR "huxerui_configure_project_app() target does not exist: ${target_name}")
    endif ()

    get_target_property(HUXERUI_PROJECT_APP_INSTALL_COMPONENT
            ${target_name}
            HUXERUI_APPLICATION_INSTALL_COMPONENT
    )
    if (NOT HUXERUI_PROJECT_APP_INSTALL_COMPONENT
            OR HUXERUI_PROJECT_APP_INSTALL_COMPONENT MATCHES "-NOTFOUND$")
        message(FATAL_ERROR "HuxerUI application target is missing its install component: ${target_name}")
    endif ()

    if (WIN32 AND HUXERUI_WINDOWS_MANIFEST)
        target_sources(${target_name} PRIVATE "${HUXERUI_WINDOWS_MANIFEST}")
    endif ()
    if (WIN32 AND COMMAND huxerui_configure_windows_project_package)
        huxerui_configure_windows_project_package(
                ${target_name}
                "${HUXERUI_PROJECT_APP_INSTALL_COMPONENT}"
        )
    endif ()
    if (APPLE AND NOT IOS AND HUXERUI_MACOS_INFO_PLIST)
        set_target_properties(${target_name} PROPERTIES
                MACOSX_BUNDLE_INFO_PLIST "${HUXERUI_MACOS_INFO_PLIST}"
        )
    endif ()
    if (APPLE AND NOT IOS AND COMMAND huxerui_configure_macos_project_package)
        huxerui_configure_macos_project_package(
                ${target_name}
                "${HUXERUI_PROJECT_APP_INSTALL_COMPONENT}"
        )
    endif ()
    if (CMAKE_SYSTEM_NAME STREQUAL "Linux" AND COMMAND huxerui_configure_linux_project_package)
        huxerui_configure_linux_project_package(
                ${target_name}
                "${HUXERUI_PROJECT_APP_INSTALL_COMPONENT}"
        )
    endif ()
    if (EMSCRIPTEN AND COMMAND huxerui_configure_web_app)
        huxerui_configure_web_app(${target_name})
    endif ()
endfunction()
