cmake_minimum_required(VERSION 3.20)

project(linecodepro_tests LANGUAGES CXX)


set(HUXERUI_HOME "$ENV{HUXERUI_HOME}" CACHE PATH "HuxerUI SDK directory")
if (HUXERUI_HOME)
    find_package(HuxerUI CONFIG REQUIRED
            COMPONENTS shared testing
            PATHS "${HUXERUI_HOME}"
            NO_DEFAULT_PATH
    )
else ()
    find_package(HuxerUI CONFIG REQUIRED COMPONENTS shared testing)
endif ()
find_package(ZLIB REQUIRED)
find_package(GTest CONFIG REQUIRED)
link_libraries(GTest::gtest_main)
# Every target here is built on HuxerUI types, so they all need the SDK's
# include directories. Working from an installed SDK on a machine that also has
# a system-wide copy of the headers hides a missing dependency -- the compiler
# finds huxerui/*.h through /usr/local/include and the omission only shows up
# on a clean runner.
#
# Only the headers are added, not the library. The SDK's GUI library links
# GTK4, graphene, epoxy and libsoup, none of which a unit test needs and none
# of which a build container has; linking it here made every target fail at
# link time. Targets that do use the library link it explicitly.
set(_huxerui_include_dirs "")
foreach (_huxerui_target HuxerUI::testing HuxerUI::huxerui)
    if (TARGET ${_huxerui_target})
        get_target_property(_huxerui_candidate ${_huxerui_target}
                INTERFACE_INCLUDE_DIRECTORIES)
        if (_huxerui_candidate)
            list(APPEND _huxerui_include_dirs ${_huxerui_candidate})
        endif ()
    endif ()
endforeach ()
if (_huxerui_include_dirs)
    list(REMOVE_DUPLICATES _huxerui_include_dirs)
    include_directories(${_huxerui_include_dirs})
endif ()
unset(_huxerui_candidate)
unset(_huxerui_target)

# GCC 13 and 14 mis-analyse the destructor of a `std::expected` held in a
# coroutine frame reached through `std::function` and report
# `-Wfree-nonheap-object` for a delete that never happens; later releases
# corrected the diagnostic. CI pins GCC 16, so this is not needed there, but
# Ubuntu's default compiler is still 13 and a contributor building with it
# would otherwise hit an error for correct code. Suppressing that one warning
# is cheaper than asking everyone to match CI's toolchain.
if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    add_compile_options(-Wno-free-nonheap-object)
endif ()

add_executable(linecodepro_tests
        application_tests.cpp
        ../src/application/chat_session.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/send_message.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/domain/input_attachment.cpp
        ../src/infrastructure/in_memory_conversation_store.cpp
)
target_include_directories(linecodepro_tests PRIVATE ../src)
target_compile_features(linecodepro_tests PRIVATE cxx_std_23)
set_target_properties(linecodepro_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(linecodepro_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(linecodepro_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(app_route_tests app_route_tests.cpp)
target_include_directories(app_route_tests PRIVATE ../src)
target_compile_features(app_route_tests PRIVATE cxx_std_23)
set_target_properties(app_route_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(app_route_tests PRIVATE /W4 /WX /permissive-)
else ()
    target_compile_options(app_route_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(working_status_presentation_tests
        working_status_presentation_tests.cpp
)
target_include_directories(working_status_presentation_tests PRIVATE ../src)
target_compile_features(working_status_presentation_tests PRIVATE cxx_std_23)
set_target_properties(working_status_presentation_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(working_status_presentation_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(working_status_presentation_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(project_workspace_presentation_tests
        project_workspace_presentation_tests.cpp
        ../src/presentation/project_workspace_path.cpp
)
target_include_directories(project_workspace_presentation_tests PRIVATE ../src)
target_compile_features(project_workspace_presentation_tests PRIVATE cxx_std_23)
target_link_libraries(project_workspace_presentation_tests PRIVATE HuxerUI::huxerui)
set_target_properties(project_workspace_presentation_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(project_workspace_presentation_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(project_workspace_presentation_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(model_form_service_tests
        model_form_service_tests.cpp
        ../src/application/model_form_service.cpp
        ../src/domain/model_config.cpp
)
target_include_directories(model_form_service_tests PRIVATE ../src)
target_compile_features(model_form_service_tests PRIVATE cxx_std_23)
set_target_properties(model_form_service_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(model_form_service_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(model_form_service_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(model_form_presentation_tests
        model_form_presentation_tests.cpp
)
target_include_directories(model_form_presentation_tests PRIVATE ../src)
target_compile_features(model_form_presentation_tests PRIVATE cxx_std_23)
set_target_properties(model_form_presentation_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(model_form_presentation_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(model_form_presentation_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(linecodepro_theme_tests
        theme_settings_tests.cpp
        ../src/application/theme_settings.cpp
        ../src/domain/theme_palette.cpp
)
target_include_directories(linecodepro_theme_tests PRIVATE ../src)
target_compile_features(linecodepro_theme_tests PRIVATE cxx_std_23)
set_target_properties(linecodepro_theme_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(linecodepro_theme_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(linecodepro_theme_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(presentation_theme_tests
        presentation_theme_tests.cpp
        ../src/presentation/line_theme.cpp
        ../src/domain/theme_palette.cpp
)
target_include_directories(presentation_theme_tests PRIVATE ../src)
target_compile_features(presentation_theme_tests PRIVATE cxx_std_23)
target_link_libraries(presentation_theme_tests PRIVATE HuxerUI::huxerui)
set_target_properties(presentation_theme_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(presentation_theme_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(presentation_theme_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

find_package(SQLite3 REQUIRED)
if (TARGET SQLite::SQLite3 AND NOT TARGET SQLite3::SQLite3)
    add_library(SQLite3::SQLite3 ALIAS SQLite::SQLite3)
endif ()

add_executable(legacy_conversation_schema_tests
        legacy_conversation_schema_tests.cpp
)
target_include_directories(legacy_conversation_schema_tests PRIVATE ../src)
target_compile_features(legacy_conversation_schema_tests PRIVATE cxx_std_23)
target_link_libraries(legacy_conversation_schema_tests PRIVATE SQLite3::SQLite3)
set_target_properties(legacy_conversation_schema_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(legacy_conversation_schema_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(legacy_conversation_schema_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(legacy_feature_schema_tests
        legacy_feature_schema_tests.cpp
)
target_include_directories(legacy_feature_schema_tests PRIVATE ../src)
target_compile_features(legacy_feature_schema_tests PRIVATE cxx_std_23)
target_link_libraries(legacy_feature_schema_tests PRIVATE SQLite3::SQLite3)
set_target_properties(legacy_feature_schema_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(legacy_feature_schema_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(legacy_feature_schema_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(settings_store_schema_tests
        settings_store_schema_tests.cpp
)
target_include_directories(settings_store_schema_tests PRIVATE ../src)
target_compile_features(settings_store_schema_tests PRIVATE cxx_std_23)
target_link_libraries(settings_store_schema_tests PRIVATE SQLite3::SQLite3)
set_target_properties(settings_store_schema_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(settings_store_schema_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(settings_store_schema_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(behavior_settings_tests
        behavior_settings_tests.cpp
        ../src/domain/prompt_template.cpp
)
target_include_directories(behavior_settings_tests PRIVATE ../src)
target_compile_features(behavior_settings_tests PRIVATE cxx_std_23)
set_target_properties(behavior_settings_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(behavior_settings_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(behavior_settings_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

# Pure logic only: the shared behaviour-settings cell needs no runtime, no SDK
# library and no GUI, so this target stays a header-only include.
add_executable(behavior_settings_state_tests
        behavior_settings_state_tests.cpp
)
target_include_directories(behavior_settings_state_tests PRIVATE ../src)
target_compile_features(behavior_settings_state_tests PRIVATE cxx_std_23)
set_target_properties(behavior_settings_state_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(behavior_settings_state_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(behavior_settings_state_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(chat_export_tests
        chat_export_tests.cpp
        ../src/application/chat_export.cpp
)
target_include_directories(chat_export_tests PRIVATE ../src)
target_compile_features(chat_export_tests PRIVATE cxx_std_23)
set_target_properties(chat_export_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(chat_export_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(chat_export_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(image_generation_codec_tests
        image_generation_codec_tests.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/image_generation_codec.cpp
        ../src/infrastructure/model_url_policy.cpp
)
target_include_directories(image_generation_codec_tests PRIVATE ../src)
target_compile_features(image_generation_codec_tests PRIVATE cxx_std_23)
set_target_properties(image_generation_codec_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(image_generation_codec_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(image_generation_codec_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(image_generation_tool_registry_tests
        image_generation_tool_registry_tests.cpp
        ../src/application/image_generation_tool_registry.cpp
        ../src/application/tool_settings_service.cpp
        ../src/domain/tool_settings.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/image_generation_codec.cpp
        ../src/infrastructure/model_url_policy.cpp
)
target_include_directories(image_generation_tool_registry_tests PRIVATE ../src)
target_compile_features(image_generation_tool_registry_tests PRIVATE cxx_std_23)
target_link_libraries(image_generation_tool_registry_tests PRIVATE
        HuxerUI::testing HuxerUI::huxerui)
set_target_properties(image_generation_tool_registry_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(image_generation_tool_registry_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(image_generation_tool_registry_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(image_understanding_codec_tests
        image_understanding_codec_tests.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/image_generation_codec.cpp
        ../src/infrastructure/image_understanding_codec.cpp
        ../src/infrastructure/model_url_policy.cpp
)
target_include_directories(image_understanding_codec_tests PRIVATE ../src)
target_compile_features(image_understanding_codec_tests PRIVATE cxx_std_23)
set_target_properties(image_understanding_codec_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(image_understanding_codec_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(image_understanding_codec_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(image_understanding_tool_registry_tests
        image_understanding_tool_registry_tests.cpp
        ../src/application/image_understanding_tool_registry.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/domain/prompt_template.cpp
        ../src/domain/tool_settings.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/image_generation_codec.cpp
        ../src/infrastructure/image_understanding_codec.cpp
        ../src/infrastructure/model_url_policy.cpp
)
target_include_directories(image_understanding_tool_registry_tests PRIVATE ../src)
target_compile_features(image_understanding_tool_registry_tests PRIVATE cxx_std_23)
target_link_libraries(image_understanding_tool_registry_tests PRIVATE
        HuxerUI::testing)
set_target_properties(image_understanding_tool_registry_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(image_understanding_tool_registry_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(image_understanding_tool_registry_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(workspace_image_readers_tests
        workspace_image_readers_tests.cpp
        ../src/application/ssh_workspace_service.cpp
        ../src/infrastructure/workspace_image_readers.cpp
)
target_include_directories(workspace_image_readers_tests PRIVATE ../src)
target_compile_features(workspace_image_readers_tests PRIVATE cxx_std_23)
target_link_libraries(workspace_image_readers_tests PRIVATE
        HuxerUI::testing HuxerUI::huxerui)
set_target_properties(workspace_image_readers_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(workspace_image_readers_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(workspace_image_readers_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(sse_decoder_tests
        sse_decoder_tests.cpp
        ../src/infrastructure/sse_decoder.cpp
)
target_include_directories(sse_decoder_tests PRIVATE ../src)
target_compile_features(sse_decoder_tests PRIVATE cxx_std_23)
set_target_properties(sse_decoder_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(sse_decoder_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(sse_decoder_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(model_store_selection_tests
        model_store_selection_tests.cpp
)
target_include_directories(model_store_selection_tests PRIVATE ../src)
target_compile_features(model_store_selection_tests PRIVATE cxx_std_23)
target_link_libraries(model_store_selection_tests PRIVATE SQLite3::SQLite3)
set_target_properties(model_store_selection_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(model_store_selection_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(model_store_selection_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(completion_pipeline_tests
        completion_pipeline_tests.cpp
        ../src/application/agent_run_progress_codec.cpp
        ../src/application/chat_session.cpp
        ../src/application/generation_controller.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/send_message.cpp
        ../src/application/tool_result_display_policy.cpp
        ../src/domain/input_attachment.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/completion_end_marker.cpp
        ../src/infrastructure/in_memory_conversation_store.cpp
        ../src/infrastructure/model_url_policy.cpp
        ../src/infrastructure/openai_chat_codec.cpp
)
target_include_directories(completion_pipeline_tests PRIVATE ../src)
target_compile_features(completion_pipeline_tests PRIVATE cxx_std_23)
set_target_properties(completion_pipeline_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(completion_pipeline_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(completion_pipeline_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(completion_protocol_codec_tests
        completion_protocol_codec_tests.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/completion_protocol_codec.cpp
        ../src/infrastructure/openai_chat_codec.cpp
)
target_include_directories(completion_protocol_codec_tests PRIVATE ../src)
target_compile_features(completion_protocol_codec_tests PRIVATE cxx_std_23)
set_target_properties(completion_protocol_codec_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(completion_protocol_codec_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(completion_protocol_codec_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(completion_retry_policy_tests
        completion_retry_policy_tests.cpp
)
target_include_directories(completion_retry_policy_tests PRIVATE ../src)
target_compile_features(completion_retry_policy_tests PRIVATE cxx_std_23)
target_link_libraries(completion_retry_policy_tests PRIVATE HuxerUI::testing)
set_target_properties(completion_retry_policy_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(completion_retry_policy_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(completion_retry_policy_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(http_response_policy_tests
        http_response_policy_tests.cpp
)
target_include_directories(http_response_policy_tests PRIVATE ../src)
target_compile_features(http_response_policy_tests PRIVATE cxx_std_23)
target_link_libraries(http_response_policy_tests PRIVATE HuxerUI::testing)
set_target_properties(http_response_policy_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(http_response_policy_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(http_response_policy_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(tool_read_only_policy_tests
        tool_read_only_policy_tests.cpp
        ../src/application/tool_read_only_policy.cpp
)
target_include_directories(tool_read_only_policy_tests PRIVATE ../src)
target_compile_features(tool_read_only_policy_tests PRIVATE cxx_std_23)
set_target_properties(tool_read_only_policy_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tool_read_only_policy_tests PRIVATE
            /W4 /WX /permissive-)
else ()
    target_compile_options(tool_read_only_policy_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(prompt_request_composer_tests
        prompt_request_composer_tests.cpp
        ../src/application/behavior_settings_repository.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/legacy_attachment_prompt_renderer.cpp
        ../src/application/prompt_request_composer.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/domain/input_attachment.cpp
        ../src/domain/model_config.cpp
        ../src/domain/prompt_renderer.cpp
        ../src/domain/prompt_template.cpp
)
target_include_directories(prompt_request_composer_tests PRIVATE ../src)
target_compile_features(prompt_request_composer_tests PRIVATE cxx_std_23)
target_link_libraries(prompt_request_composer_tests PRIVATE HuxerUI::testing)
set_target_properties(prompt_request_composer_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(prompt_request_composer_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(prompt_request_composer_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(output_settings_tests
        output_settings_tests.cpp
)
target_include_directories(output_settings_tests PRIVATE ../src)
target_compile_features(output_settings_tests PRIVATE cxx_std_23)
set_target_properties(output_settings_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(output_settings_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(output_settings_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(model_catalog_codec_tests
        model_catalog_codec_tests.cpp
        ../src/infrastructure/model_catalog_codec.cpp
)
target_include_directories(model_catalog_codec_tests PRIVATE ../src)
target_compile_features(model_catalog_codec_tests PRIVATE cxx_std_23)
set_target_properties(model_catalog_codec_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(model_catalog_codec_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(model_catalog_codec_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(error_log_tests
        error_log_tests.cpp
        ../src/application/error_log_policy.cpp
        ../src/application/error_log_redactor.cpp
)
target_include_directories(error_log_tests PRIVATE ../src)
target_compile_features(error_log_tests PRIVATE cxx_std_23)
set_target_properties(error_log_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(error_log_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(error_log_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(storage_stats_tests
        storage_stats_tests.cpp
        ../src/application/storage_stats.cpp
)
target_include_directories(storage_stats_tests PRIVATE ../src)
target_compile_features(storage_stats_tests PRIVATE cxx_std_23)
set_target_properties(storage_stats_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(storage_stats_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(storage_stats_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(archive_safety_tests
        archive_safety_tests.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/archive_validation.cpp
        ../src/infrastructure/linecode_zip.cpp
)
target_include_directories(archive_safety_tests PRIVATE ../src)
target_compile_features(archive_safety_tests PRIVATE cxx_std_23)
target_link_libraries(archive_safety_tests PRIVATE
        HuxerUI::huxerui
        ZLIB::ZLIB
)
set_target_properties(archive_safety_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(archive_safety_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(archive_safety_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(archive_transaction_tests
        archive_transaction_tests.cpp
        ../src/application/data_archive.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/archive_validation.cpp
        ../src/infrastructure/hux_data_archive_service.cpp
        ../src/infrastructure/linecode_zip.cpp
)
target_include_directories(archive_transaction_tests PRIVATE ../src)
target_compile_features(archive_transaction_tests PRIVATE cxx_std_23)
target_link_libraries(archive_transaction_tests PRIVATE
        HuxerUI::testing
        ZLIB::ZLIB
)
set_target_properties(archive_transaction_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(archive_transaction_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(archive_transaction_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(archive_redaction_tests
        archive_redaction_tests.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/archive_redaction.cpp
)
target_include_directories(archive_redaction_tests PRIVATE ../src)
target_compile_features(archive_redaction_tests PRIVATE cxx_std_23)
set_target_properties(archive_redaction_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(archive_redaction_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(archive_redaction_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(sqlite_archive_redaction_tests
        sqlite_archive_redaction_tests.cpp
        ../src/domain/model_config.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/archive_redaction.cpp
        ../src/infrastructure/archive_validation.cpp
        ../src/infrastructure/legacy_feature_schema.cpp
        ../src/infrastructure/legacy_project_schema.cpp
        ../src/infrastructure/linecode_zip.cpp
        ../src/infrastructure/sqlite_archive_database.cpp
)
target_include_directories(sqlite_archive_redaction_tests PRIVATE ../src)
target_compile_features(sqlite_archive_redaction_tests PRIVATE cxx_std_23)
huxerui_use_library(sqlite_archive_redaction_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(sqlite_archive_redaction_tests PRIVATE
        HuxerUI::testing
        SQLite3::SQLite3
        ZLIB::ZLIB)
set_target_properties(sqlite_archive_redaction_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(sqlite_archive_redaction_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(sqlite_archive_redaction_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(tutorial_markdown_parser_tests
        tutorial_markdown_parser_tests.cpp
        ../src/infrastructure/tutorial_markdown_parser.cpp
)
target_include_directories(tutorial_markdown_parser_tests PRIVATE ../src)
target_compile_features(tutorial_markdown_parser_tests PRIVATE cxx_std_23)
set_target_properties(tutorial_markdown_parser_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tutorial_markdown_parser_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(tutorial_markdown_parser_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(streaming_markdown_chunks_tests
        streaming_markdown_chunks_tests.cpp
        ../src/presentation/streaming_markdown_chunks.cpp
        ../src/infrastructure/tutorial_markdown_parser.cpp
)
target_include_directories(streaming_markdown_chunks_tests PRIVATE ../src)
target_compile_features(streaming_markdown_chunks_tests PRIVATE cxx_std_23)
if (MSVC)
    target_compile_options(streaming_markdown_chunks_tests PRIVATE /W4 /WX /permissive-)
else ()
    target_compile_options(streaming_markdown_chunks_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(tutorial_table_layout_tests
        tutorial_table_layout_tests.cpp
        ../src/presentation/components/tutorial_table_layout.cpp
)
target_include_directories(tutorial_table_layout_tests PRIVATE ../src)
target_compile_features(tutorial_table_layout_tests PRIVATE cxx_std_23)
target_link_libraries(tutorial_table_layout_tests PRIVATE
        HuxerUI::testing HuxerUI::huxerui)
set_target_properties(tutorial_table_layout_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tutorial_table_layout_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(tutorial_table_layout_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(mcp_execution_settings_tests
        mcp_execution_settings_tests.cpp
)
target_include_directories(mcp_execution_settings_tests PRIVATE ../src)
target_compile_features(mcp_execution_settings_tests PRIVATE cxx_std_23)
set_target_properties(mcp_execution_settings_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(mcp_execution_settings_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(mcp_execution_settings_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(tool_settings_tests
        tool_settings_tests.cpp
        ../src/domain/tool_settings.cpp
        ../src/application/tool_settings_service.cpp
        ../src/infrastructure/tool_settings_codec.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(tool_settings_tests PRIVATE ../src)
target_compile_features(tool_settings_tests PRIVATE cxx_std_23)
target_link_libraries(tool_settings_tests PRIVATE HuxerUI::huxerui)
set_target_properties(tool_settings_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tool_settings_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(tool_settings_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(ssh_settings_tests
        ssh_settings_tests.cpp
        ../src/domain/ssh_config.cpp
        ../src/infrastructure/ssh_config_codec.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(ssh_settings_tests PRIVATE ../src)
target_compile_features(ssh_settings_tests PRIVATE cxx_std_23)
target_link_libraries(ssh_settings_tests PRIVATE HuxerUI::huxerui)
set_target_properties(ssh_settings_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(ssh_settings_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(ssh_settings_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()

add_executable(memory_store_contract_tests
        memory_store_contract_tests.cpp
        ../src/domain/memory.cpp
)
target_include_directories(memory_store_contract_tests PRIVATE ../src)
target_compile_features(memory_store_contract_tests PRIVATE cxx_std_23)
target_link_libraries(memory_store_contract_tests PRIVATE SQLite3::SQLite3)
set_target_properties(memory_store_contract_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(memory_store_contract_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(memory_store_contract_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(sqlite_memory_store_tests
        sqlite_memory_store_tests.cpp
        ../src/domain/memory.cpp
        ../src/domain/memory_rag.cpp
        ../src/infrastructure/sqlite_memory_store.cpp
)
target_include_directories(sqlite_memory_store_tests PRIVATE ../src)
target_compile_features(sqlite_memory_store_tests PRIVATE cxx_std_23)
huxerui_use_library(sqlite_memory_store_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(sqlite_memory_store_tests PRIVATE
        HuxerUI::testing)
set_target_properties(sqlite_memory_store_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(sqlite_memory_store_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(sqlite_memory_store_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(memory_rag_tests
        memory_rag_tests.cpp
        ../src/domain/memory.cpp
        ../src/domain/memory_rag.cpp
        ../src/application/memory_prompt_renderer.cpp
)
target_include_directories(memory_rag_tests PRIVATE ../src)
target_compile_features(memory_rag_tests PRIVATE cxx_std_23)
set_target_properties(memory_rag_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(memory_rag_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(memory_rag_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(memory_context_service_tests
        memory_context_service_tests.cpp
        ../src/domain/memory.cpp
        ../src/domain/memory_rag.cpp
        ../src/application/memory_context_service.cpp
        ../src/application/memory_prompt_renderer.cpp
)
target_include_directories(memory_context_service_tests PRIVATE ../src)
target_compile_features(memory_context_service_tests PRIVATE cxx_std_23)
target_link_libraries(memory_context_service_tests PRIVATE HuxerUI::testing)
set_target_properties(memory_context_service_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(memory_context_service_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(memory_context_service_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(memory_conversation_snapshot_tests
        memory_conversation_snapshot_tests.cpp
        ../src/application/memory_conversation_snapshot.cpp
        ../src/domain/memory.cpp
)
target_include_directories(memory_conversation_snapshot_tests PRIVATE ../src)
target_compile_features(memory_conversation_snapshot_tests PRIVATE cxx_std_23)
target_link_libraries(memory_conversation_snapshot_tests PRIVATE
        GTest::gtest_main)
set_target_properties(memory_conversation_snapshot_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(memory_conversation_snapshot_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(memory_conversation_snapshot_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(chat_generation_state_tests
        chat_generation_state_tests.cpp
        ../src/presentation/components/chat_generation_state.cpp
)
target_include_directories(chat_generation_state_tests PRIVATE ../src)
target_compile_features(chat_generation_state_tests PRIVATE cxx_std_23)
target_link_libraries(chat_generation_state_tests PRIVATE GTest::gtest_main)
set_target_properties(chat_generation_state_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(chat_generation_state_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(chat_generation_state_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(extension_store_tests
        extension_store_tests.cpp
        ../src/domain/extension_config.cpp
        ../src/infrastructure/extension_config_codec.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(extension_store_tests PRIVATE ../src)
target_compile_features(extension_store_tests PRIVATE cxx_std_23)
set_target_properties(extension_store_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(extension_store_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(extension_store_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(terminal_provider_contract_tests
        terminal_provider_contract_tests.cpp
)
target_include_directories(terminal_provider_contract_tests PRIVATE ../src)
target_compile_features(terminal_provider_contract_tests PRIVATE cxx_std_23)
target_link_libraries(terminal_provider_contract_tests PRIVATE SQLite3::SQLite3)
set_target_properties(terminal_provider_contract_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(terminal_provider_contract_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(terminal_provider_contract_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(mcp_protocol_codec_tests
        mcp_protocol_codec_tests.cpp
        ../src/domain/extension_config.cpp
        ../src/infrastructure/mcp_protocol_codec.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(mcp_protocol_codec_tests PRIVATE ../src)
target_compile_features(mcp_protocol_codec_tests PRIVATE cxx_std_23)
set_target_properties(mcp_protocol_codec_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(mcp_protocol_codec_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(mcp_protocol_codec_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(mcp_response_reader_tests
        mcp_response_reader_tests.cpp
        ../src/domain/extension_config.cpp
        ../src/infrastructure/extension_config_codec.cpp
        ../src/infrastructure/hux_mcp_tool_catalog.cpp
        ../src/infrastructure/hux_mcp_tool_invoker.cpp
        ../src/infrastructure/mcp_protocol_codec.cpp
        ../src/infrastructure/mcp_response_reader.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(mcp_response_reader_tests PRIVATE ../src)
target_compile_features(mcp_response_reader_tests PRIVATE cxx_std_23)
target_link_libraries(mcp_response_reader_tests PRIVATE HuxerUI::huxerui)
set_target_properties(mcp_response_reader_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(mcp_response_reader_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(mcp_response_reader_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(mcp_extension_tool_registry_tests
        mcp_extension_tool_registry_tests.cpp
        ../src/application/mcp_extension_tool_registry.cpp
        ../src/domain/extension_config.cpp
        ../src/infrastructure/json_mcp_tool_schema_policy.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(mcp_extension_tool_registry_tests PRIVATE ../src)
target_compile_features(mcp_extension_tool_registry_tests PRIVATE cxx_std_23)
target_link_libraries(mcp_extension_tool_registry_tests PRIVATE
        HuxerUI::huxerui)
set_target_properties(mcp_extension_tool_registry_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(mcp_extension_tool_registry_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(mcp_extension_tool_registry_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(mcp_completion_loop_tests
        mcp_completion_loop_tests.cpp
        ../src/application/mcp_completion_loop.cpp
        ../src/application/mcp_extension_tool_registry.cpp
        ../src/application/tool_permission_service.cpp
        ../src/application/tool_read_only_policy.cpp
        ../src/application/tool_result_display_policy.cpp
        ../src/domain/extension_config.cpp
        ../src/infrastructure/json_mcp_tool_schema_policy.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(mcp_completion_loop_tests PRIVATE ../src)
target_compile_features(mcp_completion_loop_tests PRIVATE cxx_std_23)
target_link_libraries(mcp_completion_loop_tests PRIVATE HuxerUI::testing)
set_target_properties(mcp_completion_loop_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(mcp_completion_loop_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(mcp_completion_loop_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(skill_services_tests
        skill_services_tests.cpp
        ../src/application/skill_management_service.cpp
        ../src/application/skill_repository.cpp
        ../src/domain/skill.cpp
        ../src/domain/skill_hub.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/hux_skill_files.cpp
        ../src/infrastructure/hux_skill_hub_gateway.cpp
        ../src/infrastructure/hux_skill_hub_session_gateway.cpp
        ../src/infrastructure/linecode_zip.cpp
        ../src/infrastructure/skill_hub_codec.cpp
)
target_include_directories(skill_services_tests PRIVATE ../src)
target_compile_features(skill_services_tests PRIVATE cxx_std_23)
target_link_libraries(skill_services_tests PRIVATE
        HuxerUI::testing
        ZLIB::ZLIB)
set_target_properties(skill_services_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(skill_services_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(skill_services_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(skill_hub_route_tests
        skill_hub_route_tests.cpp
)
target_include_directories(skill_hub_route_tests PRIVATE ../src)
target_compile_features(skill_hub_route_tests PRIVATE cxx_std_23)
set_target_properties(skill_hub_route_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(skill_hub_route_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(skill_hub_route_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(sqlite_skill_record_store_tests
        sqlite_skill_record_store_tests.cpp
        ../src/domain/skill.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/legacy_feature_schema.cpp
        ../src/infrastructure/sqlite_skill_record_store.cpp
)
target_include_directories(sqlite_skill_record_store_tests PRIVATE ../src)
target_compile_features(sqlite_skill_record_store_tests PRIVATE cxx_std_23)
huxerui_use_library(sqlite_skill_record_store_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(sqlite_skill_record_store_tests PRIVATE
        HuxerUI::testing)
set_target_properties(sqlite_skill_record_store_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(sqlite_skill_record_store_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(sqlite_skill_record_store_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(project_workspace_service_tests
        project_workspace_service_tests.cpp
        ../src/application/project_workspace_service.cpp
        ../src/domain/model_config.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/archive_redaction.cpp
        ../src/infrastructure/archive_validation.cpp
        ../src/infrastructure/hux_workspace_file_store.cpp
        ../src/infrastructure/legacy_feature_schema.cpp
        ../src/infrastructure/legacy_project_schema.cpp
        ../src/infrastructure/linecode_zip.cpp
        ../src/infrastructure/sqlite_archive_database.cpp
        ../src/infrastructure/sqlite_project_catalog_store.cpp
)
target_include_directories(project_workspace_service_tests PRIVATE ../src)
target_compile_features(project_workspace_service_tests PRIVATE cxx_std_23)
huxerui_use_library(project_workspace_service_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(project_workspace_service_tests PRIVATE
        HuxerUI::testing
        ZLIB::ZLIB)
set_target_properties(project_workspace_service_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(project_workspace_service_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(project_workspace_service_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(runtime_tool_registry_tests
        runtime_tool_registry_tests.cpp
        ../src/application/composite_tool_registry.cpp
        ../src/application/terminal_provider_tool_registry.cpp
        ../src/application/mcp_execution_settings.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(runtime_tool_registry_tests PRIVATE ../src)
target_compile_features(runtime_tool_registry_tests PRIVATE cxx_std_23)
target_link_libraries(runtime_tool_registry_tests PRIVATE HuxerUI::testing)
set_target_properties(runtime_tool_registry_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(runtime_tool_registry_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(runtime_tool_registry_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(tool_approval_presentation_tests
        tool_approval_presentation_tests.cpp
        ../src/presentation/tool_approval_presentation.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(tool_approval_presentation_tests PRIVATE ../src)
target_compile_features(tool_approval_presentation_tests PRIVATE cxx_std_23)
set_target_properties(tool_approval_presentation_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tool_approval_presentation_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(tool_approval_presentation_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(tool_permission_service_tests
        tool_permission_service_tests.cpp
        ../src/application/tool_permission_service.cpp
        ../src/application/tool_read_only_policy.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(tool_permission_service_tests PRIVATE ../src)
target_compile_features(tool_permission_service_tests PRIVATE cxx_std_23)
target_link_libraries(tool_permission_service_tests PRIVATE HuxerUI::testing)
set_target_properties(tool_permission_service_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tool_permission_service_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(tool_permission_service_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(chat_mode_service_tests
        chat_mode_service_tests.cpp
        ../src/application/chat_mode_service.cpp
        ../src/application/tool_permission_service.cpp
        ../src/application/tool_read_only_policy.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(chat_mode_service_tests PRIVATE ../src)
target_compile_features(chat_mode_service_tests PRIVATE cxx_std_23)
target_link_libraries(chat_mode_service_tests PRIVATE HuxerUI::testing)
set_target_properties(chat_mode_service_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(chat_mode_service_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(chat_mode_service_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(slash_command_catalog_tests
        slash_command_catalog_tests.cpp
        ../src/application/slash_command_catalog.cpp
)
target_include_directories(slash_command_catalog_tests PRIVATE ../src)
target_compile_features(slash_command_catalog_tests PRIVATE cxx_std_23)
if (MSVC)
    target_compile_options(slash_command_catalog_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(slash_command_catalog_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(attachment_backend_tests
        attachment_backend_tests.cpp
        ../src/application/chat_session.cpp
        ../src/application/composer_image_input.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/legacy_attachment_prompt_renderer.cpp
        ../src/application/send_message.cpp
        ../src/domain/input_attachment.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/attachment_json_codec.cpp
        ../src/infrastructure/in_memory_conversation_store.cpp
)
target_include_directories(attachment_backend_tests PRIVATE ../src)
target_compile_features(attachment_backend_tests PRIVATE cxx_std_23)
set_target_properties(attachment_backend_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(attachment_backend_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(attachment_backend_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(pending_message_queue_tests
  pending_message_queue_tests.cpp
  ${PROJECT_SOURCE_DIR}/../src/application/pending_message_queue.cpp
  ${PROJECT_SOURCE_DIR}/../src/application/legacy_attachment_policy.cpp
  ${PROJECT_SOURCE_DIR}/../src/application/send_message.cpp
  ${PROJECT_SOURCE_DIR}/../src/domain/input_attachment.cpp
)
target_include_directories(pending_message_queue_tests PRIVATE ${PROJECT_SOURCE_DIR}/../src)
target_compile_features(pending_message_queue_tests PRIVATE cxx_std_23)
target_compile_options(pending_message_queue_tests PRIVATE -Wall -Wextra -Werror)
add_executable(sqlite_conversation_attachment_tests
        sqlite_conversation_attachment_tests.cpp
        ../src/domain/compaction_progress.cpp
        ../src/domain/conversation_resume_sanitizer.cpp
        ../src/domain/input_attachment.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/attachment_json_codec.cpp
        ../src/infrastructure/sqlite_conversation_store.cpp
)
target_include_directories(sqlite_conversation_attachment_tests PRIVATE ../src)
target_compile_features(sqlite_conversation_attachment_tests PRIVATE cxx_std_23)
huxerui_use_library(sqlite_conversation_attachment_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(sqlite_conversation_attachment_tests PRIVATE
        HuxerUI::testing
        SQLite3::SQLite3)
set_target_properties(sqlite_conversation_attachment_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(sqlite_conversation_attachment_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(sqlite_conversation_attachment_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(chat_timeline_presentation_tests
        chat_timeline_presentation_tests.cpp
        ../src/application/agent_result_registry.cpp
        ../src/application/agent_run_progress_codec.cpp
        ../src/application/tool_text_catalog.cpp
        ../src/application/tool_result_display_policy.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/presentation/chat_timeline_presentation.cpp)
target_include_directories(chat_timeline_presentation_tests PRIVATE ../src)
target_compile_features(chat_timeline_presentation_tests PRIVATE cxx_std_23)
if (MSVC)
    target_compile_options(chat_timeline_presentation_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(chat_timeline_presentation_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(ssh_backend_tests
        ssh_backend_tests.cpp
        ../src/application/execution_mode_project_workspace.cpp
        ../src/application/ssh_project_workspace.cpp
        ../src/application/ssh_runtime_service.cpp
        ../src/application/ssh_tool_registry.cpp
        ../src/application/tool_text_catalog.cpp
        ../src/application/ssh_workspace_service.cpp
        ../src/domain/ssh_config.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(ssh_backend_tests PRIVATE ../src)
target_compile_features(ssh_backend_tests PRIVATE cxx_std_23)
target_link_libraries(ssh_backend_tests PRIVATE HuxerUI::testing)
set_target_properties(ssh_backend_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(ssh_backend_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(ssh_backend_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(ssh_protocol_tests
        ssh_protocol_tests.cpp
        ../src/domain/ssh_config.cpp
        ../src/infrastructure/hux_known_hosts_store.cpp
        ../src/infrastructure/libssh2_transport.cpp
)
target_include_directories(ssh_protocol_tests PRIVATE ../src)
target_compile_features(ssh_protocol_tests PRIVATE cxx_std_23)
target_link_libraries(ssh_protocol_tests PRIVATE HuxerUI::huxerui)
set_target_properties(ssh_protocol_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(ssh_protocol_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(ssh_protocol_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
include("${CMAKE_CURRENT_SOURCE_DIR}/../cmake/LineCodeSsh.cmake")
linecode_enable_ssh(ssh_protocol_tests)

if (UNIX AND NOT APPLE)
    find_program(LINECODE_SSHD_EXECUTABLE sshd)
    find_program(LINECODE_SSH_KEYGEN_EXECUTABLE ssh-keygen)
    if (LINECODE_SSHD_EXECUTABLE AND LINECODE_SSH_KEYGEN_EXECUTABLE)
        # The only non-GoogleTest entry: a real sshd fixture wrapping
        # `ssh_protocol_tests`. Recorded for the aggregated runner at the bottom
        # of this file, which executes it as a separate step.
        set(LINECODE_SSH_FIXTURE_COMMAND
                bash
                "${CMAKE_CURRENT_SOURCE_DIR}/run_ssh_protocol_fixture.sh"
                "$<TARGET_FILE:ssh_protocol_tests>"
                "${LINECODE_SSHD_EXECUTABLE}"
                "${LINECODE_SSH_KEYGEN_EXECUTABLE}")
        find_program(LINECODE_TIMEOUT_EXECUTABLE timeout)
        if (LINECODE_TIMEOUT_EXECUTABLE)
            # Keep the 45s guard the CTest registration used to carry.
            list(PREPEND LINECODE_SSH_FIXTURE_COMMAND
                    "${LINECODE_TIMEOUT_EXECUTABLE}" 45)
        endif ()
    endif ()
endif ()

add_executable(skill_hub_reading_settings_tests
        skill_hub_reading_settings_tests.cpp
        ../src/application/skill_hub_reading_settings.cpp)
target_include_directories(skill_hub_reading_settings_tests PRIVATE ../src)
target_compile_features(skill_hub_reading_settings_tests PRIVATE cxx_std_23)
target_link_libraries(skill_hub_reading_settings_tests PRIVATE
        HuxerUI::testing HuxerUI::huxerui)
if (MSVC)
    target_compile_options(skill_hub_reading_settings_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(skill_hub_reading_settings_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(user_agreement_tests
        user_agreement_tests.cpp
        ../src/application/user_agreement.cpp)
target_include_directories(user_agreement_tests PRIVATE ../src)
target_compile_features(user_agreement_tests PRIVATE cxx_std_23)
target_link_libraries(user_agreement_tests PRIVATE
        HuxerUI::testing HuxerUI::huxerui)
if (MSVC)
    target_compile_options(user_agreement_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(user_agreement_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(theme_settings_migration_tests
        theme_settings_migration_tests.cpp
        ../src/application/theme_settings_migration.cpp
)
target_include_directories(theme_settings_migration_tests PRIVATE ../src)
target_compile_features(theme_settings_migration_tests PRIVATE cxx_std_23)
target_link_libraries(theme_settings_migration_tests PRIVATE
        HuxerUI::huxerui HuxerUI::testing)
if (MSVC)
    target_compile_options(theme_settings_migration_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(theme_settings_migration_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(legacy_upgrade_integration_tests
        legacy_upgrade_integration_tests.cpp
        ../src/application/theme_settings.cpp
        ../src/application/theme_settings_migration.cpp
        ../src/domain/model_config.cpp
        ../src/domain/theme_palette.cpp
        ../src/infrastructure/sqlite_model_store.cpp
        ../src/infrastructure/sqlite_settings_store.cpp
        ../src/infrastructure/theme_file_settings_store.cpp
)
target_include_directories(legacy_upgrade_integration_tests PRIVATE ../src)
target_compile_features(legacy_upgrade_integration_tests PRIVATE cxx_std_23)
huxerui_use_library(legacy_upgrade_integration_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(legacy_upgrade_integration_tests PRIVATE
        HuxerUI::testing
        SQLite3::SQLite3)
set_target_properties(legacy_upgrade_integration_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(legacy_upgrade_integration_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(legacy_upgrade_integration_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(markdown_link_policy_tests
        markdown_link_policy_tests.cpp
        ../src/presentation/markdown_link_policy.cpp)
target_include_directories(markdown_link_policy_tests PRIVATE ../src)
target_compile_features(markdown_link_policy_tests PRIVATE cxx_std_23)
target_link_libraries(markdown_link_policy_tests PRIVATE HuxerUI::huxerui)
if (MSVC)
    target_compile_options(markdown_link_policy_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(markdown_link_policy_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(agent_extension_draft_tests
        agent_extension_draft_tests.cpp
        ../src/application/agent_extension_draft.cpp
        ../src/domain/extension_config.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/json_agent_extension_draft_codec.cpp)
target_include_directories(agent_extension_draft_tests PRIVATE ../src)
target_compile_features(agent_extension_draft_tests PRIVATE cxx_std_23)
target_link_libraries(agent_extension_draft_tests PRIVATE
        HuxerUI::testing HuxerUI::huxerui)
if (MSVC)
    target_compile_options(agent_extension_draft_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(agent_extension_draft_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(todo_tool_tests
        todo_tool_tests.cpp
        ../src/application/todo_tool_registry.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(todo_tool_tests PRIVATE ../src)
target_compile_features(todo_tool_tests PRIVATE cxx_std_23)
target_link_libraries(todo_tool_tests PRIVATE HuxerUI::testing)
set_target_properties(todo_tool_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(todo_tool_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(todo_tool_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(memory_tool_tests
        memory_tool_tests.cpp
        ../src/application/memory_tool_registry.cpp
        ../src/domain/memory.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(memory_tool_tests PRIVATE ../src)
target_compile_features(memory_tool_tests PRIVATE cxx_std_23)
target_link_libraries(memory_tool_tests PRIVATE HuxerUI::testing)
set_target_properties(memory_tool_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(memory_tool_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(memory_tool_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(web_tool_tests
        web_tool_tests.cpp
        ../src/application/web_tool_registry.cpp
        ../src/domain/tool_settings.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/hux_web_tools_gateway.cpp
        ../src/infrastructure/web_tools_codec.cpp
)
target_include_directories(web_tool_tests PRIVATE ../src)
target_compile_features(web_tool_tests PRIVATE cxx_std_23)
target_link_libraries(web_tool_tests PRIVATE HuxerUI::testing HuxerUI::huxerui)
set_target_properties(web_tool_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(web_tool_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(web_tool_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(file_tool_tests
        file_tool_tests.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/file_observation_store.cpp
        ../src/application/file_tool_registry.cpp
        ../src/application/tool_file_access.cpp
        ../src/application/tool_file_path_policy.cpp
        ../src/application/tool_text_catalog.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(file_tool_tests PRIVATE ../src)
target_compile_definitions(file_tool_tests PRIVATE
        LINECODE_STRING_RESOURCES_DIR="${CMAKE_CURRENT_SOURCE_DIR}/../resources/strings")
target_compile_features(file_tool_tests PRIVATE cxx_std_23)
target_link_libraries(file_tool_tests PRIVATE HuxerUI::testing)
set_target_properties(file_tool_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(file_tool_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(file_tool_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(file_observation_tests
        file_observation_tests.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/file_observation_store.cpp
)
target_include_directories(file_observation_tests PRIVATE ../src)
target_compile_features(file_observation_tests PRIVATE cxx_std_23)
set_target_properties(file_observation_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(file_observation_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(file_observation_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(context_usage_tests
        context_usage_tests.cpp
        ../src/domain/context_usage.cpp
        ../src/domain/input_attachment.cpp
)
target_include_directories(context_usage_tests PRIVATE ../src)
target_compile_features(context_usage_tests PRIVATE cxx_std_23)
set_target_properties(context_usage_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(context_usage_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(context_usage_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(context_compaction_tests
        context_compaction_tests.cpp
        ../src/application/context_compaction.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/domain/context_usage.cpp
        ../src/domain/input_attachment.cpp
        ../src/domain/model_config.cpp
        ../src/domain/prompt_renderer.cpp
        ../src/domain/prompt_template.cpp
)
target_include_directories(context_compaction_tests PRIVATE ../src)
target_compile_features(context_compaction_tests PRIVATE cxx_std_23)
target_link_libraries(context_compaction_tests PRIVATE HuxerUI::testing)
set_target_properties(context_compaction_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(context_compaction_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(context_compaction_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(diff_lines_tests
        diff_lines_tests.cpp
        ../src/domain/diff_lines.cpp
)
target_include_directories(diff_lines_tests PRIVATE ../src)
target_compile_features(diff_lines_tests PRIVATE cxx_std_23)
set_target_properties(diff_lines_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(diff_lines_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(diff_lines_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(diff_store_tests
        diff_store_tests.cpp
        ../src/infrastructure/sqlite_diff_store.cpp
        ../src/infrastructure/sqlite_diff_file_restorer.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(diff_store_tests PRIVATE ../src)
target_compile_features(diff_store_tests PRIVATE cxx_std_23)
huxerui_use_library(diff_store_tests
        TARGET HuxerUI::SQLite
        URL "https://github.com/HuxerUI/Lib-SQLite.git"
        COMMIT "0b316df41cbd88b212e6aeead326196e3db6c96a"
)
target_link_libraries(diff_store_tests PRIVATE HuxerUI::testing)
set_target_properties(diff_store_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(diff_store_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(diff_store_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(diff_review_service_tests
        diff_review_service_tests.cpp
        ../src/application/diff_review_service.cpp
)
target_include_directories(diff_review_service_tests PRIVATE ../src)
target_compile_features(diff_review_service_tests PRIVATE cxx_std_23)
target_link_libraries(diff_review_service_tests PRIVATE HuxerUI::testing)
set_target_properties(diff_review_service_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(diff_review_service_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(diff_review_service_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(agent_pipeline_tests
        agent_pipeline_tests.cpp
        ../src/domain/agent_pipeline.cpp
)
target_include_directories(agent_pipeline_tests PRIVATE ../src)
target_compile_features(agent_pipeline_tests PRIVATE cxx_std_23)
set_target_properties(agent_pipeline_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(agent_pipeline_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(agent_pipeline_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(sub_agent_runner_tests
        sub_agent_runner_tests.cpp
        ../src/application/agent_result_registry.cpp
        ../src/application/agent_run_progress_codec.cpp
        ../src/application/sub_agent_environment.cpp
        ../src/application/tool_permission_service.cpp
        ../src/application/tool_read_only_policy.cpp
        ../src/application/tool_review_broker.cpp
        ../src/application/tool_text_catalog.cpp
        ../src/domain/agent_pipeline.cpp
        ../src/application/sub_agent_runner.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/application/skill_repository.cpp
        ../src/domain/skill.cpp
        ../src/domain/prompt_renderer.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/domain/prompt_template.cpp
)
target_include_directories(sub_agent_runner_tests PRIVATE ../src)
target_compile_features(sub_agent_runner_tests PRIVATE cxx_std_23)
target_link_libraries(sub_agent_runner_tests PRIVATE
        HuxerUI::testing
        GTest::gtest_main
)
set_target_properties(sub_agent_runner_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(sub_agent_runner_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(sub_agent_runner_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(agent_tool_tests
        agent_tool_tests.cpp
        ../src/application/agent_result_registry.cpp
        ../src/application/agent_run_progress_codec.cpp
        ../src/application/agent_tool_registry.cpp
        ../src/application/tool_text_catalog.cpp
        ../src/domain/agent_pipeline.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(agent_tool_tests PRIVATE ../src)
target_compile_features(agent_tool_tests PRIVATE cxx_std_23)
target_link_libraries(agent_tool_tests PRIVATE HuxerUI::testing)
set_target_properties(agent_tool_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(agent_tool_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(agent_tool_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(auto_compaction_tests
        auto_compaction_tests.cpp
        ../src/application/auto_compaction_service.cpp
        ../src/application/chat_session.cpp
        ../src/application/context_compaction.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/application/send_message.cpp
        ../src/infrastructure/in_memory_conversation_store.cpp
        ../src/domain/compaction_progress.cpp
        ../src/domain/context_usage.cpp
        ../src/domain/input_attachment.cpp
        ../src/domain/model_config.cpp
        ../src/domain/prompt_renderer.cpp
        ../src/domain/prompt_template.cpp
        ../src/presentation/compaction_progress_presentation.cpp
)
target_include_directories(auto_compaction_tests PRIVATE ../src)
target_compile_features(auto_compaction_tests PRIVATE cxx_std_23)
target_link_libraries(auto_compaction_tests PRIVATE HuxerUI::testing)
set_target_properties(auto_compaction_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(auto_compaction_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(auto_compaction_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(tool_loop_compactor_tests
        tool_loop_compactor_tests.cpp
        ../src/application/tool_loop_compactor.cpp
        ../src/application/chat_session.cpp
        ../src/application/send_message.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/infrastructure/in_memory_conversation_store.cpp
        ../src/application/auto_compaction_service.cpp
        ../src/application/context_compaction.cpp
        ../src/application/file_content_hash.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/domain/compaction_progress.cpp
        ../src/domain/context_usage.cpp
        ../src/domain/input_attachment.cpp
        ../src/domain/model_config.cpp
        ../src/domain/prompt_template.cpp
        ../src/domain/prompt_renderer.cpp
)
target_include_directories(tool_loop_compactor_tests PRIVATE ../src)
target_compile_features(tool_loop_compactor_tests PRIVATE cxx_std_23)
target_link_libraries(tool_loop_compactor_tests PRIVATE HuxerUI::testing)
set_target_properties(tool_loop_compactor_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tool_loop_compactor_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(tool_loop_compactor_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
# Port tests for the streamed-reasoning inline emphasis parser
# (`cn.lineai.ui.theme.InlineEmphasisParser`).
add_executable(inline_emphasis_tests
        inline_emphasis_tests.cpp
        ../src/domain/inline_emphasis.cpp
)
target_include_directories(inline_emphasis_tests PRIVATE ../src)
target_compile_features(inline_emphasis_tests PRIVATE cxx_std_23)
set_target_properties(inline_emphasis_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(inline_emphasis_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(inline_emphasis_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
# Bare-URL recognition equivalent to `Linkify.addLinks(..., WEB_URLS)`.
add_executable(markdown_linkify_tests
        markdown_linkify_tests.cpp
        ../src/presentation/markdown_linkify.cpp
)
target_include_directories(markdown_linkify_tests PRIVATE ../src)
target_compile_features(markdown_linkify_tests PRIVATE cxx_std_23)
set_target_properties(markdown_linkify_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(markdown_linkify_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(markdown_linkify_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
# Quotes and list items keep their block-level children.
add_executable(tutorial_nested_blocks_tests
        tutorial_nested_blocks_tests.cpp
        ../src/infrastructure/tutorial_markdown_parser.cpp
)
target_include_directories(tutorial_nested_blocks_tests PRIVATE ../src)
target_compile_features(tutorial_nested_blocks_tests PRIVATE cxx_std_23)
set_target_properties(tutorial_nested_blocks_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tutorial_nested_blocks_tests PRIVATE
            /W4 /permissive-)
else ()
    target_compile_options(tutorial_nested_blocks_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(generation_retry_tests
        generation_retry_tests.cpp
        ../src/application/agent_run_progress_codec.cpp
        ../src/application/chat_session.cpp
        ../src/application/generation_controller.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/send_message.cpp
        ../src/application/tool_result_display_policy.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/in_memory_conversation_store.cpp
        ../src/domain/compaction_progress.cpp
        ../src/domain/context_usage.cpp
        ../src/domain/input_attachment.cpp
        ../src/domain/model_config.cpp
)
target_include_directories(generation_retry_tests PRIVATE ../src)
target_compile_features(generation_retry_tests PRIVATE cxx_std_23)
target_link_libraries(generation_retry_tests PRIVATE HuxerUI::testing)
set_target_properties(generation_retry_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(generation_retry_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(generation_retry_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(conversation_resume_sanitizer_tests
        conversation_resume_sanitizer_tests.cpp
        ../src/domain/conversation_resume_sanitizer.cpp
        ../src/infrastructure/archive_json.cpp
)
target_include_directories(conversation_resume_sanitizer_tests PRIVATE ../src)
target_compile_features(conversation_resume_sanitizer_tests PRIVATE cxx_std_23)
set_target_properties(conversation_resume_sanitizer_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(conversation_resume_sanitizer_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(conversation_resume_sanitizer_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(token_usage_tracker_tests
        token_usage_tracker_tests.cpp
)
target_include_directories(token_usage_tracker_tests PRIVATE ../src)
target_compile_features(token_usage_tracker_tests PRIVATE cxx_std_23)
target_link_libraries(token_usage_tracker_tests PRIVATE HuxerUI::testing)
set_target_properties(token_usage_tracker_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(token_usage_tracker_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(token_usage_tracker_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(custom_agent_tool_tests
        custom_agent_tool_tests.cpp
        ../src/application/agent_extension_tool_registry.cpp
        ../src/application/agent_run_progress_codec.cpp
        ../src/application/custom_agent_tool.cpp
        ../src/application/tool_text_catalog.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/domain/extension_config.cpp
)
target_include_directories(custom_agent_tool_tests PRIVATE ../src)
target_compile_features(custom_agent_tool_tests PRIVATE cxx_std_23)
target_link_libraries(custom_agent_tool_tests PRIVATE HuxerUI::testing)
set_target_properties(custom_agent_tool_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(custom_agent_tool_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(custom_agent_tool_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(legacy_archive_writer_tests
        legacy_archive_writer_tests.cpp
        ../src/infrastructure/archive_validation.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/infrastructure/linecode_zip.cpp
        ../src/domain/model_config.cpp
)
target_include_directories(legacy_archive_writer_tests PRIVATE ../src)
target_compile_features(legacy_archive_writer_tests PRIVATE cxx_std_23)
find_package(ZLIB REQUIRED)
target_link_libraries(legacy_archive_writer_tests PRIVATE HuxerUI::testing ZLIB::ZLIB)
set_target_properties(legacy_archive_writer_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(legacy_archive_writer_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(legacy_archive_writer_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
add_executable(tool_review_broker_tests
        tool_review_broker_tests.cpp
        ../src/application/tool_review_broker.cpp
        ../src/application/tool_review_coordinator.cpp
)
target_include_directories(tool_review_broker_tests PRIVATE ../src)
target_compile_features(tool_review_broker_tests PRIVATE cxx_std_23)
target_link_libraries(tool_review_broker_tests PRIVATE HuxerUI::testing)
set_target_properties(tool_review_broker_tests PROPERTIES
        CXX_STANDARD 23
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)
if (MSVC)
    target_compile_options(tool_review_broker_tests PRIVATE /W4 /permissive-)
else ()
    target_compile_options(tool_review_broker_tests PRIVATE
            -Wall -Wextra -Wpedantic -Werror)
endif ()
# ---------------------------------------------------------------------------
# GoogleTest runner
#
# Note for maintainers: this project no longer calls enable_testing(), so
# `ctest --test-dir <build>` reports "No tests were found!!!" and exits 0. That
# is CTest's behaviour for an empty project, not a passing suite -- run the
# target below instead. Do not "fix" a stale ctest invocation by trusting it.
#
# This project does not use CTest. Every target below already links
# GTest::gtest_main, so the suite is executed by running those binaries
# directly:
#
#     cmake --build build/tests-ninja --target run_tests
#
# Adding a test needs no registration: create `add_executable(<name>_tests ...)`
# in this file and the runner picks it up. Environment variables:
#   GTEST_FILTER              forwarded to every binary as --gtest_filter
#   LINECODE_TEST_VERBOSE=1   print full GoogleTest output instead of failures
# ---------------------------------------------------------------------------

# Working directories the old CTest registrations set explicitly. Everything
# else runs from the build directory, which is what CTest used as its default.
set(LINECODE_TEST_WORKING_DIRECTORY_tutorial_markdown_parser_tests
        "${CMAKE_CURRENT_SOURCE_DIR}/..")
set(LINECODE_TEST_WORKING_DIRECTORY_legacy_archive_writer_tests
        "${CMAKE_CURRENT_SOURCE_DIR}/..")

# Binaries that are not standalone tests: they need a harness to supply their
# arguments (`ssh_protocol_tests` expects six fixture arguments and is driven by
# `run_ssh_protocol_fixture.sh`, which the runner executes below).
set(LINECODE_TEST_FIXTURE_ONLY_EXECUTABLES ssh_protocol_tests)

get_property(LINECODE_ALL_TARGETS DIRECTORY PROPERTY BUILDSYSTEM_TARGETS)
set(LINECODE_TEST_EXECUTABLES "")
foreach (_linecode_target IN LISTS LINECODE_ALL_TARGETS)
    if (NOT _linecode_target MATCHES "_tests$")
        continue ()
    endif ()
    if (_linecode_target IN_LIST LINECODE_TEST_FIXTURE_ONLY_EXECUTABLES)
        continue ()
    endif ()
    get_target_property(_linecode_type ${_linecode_target} TYPE)
    if (_linecode_type STREQUAL "EXECUTABLE")
        list(APPEND LINECODE_TEST_EXECUTABLES ${_linecode_target})
    endif ()
endforeach ()
list(SORT LINECODE_TEST_EXECUTABLES)
list(LENGTH LINECODE_TEST_EXECUTABLES LINECODE_TEST_EXECUTABLE_COUNT)
# A silent discovery failure would turn the gate into a no-op, so refuse to
# configure a runner that found almost nothing.
if (LINECODE_TEST_EXECUTABLE_COUNT LESS 50)
    message(FATAL_ERROR
            "discovered only ${LINECODE_TEST_EXECUTABLE_COUNT} test "
            "executables; the GoogleTest runner would skip tests")
endif ()

set(_linecode_runner [==[# Generated by tests/CMakeLists.txt -- do not edit.
# Runs every GoogleTest binary of this project directly; there is no CTest.
set(LINECODE_TEST_TOTAL 0)
set(LINECODE_TEST_FAILED 0)
set(LINECODE_TEST_FAILED_NAMES "")

macro(linecode_record_result _label _result)
  math(EXPR LINECODE_TEST_TOTAL "${LINECODE_TEST_TOTAL} + 1")
  # No early return() here: a macro does not open a scope of its own, so
  # return() would abort the whole generated script after the first binary.
  if(_result EQUAL 0)
    message(STATUS "[  OK  ] ${_label}")
  else()
    math(EXPR LINECODE_TEST_FAILED "${LINECODE_TEST_FAILED} + 1")
    list(APPEND LINECODE_TEST_FAILED_NAMES "${_label}")
    message(STATUS "[FAILED] ${_label} (exit ${_result})")
  endif()
endmacro()

# GoogleTest >= 1.11 prints only failures with --gtest_brief, which keeps the
# CI log readable; LINECODE_TEST_VERBOSE=1 restores the full output.
macro(linecode_run_gtest _binary _working_directory)
  set(_args "${_binary}")
  if(NOT "$ENV{LINECODE_TEST_VERBOSE}" STREQUAL "1")
    list(APPEND _args "--gtest_brief=1")
  endif()
  if(DEFINED ENV{GTEST_FILTER} AND NOT "$ENV{GTEST_FILTER}" STREQUAL "")
    list(APPEND _args "--gtest_filter=$ENV{GTEST_FILTER}")
  endif()
  execute_process(COMMAND ${_args}
                  WORKING_DIRECTORY "${_working_directory}"
                  RESULT_VARIABLE _result)
  get_filename_component(_label "${_binary}" NAME)
  linecode_record_result("${_label}" "${_result}")
endmacro()

# Non-GoogleTest steps (the sshd fixture wrapping ssh_protocol_tests). The label
# is explicit because `list(GET ARGV ..)` does not resolve inside a macro (only
# functions define ARGV as a real list variable).
macro(linecode_run_step _label)
  set(_command ${ARGN})
  execute_process(COMMAND ${_command}
                  WORKING_DIRECTORY "${CMAKE_CURRENT_LIST_DIR}"
                  RESULT_VARIABLE _result)
  linecode_record_result("${_label}" "${_result}")
endmacro()
]==])

foreach (_linecode_target IN LISTS LINECODE_TEST_EXECUTABLES)
    set(_linecode_workdir "${CMAKE_CURRENT_BINARY_DIR}")
    if (DEFINED LINECODE_TEST_WORKING_DIRECTORY_${_linecode_target})
        set(_linecode_workdir
                "${LINECODE_TEST_WORKING_DIRECTORY_${_linecode_target}}")
    endif ()
    string(APPEND _linecode_runner
            "linecode_run_gtest(\"$<TARGET_FILE:${_linecode_target}>\" \"${_linecode_workdir}\")\n")
endforeach ()

if (DEFINED LINECODE_SSH_FIXTURE_COMMAND)
    set(_linecode_fixture_arguments "")
    foreach (_linecode_part IN LISTS LINECODE_SSH_FIXTURE_COMMAND)
        string(APPEND _linecode_fixture_arguments "\"${_linecode_part}\" ")
    endforeach ()
    string(APPEND _linecode_runner
            "linecode_run_step(\"ssh_protocol_fixture\" ${_linecode_fixture_arguments})\n")
endif ()

string(APPEND _linecode_runner [==[
message(STATUS "")
if(LINECODE_TEST_FAILED GREATER 0)
  message(STATUS "${LINECODE_TEST_FAILED} of ${LINECODE_TEST_TOTAL} test binaries FAILED:")
  foreach(_name IN LISTS LINECODE_TEST_FAILED_NAMES)
    message(STATUS "  - ${_name}")
  endforeach()
  message(FATAL_ERROR
          "GoogleTest suite: ${LINECODE_TEST_FAILED} of ${LINECODE_TEST_TOTAL} binaries failed")
endif()
message(STATUS "GoogleTest suite: all ${LINECODE_TEST_TOTAL} binaries passed")
]==])

file(GENERATE
        OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/run_tests.cmake"
        CONTENT "${_linecode_runner}")

add_custom_target(run_tests
        COMMAND "${CMAKE_COMMAND}" -P "${CMAKE_CURRENT_BINARY_DIR}/run_tests.cmake"
        DEPENDS ${LINECODE_TEST_EXECUTABLES}
        COMMENT "Running the GoogleTest suite (${LINECODE_TEST_EXECUTABLE_COUNT} binaries)"
        USES_TERMINAL VERBATIM)

# Convenience alias so the entry point is discoverable by the tool it runs.
add_custom_target(gtest DEPENDS run_tests)
