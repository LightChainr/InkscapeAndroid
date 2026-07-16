cmake_minimum_required(VERSION 3.24)
project(inkscape_android_tablet_input_core LANGUAGES CXX)

add_library(tablet_input_core
    src/tablet_input_core.cpp
)

target_include_directories(tablet_input_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_features(tablet_input_core PUBLIC cxx_std_20)

if(MSVC)
    target_compile_options(tablet_input_core PRIVATE /W4 /WX)
else()
    target_compile_options(tablet_input_core PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()

option(TABLET_INPUT_BUILD_TESTS "Build tablet input core tests" ON)
if(TABLET_INPUT_BUILD_TESTS)
    enable_testing()
    add_executable(tablet_input_core_tests
        tests/tablet_input_core_test.cpp
    )
    target_link_libraries(tablet_input_core_tests PRIVATE tablet_input_core)
    target_compile_features(tablet_input_core_tests PRIVATE cxx_std_20)
    if(MSVC)
        target_compile_options(tablet_input_core_tests PRIVATE /W4 /WX)
    else()
        target_compile_options(tablet_input_core_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
    endif()
    add_test(NAME tablet_input_core_tests COMMAND tablet_input_core_tests)
endif()
