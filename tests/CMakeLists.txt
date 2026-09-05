cmake_minimum_required(VERSION 3.20)

project(linecodepro_tests LANGUAGES CXX)

enable_testing()

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
