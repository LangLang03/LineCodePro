cmake_minimum_required(VERSION 3.20)

project(linecodepro VERSION 0.1.0 LANGUAGES NONE)

include("${CMAKE_CURRENT_SOURCE_DIR}/HuxerUIProject.cmake")
if (HUXERUI_PROJECT_PLAN_ONLY)
    return()
endif ()

file(GLOB_RECURSE APP_SOURCE_FILES CONFIGURE_DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cc"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cxx"
)
if (NOT HUXERUI_LIBRARY_GRAPH_ONLY)
    if (WIN32)
        list(APPEND APP_SOURCE_FILES "${CMAKE_CURRENT_SOURCE_DIR}/platform/windows/main.cpp")
    elseif (APPLE AND NOT IOS)
        list(APPEND APP_SOURCE_FILES "${CMAKE_CURRENT_SOURCE_DIR}/platform/macos/main.cpp")
    elseif (CMAKE_SYSTEM_NAME STREQUAL "Linux")
        list(APPEND APP_SOURCE_FILES "${CMAKE_CURRENT_SOURCE_DIR}/platform/linux/main.cpp")
    endif ()
endif ()

huxerui_add_app(linecodepro
        SOURCES
            ${APP_SOURCE_FILES}
        RESOURCES
            resources
        RESOURCE_NAMESPACE
            app
        ${HUXERUI_APP_RESOURCE_OUTPUT_ARGUMENTS}
        BUNDLE_NAME
            "LineCodePro"
        BUNDLE_IDENTIFIER
            "cn.lineai"
)

huxerui_use_library(linecodepro
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "5e3d040079629e698ca2f456ad79042cbbcd6aba"
)

huxerui_use_library(linecodepro
        TARGET HuxerUI::WebView
        URL "https://github.com/HuxerUI/Lib-WebView.git"
        COMMIT "d07820374b4a5f75659cd29e4e4fe2bcc899b8ef"
)

if (NOT HUXERUI_LIBRARY_GRAPH_ONLY)
    find_package(ZLIB REQUIRED)
    include("${CMAKE_CURRENT_SOURCE_DIR}/cmake/LineCodeSsh.cmake")
    linecode_enable_ssh(linecodepro)
    target_include_directories(linecodepro PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/src")
    target_link_libraries(linecodepro PRIVATE ZLIB::ZLIB)
    if (ANDROID)
        target_link_libraries(linecodepro PRIVATE log)
    endif ()
    target_compile_features(linecodepro PRIVATE cxx_std_23)
    if (WIN32)
        target_link_libraries(linecodepro PRIVATE shell32)
    endif ()
    set_target_properties(linecodepro PROPERTIES
            CXX_STANDARD 23
            CXX_STANDARD_REQUIRED ON
            CXX_EXTENSIONS OFF
    )
endif ()

huxerui_configure_project_app(linecodepro)
