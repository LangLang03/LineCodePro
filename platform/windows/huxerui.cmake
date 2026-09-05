set(HUXERUI_WINDOWS_MANIFEST "${CMAKE_CURRENT_LIST_DIR}/app.manifest")

function(huxerui_configure_windows_project_package target_name install_component)
    if (NOT HUXERUI_PACKAGE)
        return()
    endif ()

    install(TARGETS ${target_name}
            RUNTIME DESTINATION .
            COMPONENT "${install_component}"
    )
    get_target_property(HUXERUI_WINDOWS_APP_RESOURCES
            ${target_name}
            HUXERUI_RESOURCE_PACKAGE
    )
    if (HUXERUI_WINDOWS_APP_RESOURCES
            AND NOT HUXERUI_WINDOWS_APP_RESOURCES MATCHES "-NOTFOUND$")
        install(DIRECTORY "${HUXERUI_WINDOWS_APP_RESOURCES}/"
                DESTINATION "${target_name}.resources"
                COMPONENT "${install_component}"
        )
    endif ()

    string(UUID HUXERUI_WINDOWS_MSI_UPGRADE_CODE
            NAMESPACE 6ba7b810-9dad-11d1-80b4-00c04fd430c8
            NAME "com.linecode.linecodepro.msi"
            TYPE SHA1
            UPPER
    )
    string(UUID HUXERUI_WINDOWS_BUNDLE_UPGRADE_CODE
            NAMESPACE 6ba7b810-9dad-11d1-80b4-00c04fd430c8
            NAME "com.linecode.linecodepro.bundle"
            TYPE SHA1
            UPPER
    )
    set(HUXERUI_WINDOWS_PACKAGE_DIRECTORY
            "${CMAKE_CURRENT_BINARY_DIR}/huxerui-package/windows"
    )
    set(HUXERUI_WINDOWS_PROJECT_VERSION "${PROJECT_VERSION}")
    file(MAKE_DIRECTORY "${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}")
    configure_file(
            "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/package/Package.wxs.in"
            "${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/Package.wxs"
            @ONLY
    )
    configure_file(
            "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/package/Bundle.wxs.in"
            "${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/Bundle.wxs"
            @ONLY
    )

    file(GLOB_RECURSE HUXERUI_WINDOWS_INSTALLER_SOURCES CONFIGURE_DEPENDS
            "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/package/src/*.cpp"
            "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/package/src/*.cc"
            "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/package/src/*.cxx"
    )
    huxerui_add_windows_installer(${target_name}_installer
            SOURCES
                ${HUXERUI_WINDOWS_INSTALLER_SOURCES}
            RESOURCES
                "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/package/resources"
            RESOURCE_NAMESPACE
                installer
            INTEGRATION_OUTPUT
                "${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/$<CONFIG>/installer.json"
    )
    set_target_properties(${target_name}_installer PROPERTIES
            OUTPUT_NAME "linecodepro-Installer"
    )
    file(GENERATE
            OUTPUT "${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/$<CONFIG>/package.json"
            CONTENT "{\n  \"schema\": 1,\n  \"name\": \"LineCodePro\",\n  \"target\": \"linecodepro\",\n  \"version\": \"${PROJECT_VERSION}\",\n  \"installComponent\": \"${install_component}\",\n  \"packageSource\": \"${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/Package.wxs\",\n  \"bundleSource\": \"${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/Bundle.wxs\",\n  \"installerPlan\": \"${HUXERUI_WINDOWS_PACKAGE_DIRECTORY}/$<CONFIG>/installer.json\"\n}\n"
    )
endfunction()
