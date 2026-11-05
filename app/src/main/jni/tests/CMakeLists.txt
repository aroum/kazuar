# SPDX-License-Identifier: GPL-3.0-only
cmake_minimum_required(VERSION 3.22.1)
project(kazuar_native_regressions LANGUAGES CXX)
find_package(GTest REQUIRED)
enable_testing()
add_executable(native_regressions
    dictionary/utils/format_utils_test.cpp
    dictionary/utils/trie_map_test.cpp
    suggest/core/layout/geometry_utils_test.cpp
    dictionary/structure/pt_common/bigram/bigram_list_read_write_utils_test.cpp
    ../src/dictionary/utils/format_utils.cpp
    ../src/dictionary/utils/byte_array_utils.cpp
    ../src/dictionary/utils/buffer_with_extendable_buffer.cpp
    ../src/dictionary/utils/trie_map.cpp
    ../src/dictionary/structure/pt_common/bigram/bigram_list_read_write_utils.cpp)
target_include_directories(native_regressions PRIVATE ../src)
target_compile_features(native_regressions PRIVATE cxx_std_11)
# Unused dictionary file-writing entry points are not part of these in-memory tests.
target_compile_options(native_regressions PRIVATE -ffunction-sections -fdata-sections
    -fsanitize=address,undefined -fno-omit-frame-pointer)
target_link_options(native_regressions PRIVATE -Wl,--gc-sections -fsanitize=address,undefined)
target_link_libraries(native_regressions PRIVATE GTest::gtest_main)
include(GoogleTest)
gtest_discover_tests(native_regressions)
