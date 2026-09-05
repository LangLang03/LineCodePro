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
