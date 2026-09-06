cmake_minimum_required(VERSION 3.20)

project(linecodepro_tests LANGUAGES CXX)

enable_testing()

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
        ../src/application/send_message.cpp
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
