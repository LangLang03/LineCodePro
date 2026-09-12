cmake_minimum_required(VERSION 3.20)

project(linecodepro_tests LANGUAGES CXX)

enable_testing()

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

# Keep assertion-based specifications active even when this small native test
# project is configured as Release.
if (MSVC)
    add_compile_options(/UNDEBUG)
else ()
    add_compile_options(-UNDEBUG)
endif ()

add_executable(linecodepro_tests
        application_tests.cpp
        ../src/application/chat_session.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/send_message.cpp
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

add_test(NAME linecodepro_tests COMMAND linecodepro_tests)

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
add_test(NAME app_route_tests COMMAND app_route_tests)

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
add_test(NAME project_workspace_presentation_tests
        COMMAND project_workspace_presentation_tests)

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

add_test(NAME model_form_service_tests COMMAND model_form_service_tests)

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

add_test(NAME model_form_presentation_tests
        COMMAND model_form_presentation_tests)

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

add_test(NAME linecodepro_theme_tests COMMAND linecodepro_theme_tests)

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

add_test(NAME presentation_theme_tests COMMAND presentation_theme_tests)

find_package(SQLite3 REQUIRED)

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

add_test(NAME legacy_conversation_schema_tests
        COMMAND legacy_conversation_schema_tests)

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

add_test(NAME legacy_feature_schema_tests
        COMMAND legacy_feature_schema_tests)

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

add_test(NAME settings_store_schema_tests
        COMMAND settings_store_schema_tests)

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

add_test(NAME behavior_settings_tests COMMAND behavior_settings_tests)

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
add_test(NAME chat_export_tests COMMAND chat_export_tests)

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
add_test(NAME image_generation_codec_tests
        COMMAND image_generation_codec_tests)

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
add_test(NAME image_generation_tool_registry_tests
        COMMAND image_generation_tool_registry_tests)

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
add_test(NAME image_understanding_codec_tests
        COMMAND image_understanding_codec_tests)

add_executable(image_understanding_tool_registry_tests
        image_understanding_tool_registry_tests.cpp
        ../src/application/image_understanding_tool_registry.cpp
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
add_test(NAME image_understanding_tool_registry_tests
        COMMAND image_understanding_tool_registry_tests)

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
add_test(NAME workspace_image_readers_tests
        COMMAND workspace_image_readers_tests)

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

add_test(NAME sse_decoder_tests COMMAND sse_decoder_tests)

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

add_test(NAME model_store_selection_tests
        COMMAND model_store_selection_tests)

add_executable(completion_pipeline_tests
        completion_pipeline_tests.cpp
        ../src/application/chat_session.cpp
        ../src/application/generation_controller.cpp
        ../src/application/legacy_attachment_policy.cpp
        ../src/application/send_message.cpp
        ../src/application/tool_result_display_policy.cpp
        ../src/domain/input_attachment.cpp
        ../src/infrastructure/archive_json.cpp
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

add_test(NAME completion_pipeline_tests COMMAND completion_pipeline_tests)

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

add_test(NAME completion_protocol_codec_tests
        COMMAND completion_protocol_codec_tests)

add_executable(prompt_request_composer_tests
        prompt_request_composer_tests.cpp
        ../src/application/behavior_settings_repository.cpp
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

add_test(NAME prompt_request_composer_tests
        COMMAND prompt_request_composer_tests)

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

add_test(NAME output_settings_tests COMMAND output_settings_tests)

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

add_test(NAME model_catalog_codec_tests COMMAND model_catalog_codec_tests)

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

add_test(NAME error_log_tests COMMAND error_log_tests)

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

add_test(NAME storage_stats_tests COMMAND storage_stats_tests)

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

add_test(NAME archive_safety_tests COMMAND archive_safety_tests)

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

add_test(NAME archive_transaction_tests COMMAND archive_transaction_tests)

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

add_test(NAME archive_redaction_tests COMMAND archive_redaction_tests)

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
add_test(NAME sqlite_archive_redaction_tests
        COMMAND sqlite_archive_redaction_tests)

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

add_test(NAME tutorial_markdown_parser_tests
        COMMAND tutorial_markdown_parser_tests)
set_tests_properties(tutorial_markdown_parser_tests PROPERTIES
        WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/..")

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

add_test(NAME mcp_execution_settings_tests
        COMMAND mcp_execution_settings_tests)

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

add_test(NAME tool_settings_tests COMMAND tool_settings_tests)

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

add_test(NAME ssh_settings_tests COMMAND ssh_settings_tests)

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
add_test(NAME memory_store_contract_tests COMMAND memory_store_contract_tests)

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
add_test(NAME memory_rag_tests COMMAND memory_rag_tests)

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
add_test(NAME memory_context_service_tests COMMAND memory_context_service_tests)

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
add_test(NAME extension_store_tests COMMAND extension_store_tests)

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
add_test(NAME terminal_provider_contract_tests
        COMMAND terminal_provider_contract_tests)

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
add_test(NAME mcp_protocol_codec_tests COMMAND mcp_protocol_codec_tests)

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
add_test(NAME mcp_extension_tool_registry_tests
        COMMAND mcp_extension_tool_registry_tests)

add_executable(mcp_completion_loop_tests
        mcp_completion_loop_tests.cpp
        ../src/application/mcp_completion_loop.cpp
        ../src/application/mcp_extension_tool_registry.cpp
        ../src/application/tool_permission_service.cpp
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
add_test(NAME mcp_completion_loop_tests COMMAND mcp_completion_loop_tests)

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
add_test(NAME skill_services_tests COMMAND skill_services_tests)

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
add_test(NAME skill_hub_route_tests COMMAND skill_hub_route_tests)

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
add_test(NAME sqlite_skill_record_store_tests
        COMMAND sqlite_skill_record_store_tests)

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
add_test(NAME project_workspace_service_tests
        COMMAND project_workspace_service_tests)

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
add_test(NAME runtime_tool_registry_tests
        COMMAND runtime_tool_registry_tests)

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
add_test(NAME tool_approval_presentation_tests
        COMMAND tool_approval_presentation_tests)

add_executable(tool_permission_service_tests
        tool_permission_service_tests.cpp
        ../src/application/tool_permission_service.cpp
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
add_test(NAME tool_permission_service_tests
        COMMAND tool_permission_service_tests)

add_executable(chat_mode_service_tests
        chat_mode_service_tests.cpp
        ../src/application/chat_mode_service.cpp
        ../src/application/tool_permission_service.cpp
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
add_test(NAME chat_mode_service_tests COMMAND chat_mode_service_tests)

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
add_test(NAME slash_command_catalog_tests COMMAND slash_command_catalog_tests)

add_executable(attachment_backend_tests
        attachment_backend_tests.cpp
        ../src/application/chat_session.cpp
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
add_test(NAME attachment_backend_tests COMMAND attachment_backend_tests)

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
add_test(NAME pending_message_queue_tests COMMAND pending_message_queue_tests)

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
add_test(NAME sqlite_conversation_attachment_tests
        COMMAND sqlite_conversation_attachment_tests)

add_executable(chat_timeline_presentation_tests
        chat_timeline_presentation_tests.cpp
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
add_test(NAME chat_timeline_presentation_tests
        COMMAND chat_timeline_presentation_tests)

add_executable(ssh_backend_tests
        ssh_backend_tests.cpp
        ../src/application/execution_mode_project_workspace.cpp
        ../src/application/ssh_project_workspace.cpp
        ../src/application/ssh_runtime_service.cpp
        ../src/application/ssh_tool_registry.cpp
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
add_test(NAME ssh_backend_tests COMMAND ssh_backend_tests)

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
        add_test(NAME ssh_protocol_fixture
                COMMAND bash
                "${CMAKE_CURRENT_SOURCE_DIR}/run_ssh_protocol_fixture.sh"
                "$<TARGET_FILE:ssh_protocol_tests>"
                "${LINECODE_SSHD_EXECUTABLE}"
                "${LINECODE_SSH_KEYGEN_EXECUTABLE}")
        set_tests_properties(ssh_protocol_fixture PROPERTIES TIMEOUT 45)
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
add_test(NAME skill_hub_reading_settings_tests
        COMMAND skill_hub_reading_settings_tests)

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
add_test(NAME markdown_link_policy_tests COMMAND markdown_link_policy_tests)

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
add_test(NAME agent_extension_draft_tests COMMAND agent_extension_draft_tests)

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
add_test(NAME todo_tool_tests COMMAND todo_tool_tests)

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
add_test(NAME memory_tool_tests COMMAND memory_tool_tests)

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
add_test(NAME web_tool_tests COMMAND web_tool_tests)

add_executable(file_tool_tests
        file_tool_tests.cpp
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
add_test(NAME file_tool_tests COMMAND file_tool_tests)

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
add_test(NAME context_usage_tests COMMAND context_usage_tests)

add_executable(context_compaction_tests
        context_compaction_tests.cpp
        ../src/application/context_compaction.cpp
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
add_test(NAME context_compaction_tests COMMAND context_compaction_tests)

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
add_test(NAME diff_lines_tests COMMAND diff_lines_tests)

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
add_test(NAME diff_store_tests COMMAND diff_store_tests)

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
add_test(NAME diff_review_service_tests COMMAND diff_review_service_tests)

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
add_test(NAME agent_pipeline_tests COMMAND agent_pipeline_tests)

add_executable(sub_agent_runner_tests
        sub_agent_runner_tests.cpp
        ../src/domain/agent_pipeline.cpp
        ../src/application/sub_agent_runner.cpp
        ../src/application/prompt_template_repository.cpp
        ../src/application/skill_repository.cpp
        ../src/domain/skill.cpp
        ../src/domain/prompt_renderer.cpp
        ../src/infrastructure/archive_json.cpp
        ../src/domain/prompt_template.cpp
)
target_include_directories(sub_agent_runner_tests PRIVATE ../src)
target_compile_features(sub_agent_runner_tests PRIVATE cxx_std_23)
target_link_libraries(sub_agent_runner_tests PRIVATE HuxerUI::testing)
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
add_test(NAME sub_agent_runner_tests COMMAND sub_agent_runner_tests)

add_executable(agent_tool_tests
        agent_tool_tests.cpp
        ../src/application/agent_result_registry.cpp
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
add_test(NAME agent_tool_tests COMMAND agent_tool_tests)

add_executable(auto_compaction_tests
        auto_compaction_tests.cpp
        ../src/application/auto_compaction_service.cpp
        ../src/application/chat_session.cpp
        ../src/application/context_compaction.cpp
        ../src/application/legacy_attachment_policy.cpp
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
add_test(NAME auto_compaction_tests COMMAND auto_compaction_tests)

add_executable(tool_loop_compactor_tests
        tool_loop_compactor_tests.cpp
        ../src/application/tool_loop_compactor.cpp
        ../src/application/auto_compaction_service.cpp
        ../src/application/context_compaction.cpp
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
add_test(NAME tool_loop_compactor_tests COMMAND tool_loop_compactor_tests)

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
add_test(NAME inline_emphasis_tests COMMAND inline_emphasis_tests)

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
add_test(NAME markdown_linkify_tests COMMAND markdown_linkify_tests)

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
add_test(NAME tutorial_nested_blocks_tests
        COMMAND tutorial_nested_blocks_tests)

add_executable(generation_retry_tests
        generation_retry_tests.cpp
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
add_test(NAME generation_retry_tests COMMAND generation_retry_tests)

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
add_test(NAME conversation_resume_sanitizer_tests COMMAND conversation_resume_sanitizer_tests)

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
add_test(NAME token_usage_tracker_tests COMMAND token_usage_tracker_tests)

add_executable(custom_agent_tool_tests
        custom_agent_tool_tests.cpp
        ../src/application/agent_extension_tool_registry.cpp
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
add_test(NAME custom_agent_tool_tests COMMAND custom_agent_tool_tests)
